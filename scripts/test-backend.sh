#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
container="${ABRACE_TEST_CONTAINER:-abrace-postgres-test-$$-$RANDOM}"
image="postgres:17-alpine@sha256:266d3c257594e195b309ae9d1805e54c788f1d7b375f56bf6c367aac6c917e7b"
# A new isolated container per run, with no published ports or production credentials.
# Only the container created by this invocation is removed on exit.
container_id=$(docker run --name "$container" -e POSTGRES_HOST_AUTH_METHOD=trust -d "$image")
trap 'docker rm -f "$container_id" >/dev/null 2>&1 || true' EXIT
test_db="abrace_test"
ready=false
for attempt in {1..30}; do
 if docker exec "$container_id" pg_isready -U postgres >/dev/null 2>&1; then ready=true; break; fi
 sleep 1
done
if [ "$ready" != true ]; then
 docker logs "$container_id" >&2
 echo "O PostgreSQL de teste não ficou pronto." >&2
 exit 1
fi
docker exec "$container_id" createdb -U postgres "$test_db"
docker exec -i "$container_id" psql -U postgres -d "$test_db" -v ON_ERROR_STOP=1 < supabase/tests/bootstrap.sql >/dev/null
for migration in supabase/migrations/*.sql; do
 docker exec -i "$container_id" psql -U postgres -d "$test_db" -v ON_ERROR_STOP=1 < "$migration" >/dev/null
done
docker exec -i "$container_id" psql -U postgres -d "$test_db" -v ON_ERROR_STOP=1 < supabase/tests/rules.sql
