import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { OrderRequest, OrderRequestForm, OrderRequestLine } from '../../models/models';
import {
  AiDevisApplyItem,
  AiDevisPrixSuggestion,
  AiDevisScanResponse,
  AiDevisSuggestion,
  AiDevisUnmatchedPart,
  DevicePrixAlerte,
  MailPreviewItem,
  OrderRequestService
} from '../../services/order-request.service';
import { AiService } from '../../services/ai.service';
import { AuthService } from '../../services/auth.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { apiErrorMessage } from '../../shared/api-error';
import { isPdfFile, isPdfOrImageFile, PDF_OR_IMAGE_ACCEPT } from '../../shared/document-upload';

interface DevisReviewRow {
  suggestion: AiDevisSuggestion;
  acceptNom: boolean;
  acceptReference: boolean;
}

interface DevisPrixReviewRow {
  suggestion: AiDevisPrixSuggestion;
  accept: boolean;
}

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
    MatCheckboxModule,
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
  private readonly aiService = inject(AiService);
  readonly pdfOrImageAccept = PDF_OR_IMAGE_ACCEPT;
  readonly items = signal<OrderRequest[]>([]);
  readonly loading = signal(false);
  readonly validatingId = signal<number | null>(null);
  readonly savingId = signal<number | null>(null);
  readonly receivingId = signal<number | null>(null);
  readonly deletingId = signal<number | null>(null);
  readonly devisUploadingId = signal<number | null>(null);
  readonly devisAnalyzing = signal(false);
  readonly devisApplying = signal(false);
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

  /** Modale revue IA devis → pièces. */
  readonly devisReviewOpen = signal(false);
  readonly devisReviewOrderId = signal<number | null>(null);
  readonly devisReviewRows = signal<DevisReviewRow[]>([]);
  readonly devisReviewUnmatched = signal<AiDevisUnmatchedPart[]>([]);
  readonly devisReviewNotes = signal<string | null>(null);
  private pendingDevisFile: File | null = null;

  /** Modale revue prix devis. */
  readonly devisPrixReviewOpen = signal(false);
  readonly devisPrixRows = signal<DevisPrixReviewRow[]>([]);
  readonly devisPrixUnmatched = signal<AiDevisUnmatchedPart[]>([]);
  readonly devisPrixNotes = signal<string | null>(null);
  readonly devisPrixAlertes = signal<DevicePrixAlerte[]>([]);

  ngOnInit(): void {
    this.aiService.refreshStatus();
    this.load();
  }

  /** Charge les demandes de commande de l'atelier courant. */
  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.orderService.list().subscribe({
      next: (data) => {
        const sorted = this.sortByStatus(data);
        this.items.set(sorted);
        this.syncEditDrafts(sorted);
        this.loading.set(false);
        this.orderService.refreshPendingCount();
      },
      error: () => {
        this.error.set('Impossible de charger les demandes.');
        this.loading.set(false);
      }
    });
  }

  /**
   * Trie par statut métier (en attente → validée → reçue), puis date décroissante.
   */
  private sortByStatus(data: OrderRequest[]): OrderRequest[] {
    return [...data].sort((a, b) => {
      const rankDiff = this.statusSortRank(a.status) - this.statusSortRank(b.status);
      if (rankDiff !== 0) {
        return rankDiff;
      }
      const dateA = Date.parse(a.dateDemande || a.createdAt || '') || 0;
      const dateB = Date.parse(b.dateDemande || b.createdAt || '') || 0;
      return dateB - dateA;
    });
  }

  /** Rang de tri : plus petit = plus prioritaire à l'affichage. */
  private statusSortRank(status: string): number {
    if (this.isPending(status)) {
      return 0;
    }
    if (this.isValidated(status)) {
      return 1;
    }
    if (this.isReceived(status)) {
      return 2;
    }
    return 3;
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

  /** Devis PDF ou image autorisé après validation (y compris réceptionnée). */
  canAttachDevis(status: string): boolean {
    return this.isValidated(status) || this.isReceived(status);
  }

  devisUrl(item: OrderRequest): string {
    return this.orderService.resolveDevisUrl(item.devisFileUrl);
  }

  isDevisImage(item: OrderRequest): boolean {
    const ct = (item.devisContentType || '').toLowerCase();
    const name = (item.devisOriginalName || '').toLowerCase();
    return ct.startsWith('image/') || /\.(png|jpe?g|webp|gif)$/.test(name);
  }

  pickDevis(orderId: number): void {
    const input = document.getElementById(`devis-input-${orderId}`) as HTMLInputElement | null;
    input?.click();
  }

  onDevisSelected(item: OrderRequest, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || !this.auth.isAdmin() || !this.canAttachDevis(item.status)) {
      return;
    }
    if (!isPdfOrImageFile(file)) {
      this.error.set('Le devis doit être un PDF ou une capture image.');
      return;
    }
    this.devisUploadingId.set(item.id);
    this.error.set(null);
    this.success.set(null);
    this.orderService.attachDevis(item.id, file).subscribe({
      next: (updated) => {
        this.replaceItem(updated);
        this.devisUploadingId.set(null);
        this.success.set(`Devis associé à la demande #${updated.id}.`);
        if (this.aiService.enabled() && isPdfFile(file)) {
          this.pendingDevisFile = file;
          this.runDevisAnalyze(updated.id, file);
        }
      },
      error: (err) => {
        this.devisUploadingId.set(null);
        this.error.set(apiErrorMessage(err, 'Envoi du devis impossible.'));
      }
    });
  }

  private runDevisAnalyze(orderId: number, file: File): void {
    this.devisAnalyzing.set(true);
    this.orderService.analyzeDevis(orderId, file).subscribe({
      next: (scan) => this.openDevisReview(orderId, scan),
      error: (err) => {
        this.devisAnalyzing.set(false);
        this.error.set(
          apiErrorMessage(err, 'Devis enregistré, mais l’analyse IA (noms) a échoué.')
        );
        this.runDevisPrixAnalyze(orderId, file);
      }
    });
  }

  private openDevisReview(orderId: number, scan: AiDevisScanResponse): void {
    this.devisAnalyzing.set(false);
    const suggestions = (scan.suggestions ?? []).filter((s) => s.hasChanges !== false);
    this.devisReviewOrderId.set(orderId);
    this.devisReviewNotes.set(scan.notes?.trim() || null);
    this.devisReviewUnmatched.set(scan.unmatched ?? []);
    this.devisReviewRows.set(
      suggestions.map((suggestion) => ({
        suggestion,
        acceptNom: !!suggestion.suggestedNom,
        acceptReference: !!suggestion.suggestedReference
      }))
    );
    if (suggestions.length === 0 && (scan.unmatched?.length ?? 0) === 0) {
      this.success.set(
        `Devis associé — aucun écart nom/réf. pour la demande #${orderId}.`
      );
      this.runDevisPrixAnalyze(orderId, this.pendingDevisFile);
      return;
    }
    this.devisReviewOpen.set(true);
  }

  closeDevisReview(): void {
    const orderId = this.devisReviewOrderId();
    const file = this.pendingDevisFile;
    this.devisReviewOpen.set(false);
    this.devisReviewOrderId.set(null);
    this.devisReviewRows.set([]);
    this.devisReviewUnmatched.set([]);
    this.devisReviewNotes.set(null);
    if (orderId != null) {
      this.runDevisPrixAnalyze(orderId, file);
    }
  }

  setAcceptNom(deviceId: number, value: boolean): void {
    this.devisReviewRows.update((rows) =>
      rows.map((row) =>
        row.suggestion.deviceId === deviceId ? { ...row, acceptNom: value } : row
      )
    );
  }

  setAcceptReference(deviceId: number, value: boolean): void {
    this.devisReviewRows.update((rows) =>
      rows.map((row) =>
        row.suggestion.deviceId === deviceId ? { ...row, acceptReference: value } : row
      )
    );
  }

  applyDevisReview(): void {
    const orderId = this.devisReviewOrderId();
    if (orderId == null) {
      return;
    }
    const items: AiDevisApplyItem[] = this.devisReviewRows()
      .filter((row) => row.acceptNom || row.acceptReference)
      .map((row) => ({
        deviceId: row.suggestion.deviceId,
        updateNom: row.acceptNom && !!row.suggestion.suggestedNom,
        updateReference: row.acceptReference && !!row.suggestion.suggestedReference,
        suggestedNom: row.suggestion.suggestedNom,
        suggestedReference: row.suggestion.suggestedReference
      }))
      .filter((item) => item.updateNom || item.updateReference);

    const file = this.pendingDevisFile;

    if (items.length === 0) {
      this.devisReviewOpen.set(false);
      this.devisReviewOrderId.set(null);
      this.devisReviewRows.set([]);
      this.devisReviewUnmatched.set([]);
      this.devisReviewNotes.set(null);
      this.success.set('Aucune mise à jour nom/réf. appliquée.');
      this.runDevisPrixAnalyze(orderId, file);
      return;
    }

    this.devisApplying.set(true);
    this.orderService.applyDevisUpdates(orderId, { items }).subscribe({
      next: (res) => {
        this.devisApplying.set(false);
        if (res.order) {
          this.replaceItem(res.order);
        }
        this.devisReviewOpen.set(false);
        this.devisReviewOrderId.set(null);
        this.devisReviewRows.set([]);
        this.devisReviewUnmatched.set([]);
        this.devisReviewNotes.set(null);
        const errCount = res.errors?.length ?? 0;
        if (errCount > 0) {
          this.error.set(
            `${res.updatedCount} pièce(s) mise(s) à jour. ${errCount} erreur(s) : ${(res.errors || []).join(' ; ')}`
          );
        } else {
          this.success.set(
            `${res.updatedCount} pièce(s) mise(s) à jour à partir du devis.`
          );
        }
        this.runDevisPrixAnalyze(orderId, file);
      },
      error: (err) => {
        this.devisApplying.set(false);
        this.error.set(apiErrorMessage(err, 'Application des mises à jour impossible.'));
      }
    });
  }

  private runDevisPrixAnalyze(orderId: number, file: File | null): void {
    if (!file || !this.aiService.enabled() || !isPdfFile(file)) {
      this.pendingDevisFile = null;
      return;
    }
    this.devisAnalyzing.set(true);
    this.orderService.analyzeDevisPrices(orderId, file).subscribe({
      next: (scan) => {
        this.devisAnalyzing.set(false);
        this.openDevisPrixReview(orderId, scan.suggestions ?? [], scan.unmatched ?? [], scan.notes);
      },
      error: (err) => {
        this.devisAnalyzing.set(false);
        this.pendingDevisFile = null;
        this.error.set(
          apiErrorMessage(err, 'Analyse des prix du devis impossible.')
        );
      }
    });
  }

  private openDevisPrixReview(
    orderId: number,
    suggestions: AiDevisPrixSuggestion[],
    unmatched: AiDevisUnmatchedPart[],
    notes?: string | null
  ): void {
    this.devisReviewOrderId.set(orderId);
    this.devisPrixNotes.set(notes?.trim() || null);
    this.devisPrixUnmatched.set(unmatched);
    this.devisPrixRows.set(
      suggestions.map((suggestion) => ({ suggestion, accept: true }))
    );
    this.devisPrixAlertes.set([]);
    if (suggestions.length === 0) {
      this.pendingDevisFile = null;
      this.success.set(
        (this.success() ? this.success() + ' ' : '') +
          `Aucun prix extractible pour la demande #${orderId}.`
      );
      return;
    }
    this.devisPrixReviewOpen.set(true);
  }

  closeDevisPrixReview(): void {
    this.devisPrixReviewOpen.set(false);
    this.devisPrixRows.set([]);
    this.devisPrixUnmatched.set([]);
    this.devisPrixNotes.set(null);
    this.devisPrixAlertes.set([]);
    this.pendingDevisFile = null;
  }

  setAcceptPrix(deviceId: number, value: boolean): void {
    this.devisPrixRows.update((rows) =>
      rows.map((row) =>
        row.suggestion.deviceId === deviceId ? { ...row, accept: value } : row
      )
    );
  }

  applyDevisPrixReview(): void {
    const orderId = this.devisReviewOrderId();
    if (orderId == null) {
      return;
    }
    const items = this.devisPrixRows()
      .filter((row) => row.accept && row.suggestion.suggestedUnitPriceHt != null)
      .map((row) => ({
        deviceId: row.suggestion.deviceId,
        unitPriceHt: Number(row.suggestion.suggestedUnitPriceHt),
        quantityOnQuote: row.suggestion.quantityOnQuote ?? null,
        devisDesignation: row.suggestion.devisDesignation ?? null,
        devisReference: row.suggestion.devisReference ?? null
      }));
    if (items.length === 0) {
      this.closeDevisPrixReview();
      this.success.set('Aucun prix confirmé.');
      return;
    }
    this.devisApplying.set(true);
    this.orderService.confirmDevisPrices(orderId, items).subscribe({
      next: (res) => {
        this.devisApplying.set(false);
        this.pendingDevisFile = null;
        this.devisPrixAlertes.set(res.alertes ?? []);
        const alertCount = res.alertes?.length ?? 0;
        const errCount = res.errors?.length ?? 0;
        let msg = `${res.confirmedCount} prix confirmé(s) dans l’historique.`;
        if (alertCount > 0) {
          msg += ` ${alertCount} alerte(s) d’incohérence.`;
        }
        if (errCount > 0) {
          this.error.set(`${msg} Erreurs : ${(res.errors || []).join(' ; ')}`);
        } else {
          this.success.set(msg);
        }
        if (alertCount === 0) {
          this.closeDevisPrixReview();
        }
      },
      error: (err) => {
        this.devisApplying.set(false);
        this.error.set(apiErrorMessage(err, 'Confirmation des prix impossible.'));
      }
    });
  }

  formatPrice(value?: number | null): string {
    if (value === null || value === undefined || Number.isNaN(Number(value))) {
      return '—';
    }
    return `${Number(value).toFixed(2)} €`;
  }

  confidenceLabel(value?: string | null): string {
    if (value === 'HIGH') {
      return 'Élevée';
    }
    if (value === 'LOW') {
      return 'Faible';
    }
    return 'Moyenne';
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
    this.items.update((list) =>
      this.sortByStatus(list.map((o) => (o.id === updated.id ? updated : o)))
    );
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
