import { Injectable, NgZone, OnDestroy, inject, signal } from '@angular/core';
import { AuthService } from './auth.service';

/** Inactivité totale avant déconnexion. */
const IDLE_MS = 30 * 60 * 1000;
/** Affiche l'avertissement avant la déconnexion. */
const WARN_BEFORE_MS = 2 * 60 * 1000;
/** Ignore les événements d'activité trop rapprochés. */
const ACTIVITY_THROTTLE_MS = 1000;

const ACTIVITY_EVENTS: (keyof DocumentEventMap)[] = [
  'click',
  'keydown',
  'mousemove',
  'scroll',
  'touchstart',
  'wheel'
];

/**
 * Déconnecte l'utilisateur après inactivité (30 min), avec alerte à 2 min.
 * Actif uniquement quand une session authentifiée est présente (shell).
 */
@Injectable({ providedIn: 'root' })
export class IdleSessionService implements OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly zone = inject(NgZone);

  readonly warningOpen = signal(false);
  readonly secondsLeft = signal(0);

  private started = false;
  private lastActivityAt = 0;
  private lastThrottleAt = 0;
  private warnTimer: ReturnType<typeof setTimeout> | null = null;
  private logoutTimer: ReturnType<typeof setTimeout> | null = null;
  private countdownTimer: ReturnType<typeof setInterval> | null = null;
  private readonly onActivity = (): void => this.noteActivity();
  private readonly onVisibility = (): void => {
    if (document.visibilityState === 'visible') {
      this.noteActivity();
    }
  };

  start(): void {
    if (this.started) {
      return;
    }
    this.started = true;
    this.zone.runOutsideAngular(() => {
      for (const event of ACTIVITY_EVENTS) {
        document.addEventListener(event, this.onActivity, { passive: true, capture: true });
      }
      document.addEventListener('visibilitychange', this.onVisibility);
    });
    this.resetTimers();
  }

  stop(): void {
    if (!this.started) {
      return;
    }
    this.started = false;
    for (const event of ACTIVITY_EVENTS) {
      document.removeEventListener(event, this.onActivity, true);
    }
    document.removeEventListener('visibilitychange', this.onVisibility);
    this.clearTimers();
    this.zone.run(() => {
      this.warningOpen.set(false);
      this.secondsLeft.set(0);
    });
  }

  /** L'utilisateur confirme qu'il reste connecté. */
  staySignedIn(): void {
    this.resetTimers();
  }

  ngOnDestroy(): void {
    this.stop();
  }

  private noteActivity(): void {
    if (!this.started || this.warningOpen()) {
      return;
    }
    const now = Date.now();
    if (now - this.lastThrottleAt < ACTIVITY_THROTTLE_MS) {
      return;
    }
    this.lastThrottleAt = now;
    this.lastActivityAt = now;
    this.scheduleFromLastActivity();
  }

  private resetTimers(): void {
    this.lastActivityAt = Date.now();
    this.lastThrottleAt = this.lastActivityAt;
    this.zone.run(() => {
      this.warningOpen.set(false);
      this.secondsLeft.set(0);
    });
    this.scheduleFromLastActivity();
  }

  private scheduleFromLastActivity(): void {
    this.clearTimers();
    const elapsed = Date.now() - this.lastActivityAt;
    const warnIn = Math.max(0, IDLE_MS - WARN_BEFORE_MS - elapsed);
    const logoutIn = Math.max(0, IDLE_MS - elapsed);

    this.warnTimer = setTimeout(() => this.openWarning(), warnIn);
    this.logoutTimer = setTimeout(() => this.expireSession(), logoutIn);
  }

  private openWarning(): void {
    if (!this.started || !this.auth.getToken()) {
      return;
    }
    this.zone.run(() => {
      this.warningOpen.set(true);
      this.secondsLeft.set(Math.ceil(WARN_BEFORE_MS / 1000));
    });
    this.countdownTimer = setInterval(() => {
      this.zone.run(() => {
        const next = Math.max(0, this.secondsLeft() - 1);
        this.secondsLeft.set(next);
      });
    }, 1000);
  }

  private expireSession(): void {
    if (!this.started) {
      return;
    }
    this.clearTimers();
    this.zone.run(() => {
      this.warningOpen.set(false);
      this.auth.logoutDueToIdle();
    });
  }

  private clearTimers(): void {
    if (this.warnTimer != null) {
      clearTimeout(this.warnTimer);
      this.warnTimer = null;
    }
    if (this.logoutTimer != null) {
      clearTimeout(this.logoutTimer);
      this.logoutTimer = null;
    }
    if (this.countdownTimer != null) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }
  }
}
