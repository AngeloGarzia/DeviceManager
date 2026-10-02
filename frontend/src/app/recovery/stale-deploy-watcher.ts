/**
 * Recharge l'appli si un nouvel index.html a été déployé pendant qu'un onglet
 * restait ouvert (sinon l'ancien bundle — ex. Accueil = inventaire pièces — reste en mémoire).
 */
export function installStaleDeployWatcher(): void {
  if (typeof document === 'undefined' || typeof fetch === 'undefined') {
    return;
  }

  const runningMain = [...document.scripts]
    .map((s) => s.getAttribute('src') || '')
    .find((src) => /main-[A-Za-z0-9]+\.js/i.test(src));
  if (!runningMain) {
    return;
  }
  const runningFile = runningMain.split('/').pop()!.split('?')[0];

  let checking = false;
  let lastCheck = 0;

  const check = async (): Promise<void> => {
    const now = Date.now();
    if (checking || now - lastCheck < 15_000) {
      return;
    }
    checking = true;
    lastCheck = now;
    try {
      const res = await fetch(`/index.html?_=${now}`, { cache: 'no-store', credentials: 'same-origin' });
      if (!res.ok) {
        return;
      }
      const html = await res.text();
      const match = html.match(/main-[A-Za-z0-9]+\.js/i);
      if (match && match[0] !== runningFile) {
        // Nouvel déploy : abandonner le vieux shell / vieux menu / vieille page pièces.
        window.location.reload();
      }
    } catch {
      // Hors ligne / API endormie : ne pas forcer.
    } finally {
      checking = false;
    }
  };

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      void check();
    }
  });
  window.addEventListener('focus', () => {
    void check();
  });
  window.addEventListener('pageshow', (ev) => {
    // Retour via bfcache navigateur.
    if ((ev as PageTransitionEvent).persisted) {
      void check();
    }
  });

  // Premier contrôle peu après le boot (évite un onglet long laissé ouvert).
  window.setTimeout(() => void check(), 3_000);
}
