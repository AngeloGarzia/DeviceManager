import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { OrderRequest, OrderRequestForm } from '../models/models';

export interface MailPreviewItem {
  kind: 'ADMIN' | 'SFM' | 'WARNING' | string;
  to: string;
  subject: string;
  body: string;
  sfmNom?: string | null;
}

@Injectable({ providedIn: 'root' })
export class OrderRequestService {
  private readonly base = `${environment.apiUrl}/api/order-requests`;

  /** Nombre de demandes en attente (atelier courant) — badge nav. */
  readonly pendingCount = signal(0);

  constructor(private http: HttpClient) {}

  create(payload: OrderRequestForm): Observable<OrderRequest> {
    return this.http.post<OrderRequest>(this.base, payload).pipe(
      tap(() => this.refreshPendingCount())
    );
  }

  list(): Observable<OrderRequest[]> {
    return this.http.get<OrderRequest[]>(this.base);
  }

  validate(id: number): Observable<OrderRequest> {
    return this.http.post<OrderRequest>(`${this.base}/${id}/validate`, {}).pipe(
      tap(() => this.refreshPendingCount())
    );
  }

  /** Ajuste les quantités / lignes d'une demande non réceptionnée (admin). */
  update(id: number, payload: OrderRequestForm): Observable<OrderRequest> {
    return this.http.put<OrderRequest>(`${this.base}/${id}`, payload);
  }

  /**
   * Confirme la réception : statut RECEIVED + mise à jour du stock.
   * Si `payload` est fourni, les quantités sont ajustées avant la réception.
   */
  receive(id: number, payload?: OrderRequestForm): Observable<OrderRequest> {
    return this.http.post<OrderRequest>(`${this.base}/${id}/receive`, payload ?? {});
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`).pipe(
      tap(() => this.refreshPendingCount())
    );
  }

  previewCreate(payload: OrderRequestForm): Observable<MailPreviewItem[]> {
    return this.http.post<MailPreviewItem[]>(`${this.base}/mail-preview`, payload);
  }

  previewValidate(id: number): Observable<MailPreviewItem[]> {
    return this.http.get<MailPreviewItem[]>(`${this.base}/${id}/mail-preview`);
  }

  refreshPendingCount(): void {
    this.http.get<{ count: number }>(`${this.base}/pending-count`).subscribe({
      next: (res) => this.pendingCount.set(Number(res?.count) || 0),
      error: () => this.pendingCount.set(0)
    });
  }
}
