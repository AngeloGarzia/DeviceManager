import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { OrderRequest } from '../../models/models';
import { MailPreviewItem, OrderRequestService } from '../../services/order-request.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-order-request-list',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatCardModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './order-request-list.component.html',
  styleUrl: './order-request-list.component.scss'
})
export class OrderRequestListComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly orderService = inject(OrderRequestService);
  readonly items = signal<OrderRequest[]>([]);
  readonly loading = signal(false);
  readonly validatingId = signal<number | null>(null);
  readonly previewLoadingId = signal<number | null>(null);
  readonly previewsById = signal<Record<number, MailPreviewItem[]>>({});
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

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

  isPending(status: string): boolean {
    return status === 'PENDING' || status === 'SENT';
  }

  statusLabel(status: string): string {
    if (status === 'VALIDATED') {
      return 'Validée';
    }
    if (this.isPending(status)) {
      return 'En attente';
    }
    return status;
  }

  toggleMailPreview(item: OrderRequest): void {
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
}
