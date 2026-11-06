import { createClient } from "npm:@supabase/supabase-js@2.49.1";

const headers = { "Content-Type": "application/json" };
const reply = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers });

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") {
    return reply(405, { error: "Método não permitido." });
  }
  const authorization = request.headers.get("Authorization") ?? "";
  if (!authorization.startsWith("Bearer ")) {
    return reply(401, { error: "Entre novamente no app." });
  }
  const url = Deno.env.get("SUPABASE_URL")!;
  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  // Never trust client role claims or user_metadata. Re-check active DB membership.
  const { data: { user }, error: authError } = await admin.auth.getUser(
    authorization.slice(7),
  );
  if (authError || !user) return reply(401, { error: "Sessão inválida." });
  const { data: caller } = await admin.from("volunteers").select(
    "role,active,archived_at",
  ).eq("id", user.id).single();
  if (caller?.role !== "admin" || !caller.active || caller.archived_at) {
    return reply(403, { error: "Apenas administradores podem convidar." });
  }
  let input: { email?: string; full_name?: string; phone?: string };
  try {
    input = await request.json();
  } catch {
    return reply(400, { error: "Dados inválidos." });
  }
  if (
    !input || typeof input.email !== "string" ||
    typeof input.full_name !== "string" ||
    (input.phone !== undefined && typeof input.phone !== "string")
  ) {
    return reply(400, { error: "Informe nome completo e e-mail válidos." });
  }
  const email = input.email?.trim().toLowerCase() ?? "";
  const full_name = input.full_name?.trim() ?? "";
  if (
    !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || full_name.length < 3 ||
    full_name.length > 150
  ) {
    return reply(400, { error: "Informe nome completo e e-mail válidos." });
  }
  const { data: existing } = await admin.from("volunteers").select("id,active")
    .eq("email", email).maybeSingle();
  if (existing?.active) {
    const { data: existingAuth } = await admin.auth.admin.getUserById(
      existing.id,
    );
    if (existingAuth.user?.email_confirmed_at) {
      return reply(409, {
        error:
          "Este voluntário já possui conta. Use a recuperação de senha se necessário.",
      });
    }
  }
  const { error: allowError } = await admin.from("invitation_allowlist").upsert(
    { email, full_name, phone: input.phone?.trim() ?? "", invited_by: user.id },
  );
  if (allowError) {
    return reply(500, { error: "Não foi possível preparar o convite." });
  }
  const { error } = await admin.auth.admin.inviteUserByEmail(email, {
    redirectTo: "abrace://auth",
    data: { full_name },
  });
  if (error) {
    return reply(502, {
      error:
        "Não foi possível enviar o convite. Confira a configuração SMTP e tente novamente.",
    });
  }
  return reply(200, { message: "Convite enviado por e-mail." });
});
