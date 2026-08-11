import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { OrderRequest, OrderRequestForm, OrderRequestLine } from '../../models/models';
import { MailPreviewItem, OrderRequestService } from '../../services/order-request.service';
import { AuthService } from '../../services/auth.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { apiErrorMessage } from '../../shared/api-error';

/**
 * Liste des demandes de commande de pièces détachées.
 * Admin : validation SFM, ajustement des quantités, confirmation de réception (+ stock).
 */
@Component({
  selector: 'app-order-request-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
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
  readonly savingId = signal<number | null>(null);
  readonly receivingId = signal<number | null>(null);
  readonly deletingId = signal<number | null>(null);
  readonly previewLoadingId = signal<number | null>(null);
  readonly previewsById = signal<Record<number, MailPreviewItem[]>>({});
  /** Brouillons de lignes éditables (quantités reçues) indexés par id de demande. */
  readonly editLinesById = signal<Record<number, OrderRequestLine[]>>({});
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);

  readonly confirmOpen = signal(false);
  /** Mode de la boîte de dialogue : suppression (2 étapes) ou réception. */
  readonly confirmMode = signal<'delete' | 'receive'>('delete');
  /** 1 = première confirmation, 2 = confirmation définitive (delete uniquement). */
  readonly confirmStep = signal<1 | 2>(1);
  pendingDelete: OrderRequest | null = null;
  pendingReceive: OrderRequest | null = null;

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
        this.syncEditDrafts(data);
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

  /** Indique si la demande est validée (en attente de réception). */
  isValidated(status: string): boolean {
    return status === 'VALIDATED';
  }

  /** Indique si la réception a été confirmée. */
  isReceived(status: string): boolean {
    return status === 'RECEIVED';
  }

  /** Libellé français du statut de demande. */
  statusLabel(status: string): string {
    if (status === 'VALIDATED') {
      return 'Validée';
    }
    if (status === 'RECEIVED') {
      return 'Reçue';
    }
    if (this.isPending(status)) {
      return 'En attente';
    }
    return status;
  }

  /** Lignes affichées / éditables pour une demande. */
  linesOf(item: OrderRequest): OrderRequestLine[] {
    return this.editLinesById()[item.id] ?? item.lignes ?? [];
  }

  /** Met à jour la quantité d'une ligne en brouillon. */
  onQtyChange(orderId: number, deviceId: number, value: number | string | null): void {
    const n = Number(value);
    const qty = Number.isFinite(n) ? Math.max(1, Math.trunc(n)) : 1;
    this.editLinesById.update((map) => ({
      ...map,
      [orderId]: (map[orderId] ?? []).map((line) =>
        line.deviceId === deviceId ? { ...line, quantite: qty } : line
      )
    }));
  }

  /** Retire une ligne du brouillon (pièce non réceptionnée). */
  removeLine(orderId: number, deviceId: number): void {
    this.editLinesById.update((map) => {
      const next = (map[orderId] ?? []).filter((line) => line.deviceId !== deviceId);
      return { ...map, [orderId]: next };
    });
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
        this.error.set(apiErrorMessage(err, 'Aperçu impossible.'));
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
        this.replaceItem(updated);
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
        this.error.set(apiErrorMessage(err, 'Validation impossible.'));
      }
    });
  }

  /** Enregistre les quantités ajustées sans confirmer la réception. */
  saveQuantities(item: OrderRequest): void {
    if (!this.auth.isAdmin() || !this.canEdit(item)) {
      return;
    }
    const payload = this.buildPayload(item);
    if (!payload) {
      return;
    }
    this.savingId.set(item.id);
    this.error.set(null);
    this.success.set(null);
    this.orderService.update(item.id, payload).subscribe({
      next: (updated) => {
        this.replaceItem(updated);
        this.savingId.set(null);
        this.success.set(`Demande #${updated.id} : quantités enregistrées.`);
      },
      error: (err) => {
        this.savingId.set(null);
        this.error.set(apiErrorMessage(err, 'Enregistrement impossible.'));
      }
    });
  }

  /** Ouvre la confirmation de réception (vérification des quantités). */
  askReceive(item: OrderRequest): void {
    if (!this.auth.isAdmin() || !this.isValidated(item.status)) {
      return;
    }
    if (!this.buildPayload(item)) {
      return;
    }
    this.pendingReceive = item;
    this.confirmMode.set('receive');
    this.confirmStep.set(1);
    this.confirmOpen.set(true);
  }

  /** Indique si l'admin peut encore ajuster les lignes. */
  canEdit(item: OrderRequest): boolean {
    return this.isPending(item.status) || this.isValidated(item.status);
  }

  /** Ouvre la première étape de confirmation de suppression. */
  askDelete(item: OrderRequest): void {
    if (!this.auth.isAdmin() || this.isReceived(item.status)) {
      return;
    }
    this.pendingDelete = item;
    this.confirmMode.set('delete');
    this.confirmStep.set(1);
    this.confirmOpen.set(true);
  }

  /** Annule la boîte de dialogue en cours. */
  cancelConfirm(): void {
    this.pendingDelete = null;
    this.pendingReceive = null;
    this.confirmStep.set(1);
    this.confirmOpen.set(false);
  }

  /** Confirme la suppression (2 étapes) ou la réception. */
  confirmAction(): void {
    if (this.confirmMode() === 'receive') {
      this.confirmReceive();
      return;
    }
    if (!this.pendingDelete) {
      this.cancelConfirm();
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
        this.editLinesById.update((map) => {
          const next = { ...map };
          delete next[id];
          return next;
        });
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
        this.error.set(apiErrorMessage(err, 'Suppression impossible.'));
      }
    });
  }

  /** Titre de la boîte de dialogue. */
  confirmTitle(): string {
    if (this.confirmMode() === 'receive') {
      return 'Confirmer la réception';
    }
    return this.confirmStep() === 1 ? 'Supprimer la demande' : 'Confirmation définitive';
  }

  /** Message de la boîte de dialogue. */
  confirmMessage(): string {
    if (this.confirmMode() === 'receive') {
      const id = this.pendingReceive?.id;
      return id
        ? `Confirmez que les quantités de la demande #${id} correspondent à la réception. Le stock des pièces détachées sera mis à jour et le statut passera à « Reçue ».`
        : 'Confirmez la réception : le stock sera mis à jour et le statut passera à « Reçue ».';
    }
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

  /** Libellé du bouton de confirmation. */
  confirmLabel(): string {
    if (this.confirmMode() === 'receive') {
      return 'Confirmer la réception';
    }
    return this.confirmStep() === 1 ? 'Continuer' : 'Supprimer définitivement';
  }

  private confirmReceive(): void {
    if (this.receivingId() != null) {
      return;
    }
    const item = this.pendingReceive;
    if (!item) {
      this.cancelConfirm();
      return;
    }
    const payload = this.buildPayload(item);
    if (!payload) {
      this.cancelConfirm();
      return;
    }
    this.confirmOpen.set(false);
    this.receivingId.set(item.id);
    this.error.set(null);
    this.success.set(null);
    this.orderService.receive(item.id, payload).subscribe({
      next: (updated) => {
        this.replaceItem(updated);
        this.receivingId.set(null);
        this.pendingReceive = null;
        this.success.set(
          `Demande #${updated.id} reçue — stock des pièces détachées mis à jour.`
        );
      },
      error: (err) => {
        this.receivingId.set(null);
        this.pendingReceive = null;
        this.error.set(apiErrorMessage(err, 'Réception impossible.'));
      }
    });
  }

  private buildPayload(item: OrderRequest): OrderRequestForm | null {
    const lines = this.linesOf(item);
    if (lines.length === 0) {
      this.error.set(`Demande #${item.id} : conservez au moins une pièce réceptionnée.`);
      return null;
    }
    return {
      message: item.message?.trim() || 'Réception confirmée',
      lignes: lines.map((line) => ({
        deviceId: line.deviceId,
        quantite: Math.max(1, Math.trunc(Number(line.quantite) || 1))
      }))
    };
  }

  private replaceItem(updated: OrderRequest): void {
    this.items.update((list) => list.map((o) => (o.id === updated.id ? updated : o)));
    this.editLinesById.update((map) => ({
      ...map,
      [updated.id]: this.cloneLines(updated)
    }));
  }

  private syncEditDrafts(data: OrderRequest[]): void {
    const next: Record<number, OrderRequestLine[]> = {};
    for (const item of data) {
      next[item.id] = this.cloneLines(item);
    }
    this.editLinesById.set(next);
  }

  private cloneLines(item: OrderRequest): OrderRequestLine[] {
    return (item.lignes ?? []).map((line) => ({ ...line }));
  }
}
