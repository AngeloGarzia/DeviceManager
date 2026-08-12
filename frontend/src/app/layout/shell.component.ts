import { Component, OnInit, effect, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { AuthService } from '../services/auth.service';
import { OrderRequestService } from '../services/order-request.service';
import { AiService } from '../services/ai.service';
import { AppTourService } from '../services/app-tour.service';

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
    MatMenuModule
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss'
})
export class ShellComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly orders = inject(OrderRequestService);
  readonly ai = inject(AiService);
  readonly router = inject(Router);
  private readonly tour = inject(AppTourService);

  constructor() {
    effect(() => {
      // Recharge le badge à chaque changement d'atelier
      this.auth.atelierRevision();
      this.auth.atelierId();
      if (this.auth.getToken()) {
        this.orders.refreshPendingCount();
      }
    });
  }

  /** Charge le compteur de commandes en attente et le statut IA au démarrage. */
  ngOnInit(): void {
    if (this.auth.getToken()) {
      this.orders.refreshPendingCount();
      this.ai.refreshStatus();
      // Premier login : lance le parcours après rendu du shell
      window.setTimeout(() => {
        void this.tour.startTour(false);
      }, 600);
    }
  }

  /** Relance le parcours guidé (pied de page). */
  restartTour(): void {
    void this.tour.restartTour();
  }

  /** Indique si la section « Pièces détachées » est active dans la barre de navigation. */
  isDevicesSectionActive(): boolean {
    return this.router.url.startsWith('/devices');
  }

  /** Indique si la section « Demandes de commande » est active dans la barre de navigation. */
  isOrdersSectionActive(): boolean {
    const url = this.router.url;
    return url.startsWith('/order-request') || url.startsWith('/order-timeline');
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
    this.ai.reset();
    this.auth.logout();
  }
}
