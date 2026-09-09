import { Component, OnDestroy, OnInit, effect, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { Subscription, switchMap, of, catchError, map } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { OrderRequestService } from '../services/order-request.service';
import { VisiteQuadriService } from '../services/visite-quadri.service';
import { ArretMaintenanceService } from '../services/arret-maintenance.service';
import { AiService, MemoireSynaptiqueResponse } from '../services/ai.service';
import { TodoService } from '../services/todo.service';
import { AppTourService } from '../services/app-tour.service';
import { AtelierSituationDialogComponent } from '../shared/atelier-situation-dialog.component';
import { apiErrorMessage } from '../shared/api-error';

/**
 * Coque principale de l'application après connexion.
 * Affiche la barre de navigation, le sélecteur d'atelier, les badges de commandes
 * en attente et le conteneur des pages routées.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatMenuModule,
    AtelierSituationDialogComponent
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss'
})
export class ShellComponent implements OnInit, OnDestroy {
  readonly auth = inject(AuthService);
  readonly orders = inject(OrderRequestService);
  readonly visites = inject(VisiteQuadriService);
  readonly arrets = inject(ArretMaintenanceService);
  readonly todos = inject(TodoService);
  readonly ai = inject(AiService);
  readonly router = inject(Router);
  private readonly tour = inject(AppTourService);

  readonly briefingOpen = signal(false);
  readonly briefingLoading = signal(false);
  readonly briefingError = signal<string | null>(null);
  readonly briefingMemory = signal<MemoireSynaptiqueResponse | null>(null);
  readonly briefingReport = signal<string | null>(null);

  private briefingSub: Subscription | null = null;

  private static readonly BRIEFING_PROMPT =
    'Rédige un rapport de situation concis de cet atelier à partir de la mémoire synaptique : '
    + 'état du parc, alertes (ruptures de stock, arrêts maintenance, tâches À faire, commandes), '
    + 'faits récents notables et priorités recommandées. Français, structuré, maximum 250 mots.';

  constructor() {
    effect(() => {
      // Recharge les badges à chaque changement d'atelier
      this.auth.atelierRevision();
      this.auth.atelierId();
      if (this.auth.getToken()) {
        this.orders.refreshPendingCount();
        this.visites.refreshWarningCount();
        this.arrets.refreshAlert();
        this.todos.refreshPendingCount();
      }
    });
  }

  /** Charge les badges au démarrage. */
  ngOnInit(): void {
    if (this.auth.getToken()) {
      this.orders.refreshPendingCount();
      this.visites.refreshWarningCount();
      this.arrets.refreshAlert();
      this.todos.refreshPendingCount();
      this.ai.refreshStatus();
      // Premier login : lance le parcours après rendu du shell
      window.setTimeout(() => {
        void this.tour.startTour(false);
      }, 600);
    }
  }

  ngOnDestroy(): void {
    this.briefingSub?.unsubscribe();
  }

  /** Relance le parcours guidé (pied de page). */
  restartTour(): void {
    void this.tour.restartTour();
  }

  /**
   * Change d'atelier (admin), recalcule la mémoire synaptique
   * et ouvre le rapport de situation.
   */
  onAtelierSelected(id: number | string): void {
    const previous = this.auth.atelierId();
    this.auth.switchAtelier(id);
    const next = this.auth.atelierId();
    if (next == null || next === previous) {
      return;
    }
    this.openSituationBriefing();
  }

  closeSituationBriefing(): void {
    this.briefingSub?.unsubscribe();
    this.briefingSub = null;
    this.briefingOpen.set(false);
    this.briefingLoading.set(false);
  }

  /** Indique si la section « Pièces détachées » est active dans la barre de navigation. */
  isDevicesSectionActive(): boolean {
    return this.router.url.startsWith('/devices');
  }

  /** Ouvre /devices et déplie la tuile « Pièces détachées ». */
  navigateToDevicesList(): void {
    void this.router.navigate(['/devices'], { queryParams: { open: 'pieces' } });
  }

  /** Indique si la section « Demandes de commande » est active dans la barre de navigation. */
  isOrdersSectionActive(): boolean {
    const url = this.router.url;
    return url.startsWith('/order-request') || url.startsWith('/order-timeline');
  }

  /** Indique si la section MAS est active. */
  isMasSectionActive(): boolean {
    return this.router.url.startsWith('/mas');
  }

  /** Ouvre l'assistant IA si le module est activé. */
  openAiAssistant(): void {
    if (!this.ai.enabled()) {
      return;
    }
    void this.router.navigate(['/ai']);
  }

  /** Réinitialise l'état IA et déconnecte l'utilisateur. */
  logout(): void {
    this.closeSituationBriefing();
    this.ai.reset();
    this.auth.logout();
  }

  private openSituationBriefing(): void {
    this.briefingSub?.unsubscribe();
    this.briefingOpen.set(true);
    this.briefingLoading.set(true);
    this.briefingError.set(null);
    this.briefingMemory.set(null);
    this.briefingReport.set(null);

    this.briefingSub = this.ai
      .rebuildMemory()
      .pipe(
        switchMap((memory) => {
          this.briefingMemory.set(memory);
          if (!this.ai.enabled()) {
            return of({ memory, report: this.formatLocalReport(memory) });
          }
          return this.ai.chat(ShellComponent.BRIEFING_PROMPT).pipe(
            map((res) => ({
              memory,
              report: (res.reply || '').trim() || this.formatLocalReport(memory)
            })),
            catchError(() => of({ memory, report: this.formatLocalReport(memory) }))
          );
        })
      )
      .subscribe({
        next: ({ report }) => {
          this.briefingReport.set(report);
          this.briefingLoading.set(false);
        },
        error: (err) => {
          this.briefingError.set(
            apiErrorMessage(err, 'Impossible de mettre à jour la mémoire synaptique.')
          );
          this.briefingLoading.set(false);
        }
      });
  }

  private formatLocalReport(memory: MemoireSynaptiqueResponse): string {
    const lines: string[] = [];
    lines.push(`Rapport de situation — ${memory.atelierNom || 'atelier'}`);
    lines.push('');
    if (memory.overview?.trim()) {
      lines.push(memory.overview.trim());
    }
    if (memory.recentFacts?.length) {
      lines.push('');
      lines.push('Faits récents :');
      for (const fact of memory.recentFacts.slice(0, 8)) {
        lines.push(`• ${fact}`);
      }
    }
    return lines.join('\n').trim();
  }
}
