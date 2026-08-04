import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterOutlet } from '@angular/router';
import { Subscription } from 'rxjs';
import { ServerWakeService } from './services/server-wake.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, MatProgressBarModule, MatButtonModule],
  template: `
    <router-outlet />

    @if (showOverlay()) {
      <div
        class="fixed inset-0 z-[200] flex items-end justify-center bg-slate-900/45 p-4 sm:items-center"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="wake-title"
        aria-describedby="wake-desc"
      >
        <div class="w-full max-w-md rounded-2xl border border-line bg-white p-5 shadow-soft">
          <p class="m-0 text-xs font-semibold uppercase tracking-[0.08em] text-brand">DeviceManager</p>
          <h2 id="wake-title" class="m-0 mt-2 text-lg font-semibold text-ink">
            {{ timedOut() ? 'Démarrage en cours' : 'Réveil des serveurs' }}
          </h2>
          <p id="wake-desc" class="mt-2 text-sm leading-relaxed text-ink-soft">
            {{ message() }}
          </p>
          <mat-progress-bar
            class="mt-5"
            [mode]="timedOut() ? 'indeterminate' : 'determinate'"
            [value]="progress()"
          ></mat-progress-bar>
          <p class="mt-2 text-xs text-ink-soft">
            @if (timedOut()) {
              Toujours en attente de l’API hébergée sur Render…
            } @else if (progress() >= 100) {
              Prêt
            } @else {
              {{ progress() }}&nbsp;%
            }
          </p>
          @if (timedOut()) {
            <button mat-flat-button color="primary" class="mt-4 !h-11 w-full" type="button" (click)="retry()">
              Réessayer
            </button>
          }
        </div>
      </div>
    }
  `,
  styles: [
    `
      :host {
        display: block;
        min-height: 100dvh;
      }
    `
  ]
})
export class AppComponent implements OnInit, OnDestroy {
  private readonly wakeService = inject(ServerWakeService);
  private sub?: Subscription;

  readonly showOverlay = signal(true);
  readonly progress = signal(8);
  readonly message = signal('Les serveurs Render démarrent… Cela peut prendre jusqu’à une minute.');
  readonly timedOut = signal(false);

  ngOnInit(): void {
    this.startWake();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  retry(): void {
    this.startWake();
  }

  private startWake(): void {
    this.sub?.unsubscribe();
    this.timedOut.set(false);
    this.progress.set(8);
    this.message.set('Les serveurs Render démarrent… Cela peut prendre jusqu’à une minute.');
    this.showOverlay.set(true);

    this.sub = this.wakeService.wake().subscribe({
      next: (state) => {
        this.showOverlay.set(true);
        this.progress.set(state.progress);
        this.message.set(state.message);
        this.timedOut.set(state.timedOut);
        if (state.ready) {
          setTimeout(() => this.showOverlay.set(false), 400);
        }
      }
    });
  }
}
