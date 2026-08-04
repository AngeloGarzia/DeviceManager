import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AiChatResponse {
  reply: string;
  enabled: boolean;
}

export interface AiLabelScanResponse {
  enabled: boolean;
  nom?: string | null;
  reference?: string | null;
  marque?: string | null;
  usage?: string | null;
  rawText?: string | null;
  notes?: string | null;
}

@Injectable({ providedIn: 'root' })
export class AiService {
  private readonly base = `${environment.apiUrl}/api/ai`;

  /** IA activée + clé API renseignée (état serveur). */
  readonly enabled = signal(false);
  readonly statusMessage = signal<string | null>(null);
  readonly statusLoaded = signal(false);

  readonly disabledReason = computed(() =>
    this.enabled()
      ? null
      : this.statusMessage() || 'IA désactivée — activez-la dans Setup (clé API).'
  );

  constructor(private http: HttpClient) {}

  status(): Observable<AiChatResponse> {
    return this.http.get<AiChatResponse>(`${this.base}/status`).pipe(
      tap((res) => this.applyStatus(res))
    );
  }

  /** Rafraîchit le statut partagé (toolbar, formulaire pièce, etc.). */
  refreshStatus(): void {
    this.status().subscribe({
      error: () => {
        this.enabled.set(false);
        this.statusMessage.set('Impossible de contacter l’assistant IA.');
        this.statusLoaded.set(true);
      }
    });
  }

  reset(): void {
    this.enabled.set(false);
    this.statusMessage.set(null);
    this.statusLoaded.set(false);
  }

  chat(message: string): Observable<AiChatResponse> {
    return this.http.post<AiChatResponse>(`${this.base}/chat`, { message });
  }

  scanLabel(image: File | Blob): Observable<AiLabelScanResponse> {
    const form = new FormData();
    const file =
      image instanceof File
        ? image
        : new File([image], `label-${Date.now()}.jpg`, { type: image.type || 'image/jpeg' });
    form.append('image', file);
    return this.http.post<AiLabelScanResponse>(`${this.base}/label-scan`, form);
  }

  private applyStatus(res: AiChatResponse): void {
    this.enabled.set(!!res.enabled);
    this.statusMessage.set(res.reply || null);
    this.statusLoaded.set(true);
  }
}
