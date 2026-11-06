#!/usr/bin/env python3
"""Export the real database and private pet photos into an age-encrypted archive.

Credentials are read from environment variables, never command arguments or logs.
Requires pg_dump (or Docker) and age. Does not mutate the source project.
"""
import argparse
import datetime
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.error
import urllib.parse
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path, help="Destination .tar.gz.age file outside the project")
    parser.add_argument("--recipient", required=True, help="Public age recipient (age1...). Keep its private key separately.")
    args = parser.parse_args()
    os.umask(0o077)
    required = ["SUPABASE_DB_URL", "SUPABASE_URL", "SUPABASE_SERVICE_ROLE_KEY"]
    if any(not os.environ.get(name) for name in required):
        parser.error("Set SUPABASE_DB_URL, SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY privately in the environment.")
    if not shutil.which("age"):
        parser.error("Install age to encrypt the backup before continuing.")
    if args.output.exists():
        parser.error("The destination already exists. Choose a new file; backups are never overwritten.")
    db = urllib.parse.urlsplit(os.environ["SUPABASE_DB_URL"])
    api_url = os.environ["SUPABASE_URL"].rstrip("/")
    if db.scheme not in ("postgres", "postgresql") or not db.hostname or not db.username:
        parser.error("Invalid database connection URL.")
    if not api_url.startswith("https://"):
        parser.error("Use the HTTPS project URL.")
    key = os.environ["SUPABASE_SERVICE_ROLE_KEY"]
    env = os.environ.copy()
    env.update(PGHOST=db.hostname, PGPORT=str(db.port or 5432), PGUSER=urllib.parse.unquote(db.username),
               PGPASSWORD=urllib.parse.unquote(db.password or ""), PGDATABASE=db.path.lstrip("/"), PGSSLMODE="require")
    command = ["pg_dump", "--format=custom", "--no-owner", "--no-privileges", "--schema=public", "--schema=private", "--schema=auth"]
    if not shutil.which("pg_dump"):
        if not shutil.which("docker"):
            parser.error("Install PostgreSQL client tools or Docker.")
        command = ["docker", "run", "--rm", *[arg for name in ["PGHOST", "PGPORT", "PGUSER", "PGPASSWORD", "PGDATABASE", "PGSSLMODE"] for arg in ("--env", name)], "postgres:17-alpine", *command]

    def request(path, body=None):
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(api_url + path, data=data, headers={
            "apikey": key, "Authorization": "Bearer " + key, "Content-Type": "application/json"
        })
        return urllib.request.urlopen(req, timeout=60)

    def list_files(prefix=""):
        offset = 0
        while True:
            with request("/storage/v1/object/list/animal-photos", {"prefix": prefix, "limit": 1000, "offset": offset, "sortBy": {"column": "name", "order": "asc"}}) as response:
                items = json.load(response)
            for item in items:
                name = "/".join(filter(None, [prefix, item["name"]]))
                if ".." in name.split("/") or name.startswith("/"):
                    raise RuntimeError("Unexpected storage object path; backup stopped.")
                if item.get("id") is None:
                    yield from list_files(name)
                else:
                    yield name
            if len(items) < 1000:
                break
            offset += len(items)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="abrace-backup-") as temp:
        dump = Path(temp) / "database.dump"
        with dump.open("wb") as output:
            result = subprocess.run(command, env=env, stdout=output, stderr=subprocess.PIPE)
        if result.returncode:
            raise RuntimeError("Database export failed. Check the private database settings and pg_dump version.")
        process = subprocess.Popen(["age", "--recipient", args.recipient, "--output", str(args.output)], stdin=subprocess.PIPE, stderr=subprocess.PIPE)
        try:
            count = 0
            with tarfile.open(fileobj=process.stdin, mode="w|gz") as archive:
                archive.add(dump, arcname="database.dump")
                for name in list_files():
                    with request("/storage/v1/object/authenticated/animal-photos/" + urllib.parse.quote(name, safe="/")) as response:
                        data = response.read()
                    info = tarfile.TarInfo("animal-photos/" + name)
                    info.size = len(data)
                    info.mode = 0o600
                    archive.addfile(info, io.BytesIO(data))
                    count += 1
                manifest = json.dumps({"format": 1, "created_at": datetime.datetime.now(datetime.timezone.utc).isoformat(), "photo_count": count, "database_schemas": ["public", "private", "auth"]}, indent=2).encode()
                info = tarfile.TarInfo("manifest.json")
                info.size = len(manifest)
                info.mode = 0o600
                archive.addfile(info, io.BytesIO(manifest))
            process.stdin.close()
            error = process.stderr.read()
            if process.wait() != 0:
                raise RuntimeError("Backup encryption failed. Check the age recipient.")
        except BaseException:
            process.kill()
            process.wait()
            args.output.unlink(missing_ok=True)
            raise
    print(f"Encrypted backup written to {args.output}. Verify decryption and restore in an isolated test project.")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, urllib.error.URLError):
        print("Backup failed. Check the configuration and connectivity. Credentials and server responses were not printed.", file=sys.stderr)
        sys.exit(1)
