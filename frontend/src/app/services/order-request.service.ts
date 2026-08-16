import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
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

export interface AiDevisSuggestion {
  deviceId: number;
  currentNom?: string | null;
  currentReference?: string | null;
  suggestedNom?: string | null;
  suggestedReference?: string | null;
  confidence?: string | null;
  hasChanges?: boolean;
}

export interface AiDevisUnmatchedPart {
  designation?: string | null;
  reference?: string | null;
}

export interface AiDevisScanResponse {
  enabled: boolean;
  notes?: string | null;
  suggestions?: AiDevisSuggestion[];
  unmatched?: AiDevisUnmatchedPart[];
}

export interface AiDevisApplyItem {
  deviceId: number;
  updateNom?: boolean;
  updateReference?: boolean;
  suggestedNom?: string | null;
  suggestedReference?: string | null;
}

export interface AiDevisApplyRequest {
  items: AiDevisApplyItem[];
}

export interface AiDevisApplyResponse {
  order: OrderRequest;
  updatedCount: number;
  errors?: string[];
}

export interface AiDevisPrixSuggestion {
  deviceId: number;
  currentNom?: string | null;
  currentReference?: string | null;
  lastUnitPriceHt?: number | null;
  suggestedUnitPriceHt: number;
  quantityOnQuote?: number | null;
  devisDesignation?: string | null;
  devisReference?: string | null;
  confidence?: string | null;
}

export interface AiDevisPrixScanResponse {
  enabled: boolean;
  notes?: string | null;
  suggestions?: AiDevisPrixSuggestion[];
  unmatched?: AiDevisUnmatchedPart[];
}

export interface AiDevisPrixConfirmItem {
  deviceId: number;
  unitPriceHt: number;
  quantityOnQuote?: number | null;
  devisDesignation?: string | null;
  devisReference?: string | null;
}

export interface DevicePrixObservation {
  id: number;
  deviceId: number;
  deviceNom?: string | null;
  commandeId?: number | null;
  source: string;
  unitPriceHt: number;
  currency: string;
  quantityOnQuote?: number | null;
  devisDesignation?: string | null;
  devisReference?: string | null;
  observedAt: string;
  confirmedAt: string;
  confirmedBy: string;
  invalidated: boolean;
}

export interface DevicePrixAlerte {
  id: number;
  deviceId: number;
  deviceNom?: string | null;
  deviceReference?: string | null;
  observationId: number;
  unitPriceHt?: number | null;
  severity: string;
  signals?: string[];
  aiSummary?: string | null;
  status: string;
  createdAt: string;
  ackBy?: string | null;
  ackAt?: string | null;
}

export interface AiDevisPrixConfirmResponse {
  confirmedCount: number;
  observations?: DevicePrixObservation[];
  alertes?: DevicePrixAlerte[];
  errors?: string[];
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

  list(opts?: { masIds?: number[] }): Observable<OrderRequest[]> {
    let params = new HttpParams();
    if (opts?.masIds?.length) {
      for (const id of opts.masIds) {
        params = params.append('masIds', String(id));
      }
    }
    return this.http.get<OrderRequest[]>(this.base, { params });
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

  /** Associe (ou remplace) un devis PDF à une commande validée / réceptionnée. */
  attachDevis(id: number, file: File): Observable<OrderRequest> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<OrderRequest>(`${this.base}/${id}/devis`, form);
  }

  /** Analyse IA du devis : propositions de mise à jour nom/référence des pièces. */
  analyzeDevis(id: number, file: File): Observable<AiDevisScanResponse> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<AiDevisScanResponse>(`${this.base}/${id}/devis/analyze`, form);
  }

  /** Applique les mises à jour acceptées après analyse du devis. */
  applyDevisUpdates(id: number, payload: AiDevisApplyRequest): Observable<AiDevisApplyResponse> {
    return this.http.post<AiDevisApplyResponse>(`${this.base}/${id}/devis/apply-updates`, payload);
  }

  analyzeDevisPrices(id: number, file: File): Observable<AiDevisPrixScanResponse> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<AiDevisPrixScanResponse>(`${this.base}/${id}/devis/analyze-prices`, form);
  }

  confirmDevisPrices(
    id: number,
    items: AiDevisPrixConfirmItem[]
  ): Observable<AiDevisPrixConfirmResponse> {
    return this.http.post<AiDevisPrixConfirmResponse>(`${this.base}/${id}/devis/confirm-prices`, {
      items
    });
  }

  resolveDevisUrl(fileUrl?: string | null): string {
    if (!fileUrl) {
      return '';
    }
    if (fileUrl.startsWith('http')) {
      return fileUrl;
    }
    return `${environment.apiUrl}${fileUrl}`;
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
