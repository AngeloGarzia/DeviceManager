import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  Observable,
  Subscriber,
  catchError,
  map,
  of,
  timeout,
  timer
} from 'rxjs';
import { environment } from '../../environments/environment';

export interface ServerWakeState {
  showOverlay: boolean;
  progress: number;
  message: string;
  ready: boolean;
  timedOut: boolean;
}

/**
 * Réveille l’API Render (free tier) via /actuator/health/liveness.
 * Affiche toujours l’overlay jusqu’à ce que l’API réponde (ou timeout).
 */
@Injectable({ providedIn: 'root' })
export class ServerWakeService {
  private readonly http = inject(HttpClient);

  private readonly healthUrl = `${environment.apiUrl}/actuator/health/liveness`;
  /** Temps mini d’affichage pour que l’utilisateur voie la popup même si l’API répond vite. */
  private readonly minDisplayMs = 1200;
  private readonly pollIntervalMs = 2500;
  /** Timeout long : un cold start Render peut garder la connexion ouverte ~30–60s. */
  private readonly requestTimeoutMs = 60_000;
  private readonly maxWaitMs = 180_000;

  wake(): Observable<ServerWakeState> {
    return new Observable((subscriber: Subscriber<ServerWakeState>) => {
      const startedAt = Date.now();
      let stopped = false;
      let progress = 8;
      let pingInFlight = false;
      let readyAt: number | null = null;

      const messageFor = (ready: boolean, timedOut: boolean) => {
        if (timedOut) {
          return 'Le démarrage prend plus de temps que prévu. Vous pouvez patienter ou réessayer.';
        }
        if (ready) {
          return 'Serveurs prêts';
        }
        return 'Les serveurs Render démarrent… Cela peut prendre jusqu’à une minute.';
      };

      const emit = (partial: Partial<ServerWakeState> = {}) => {
        if (stopped) {
          return;
        }
        const ready = partial.ready === true;
        const timedOut = partial.timedOut === true;
        subscriber.next({
          showOverlay: true,
          progress,
          message: messageFor(ready, timedOut),
          ready: false,
          timedOut: false,
          ...partial
        });
      };

      const finishReady = () => {
        if (stopped || readyAt != null) {
          return;
        }
        readyAt = Date.now();
        progress = 100;
        const elapsed = readyAt - startedAt;
        const waitMore = Math.max(0, this.minDisplayMs - elapsed);

        const completeReady = () => {
          if (stopped) {
            return;
          }
          emit({
            showOverlay: true,
            progress: 100,
            message: messageFor(true, false),
            ready: true
          });
          stopped = true;
          subscriber.complete();
        };

        if (waitMore > 0) {
          emit({ progress: 100, message: messageFor(true, false) });
          setTimeout(completeReady, waitMore);
        } else {
          completeReady();
        }
      };

      const finishTimeout = () => {
        if (stopped) {
          return;
        }
        emit({
          showOverlay: true,
          progress: Math.min(progress, 95),
          message: messageFor(false, true),
          timedOut: true
        });
        stopped = true;
        subscriber.complete();
      };

      const advanceProgress = () => {
        if (readyAt != null) {
          return;
        }
        const elapsed = Date.now() - startedAt;
        const ratio = Math.min(elapsed / this.maxWaitMs, 1);
        progress = Math.max(progress, Math.min(92, Math.round(10 + ratio * 82)));
      };

      const pingOnce = () => {
        if (stopped || pingInFlight || readyAt != null) {
          return;
        }
        pingInFlight = true;
        this.http
          .get(this.healthUrl, { observe: 'response', responseType: 'text' })
          .pipe(
            timeout(this.requestTimeoutMs),
            map((res) => res.status >= 200 && res.status < 300),
            catchError(() => of(false))
          )
          .subscribe((ok) => {
            pingInFlight = false;
            if (ok) {
              finishReady();
            }
          });
      };

      // Affiche immédiatement la popup.
      emit({ showOverlay: true, progress });

      const progressSub = timer(400, 500).subscribe(() => {
        if (stopped) {
          return;
        }
        if (readyAt != null) {
          return;
        }
        if (Date.now() - startedAt >= this.maxWaitMs) {
          finishTimeout();
          return;
        }
        advanceProgress();
        emit();
      });

      const pingSub = timer(0, this.pollIntervalMs).subscribe(() => {
        if (!stopped && readyAt == null) {
          pingOnce();
        }
      });

      return () => {
        stopped = true;
        progressSub.unsubscribe();
        pingSub.unsubscribe();
      };
    });
  }
}
