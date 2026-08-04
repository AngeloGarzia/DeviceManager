import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { OrderRequest } from '../../models/models';
import { MailPreviewItem, OrderRequestService } from '../../services/order-request.service';
import { AuthService } from '../../services/auth.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';

/**
 * Liste des demandes de commande de pièces détachées.
 * Permet la validation (admin), l'aperçu des e-mails et la suppression en deux étapes.
 */
@Component({
  selector: 'app-order-request-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    ConfirmDialogComponent
  ],
  templateUrl: './order-request-list.component.html',
  styleUrl: './order-request-list.component.scss'
})
export class OrderRequestListComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly orderService = inject(OrderRequestService);
  readonly items = signal<OrderRequest[]>([]);
  readonly loading = signal(false);
  readonly validatingId = signal<number | null>(null);
  readonly deletingId = signal<number | null>(null);
  readonly previewLoadingId = signal<number | null>(null);
  readonly previewsById = signal<Record<number, MailPreviewItem[]>>({});
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);

  readonly confirmOpen = signal(false);
  /** 1 = première confirmation, 2 = confirmation définitive */
  readonly confirmStep = signal<1 | 2>(1);
  pendingDelete: OrderRequest | null = null;

  ngOnInit(): void {
    this.load();
  }

  /** Charge les demandes de commande de l'atelier courant. */
  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.orderService.list().subscribe({
      next: (data) => {
        this.items.set(data);
        this.loading.set(false);
        this.orderService.refreshPendingCount();
      },
      error: () => {
        this.error.set('Impossible de charger les demandes.');
        this.loading.set(false);
      }
    });
  }

  /** Indique si la demande est en attente de validation. */
  isPending(status: string): boolean {
    return status === 'PENDING' || status === 'SENT';
  }

  /** Libellé français du statut de demande. */
  statusLabel(status: string): string {
    if (status === 'VALIDATED') {
      return 'Validée';
    }
    if (this.isPending(status)) {
      return 'En attente';
    }
    return status;
  }

  /** Affiche ou masque l'aperçu des e-mails de validation pour une demande. */
  toggleMailPreview(item: OrderRequest): void {
    if (!this.auth.isAdmin()) {
      return;
    }
    const existing = this.previewsById()[item.id];
    if (existing) {
      const next = { ...this.previewsById() };
      delete next[item.id];
      this.previewsById.set(next);
      return;
    }
    this.previewLoadingId.set(item.id);
    this.error.set(null);
    this.orderService.previewValidate(item.id).subscribe({
      next: (previews) => {
        this.previewsById.update((map) => ({ ...map, [item.id]: previews }));
        this.previewLoadingId.set(null);
      },
      error: (err) => {
        this.previewLoadingId.set(null);
        this.error.set(err?.error?.message || 'Aperçu impossible.');
      }
    });
  }

  /** Valide une demande en attente et envoie les e-mails aux SFM. */
  validate(item: OrderRequest): void {
    if (!this.auth.isAdmin() || !this.isPending(item.status)) {
      return;
    }
    this.validatingId.set(item.id);
    this.error.set(null);
    this.success.set(null);
    this.orderService.validate(item.id).subscribe({
      next: (updated) => {
        this.items.update((list) => list.map((o) => (o.id === updated.id ? updated : o)));
        this.validatingId.set(null);
        this.previewsById.update((map) => {
          const next = { ...map };
          delete next[item.id];
          return next;
        });
        this.success.set(
          `Demande #${updated.id} validée — e-mails envoyés aux SFM concernés.`
        );
      },
      error: (err) => {
        this.validatingId.set(null);
        this.error.set(err?.error?.message || 'Validation impossible.');
      }
    });
  }

  /** Ouvre la première étape de confirmation de suppression. */
  askDelete(item: OrderRequest): void {
    if (!this.auth.isAdmin()) {
      return;
    }
    this.pendingDelete = item;
    this.confirmStep.set(1);
    this.confirmOpen.set(true);
  }

  /** Annule la suppression en cours et réinitialise l'étape. */
  cancelDelete(): void {
    this.pendingDelete = null;
    this.confirmStep.set(1);
    this.confirmOpen.set(false);
  }

  /** Passe à la confirmation définitive ou supprime la demande. */
  confirmDelete(): void {
    if (!this.pendingDelete) {
      this.cancelDelete();
      return;
    }
    if (this.confirmStep() === 1) {
      this.confirmStep.set(2);
      return;
    }
    const id = this.pendingDelete.id;
    this.confirmOpen.set(false);
    this.deletingId.set(id);
    this.error.set(null);
    this.success.set(null);
    this.orderService.delete(id).subscribe({
      next: () => {
        this.items.update((list) => list.filter((o) => o.id !== id));
        this.previewsById.update((map) => {
          const next = { ...map };
          delete next[id];
          return next;
        });
        this.deletingId.set(null);
        this.pendingDelete = null;
        this.confirmStep.set(1);
        this.success.set(`Demande #${id} supprimée.`);
      },
      error: (err) => {
        this.deletingId.set(null);
        this.pendingDelete = null;
        this.confirmStep.set(1);
        this.error.set(err?.error?.message || 'Suppression impossible.');
      }
    });
  }

  /** Titre de la boîte de dialogue selon l'étape de confirmation. */
  confirmTitle(): string {
    return this.confirmStep() === 1
      ? 'Supprimer la demande'
      : 'Confirmation définitive';
  }

  /** Message de la boîte de dialogue selon l'étape de confirmation. */
  confirmMessage(): string {
    const id = this.pendingDelete?.id;
    if (this.confirmStep() === 1) {
      return id
        ? `Voulez-vous supprimer la demande de commande #${id} ?`
        : 'Voulez-vous supprimer cette demande de commande ?';
    }
    return id
      ? `Dernière confirmation : la demande #${id} sera définitivement supprimée. Cette action est irréversible.`
      : 'Dernière confirmation : cette demande sera définitivement supprimée. Cette action est irréversible.';
  }

  /** Libellé du bouton de confirmation selon l'étape. */
  confirmLabel(): string {
    return this.confirmStep() === 1 ? 'Continuer' : 'Supprimer définitivement';
  }
}
