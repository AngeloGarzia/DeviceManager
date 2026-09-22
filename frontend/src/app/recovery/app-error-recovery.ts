/**
 * Récupération front après pannes « dures » (chunk JS 404 après deploy, module dynamique).
 * Ignore le bruit des extensions navigateur (ex. « No Listener: tabs:outgoing… »).
 * Recharge la page d’accueil (/ → devices) avec anti-boucle sessionStorage.
 */

const RECOVERY_KEY = 'dm-error-recovery-at';
const RECOVERY_COOLDOWN_MS = 15_000;

const IGNORE =
  /No Listener:|tabs:outgoing|Extension context invalidated|chrome-extension:|moz-extension:|ResizeObserver loop|Script error\./i;

const RECOVERABLE =
  /Loading chunk [\d]+ failed|ChunkLoadError|Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module/i;

function messageOf(reason: unknown): string {
  if (reason == null) {
    return '';
  }
  if (typeof reason === 'string') {
    return reason;
  }
  if (reason instanceof Error) {
    return `${reason.name}: ${reason.message}`;
  }
  if (typeof reason === 'object' && 'message' in reason) {
    return String((reason as { message: unknown }).message);
  }
  return String(reason);
}

function shouldIgnore(msg: string): boolean {
  return !msg || IGNORE.test(msg);
}

function isRecoverable(msg: string, reason?: unknown): boolean {
  if (RECOVERABLE.test(msg)) {
    return true;
  }
  // Certains bundlers posent un code sur l’erreur
  if (reason && typeof reason === 'object' && 'name' in reason) {
    const name = String((reason as { name: unknown }).name);
    if (name === 'ChunkLoadError') {
      return true;
    }
  }
  return false;
}

function recoverToHome(): void {
  try {
    const last = Number(sessionStorage.getItem(RECOVERY_KEY) || '0');
    if (Date.now() - last < RECOVERY_COOLDOWN_MS) {
      return;
    }
    sessionStorage.setItem(RECOVERY_KEY, String(Date.now()));
  } catch {
    // sessionStorage indisponible : on tente quand même une fois
  }
  // Accueil app = '/' (redirige vers devices)
  window.location.assign(`${window.location.origin}/`);
}

/**
 * Branche les listeners globaux (à appeler avant / autour du bootstrap).
 */
export function installAppErrorRecovery(): void {
  window.addEventListener(
    'error',
    (event) => {
      const target = event.target;
      // Script / CSS du bundle introuvable (404 après nouveau deploy)
      if (target instanceof HTMLScriptElement || target instanceof HTMLLinkElement) {
        recoverToHome();
        return;
      }
      const msg = messageOf(event.error ?? event.message);
      if (shouldIgnore(msg)) {
        return;
      }
      if (isRecoverable(msg, event.error)) {
        recoverToHome();
      }
    },
    true
  );

  window.addEventListener('unhandledrejection', (event) => {
    const msg = messageOf(event.reason);
    if (shouldIgnore(msg)) {
      return;
    }
    if (isRecoverable(msg, event.reason)) {
      event.preventDefault();
      recoverToHome();
    }
  });
}
