import { ErrorHandler, Injectable } from '@angular/core';

const IGNORE =
  /No Listener:|tabs:outgoing|Extension context invalidated|chrome-extension:|moz-extension:/i;

const RECOVERABLE =
  /Loading chunk [\d]+ failed|ChunkLoadError|Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module/i;

const RECOVERY_KEY = 'dm-ng-error-recovery-at';
const RECOVERY_COOLDOWN_MS = 15_000;

/**
 * ErrorHandler Angular : échec de chargement de chunk → rechargement page d’accueil.
 */
@Injectable()
export class AppErrorHandler implements ErrorHandler {
  handleError(error: unknown): void {
    const msg =
      error instanceof Error
        ? `${error.name}: ${error.message}`
        : String(error ?? '');

    if (IGNORE.test(msg)) {
      console.warn('[ignored]', error);
      return;
    }

    console.error(error);

    const chunkFail =
      RECOVERABLE.test(msg) || (error instanceof Error && error.name === 'ChunkLoadError');
    if (!chunkFail) {
      return;
    }

    try {
      const last = Number(sessionStorage.getItem(RECOVERY_KEY) || '0');
      if (Date.now() - last < RECOVERY_COOLDOWN_MS) {
        return;
      }
      sessionStorage.setItem(RECOVERY_KEY, String(Date.now()));
    } catch {
      /* ignore */
    }

    window.location.assign(`${window.location.origin}/`);
  }
}
