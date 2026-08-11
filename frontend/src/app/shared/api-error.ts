/**
 * Extrait un message d'erreur API métier pour l'UI Device Manager.
 * Filtre les fuites techniques (JWT, SMTP, .env, stack, etc.).
 */
export function apiErrorMessage(err: unknown, fallback: string): string {
  const e = err as { error?: { message?: unknown }; message?: unknown; status?: number } | null;
  const raw = String(e?.error?.message ?? e?.message ?? '').trim();
  if (!raw) {
    return fallback;
  }
  if (looksTechnical(raw)) {
    return fallback;
  }
  return raw;
}

function looksTechnical(message: string): boolean {
  const lower = message.toLowerCase();
  return (
    /\b(exception|stack\s*trace|nullpointer|hibernate|jdbc|sql|token|jwt|\.env|smtp|mail_|s3\b|docker|x-atelier|refresh token|caused by|transientobject)\b/i.test(
      lower
    ) ||
    lower.includes('org.') ||
    lower.includes('com.devicemanager') ||
    lower.includes('java.') ||
    /statut\s*=/.test(lower)
  );
}
