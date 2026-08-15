import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AiProviderAvailability {
  id: string;
  label: string;
  hasApiKey: boolean;
}

export interface AiChatResponse {
  reply: string;
  enabled: boolean;
  providers?: AiProviderAvailability[];
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

export interface AiPdfScanResponse {
  enabled: boolean;
  informationTechnique?: string | null;
  notes?: string | null;
}

export interface AiModelOption {
  id: string;
  label: string;
  vision: boolean;
}

export interface AiModelsResponse {
  providerId: string;
  providerLabel: string;
  hasApiKey: boolean;
  message?: string | null;
  models: AiModelOption[];
}

@Injectable({ providedIn: 'root' })
export class AiService {
  private readonly base = `${environment.apiUrl}/api/ai`;

  /** IA activée + clé API renseignée (état serveur). */
  readonly enabled = signal(false);
  readonly statusMessage = signal<string | null>(null);
  readonly statusLoaded = signal(false);
  /** id fournisseur → clé .env présente */
  readonly providerKeyStatus = signal<Record<string, boolean>>({});
  /** Fournisseurs renvoyés par /api/ai/status (id + label). */
  readonly providers = signal<AiProviderAvailability[]>([]);

  readonly disabledReason = computed(() =>
    this.enabled()
      ? null
      : this.statusMessage() ||
        'IA désactivée — activez-la dans les paramètres et choisissez un fournisseur avec clé.'
  );

  constructor(private http: HttpClient) {}

  status(): Observable<AiChatResponse> {
    return this.http.get<AiChatResponse>(`${this.base}/status`).pipe(tap((res) => this.applyStatus(res)));
  }

  /** Rafraîchit le statut partagé (toolbar, formulaire pièce, etc.). */
  refreshStatus(): void {
    this.status().subscribe({
      error: () => {
        this.enabled.set(false);
        this.statusMessage.set('Impossible de contacter l’assistant IA.');
        this.providerKeyStatus.set({});
        this.providers.set([]);
        this.statusLoaded.set(true);
      }
    });
  }

  hasProviderKey(providerId: string): boolean {
    const status = this.providerKeyStatus();
    // Pas dans la map = pas de clé (ou fournisseur inconnu côté API)
    return status[providerId] === true;
  }

  reset(): void {
    this.enabled.set(false);
    this.statusMessage.set(null);
    this.providerKeyStatus.set({});
    this.providers.set([]);
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

  analyzePdf(
    pdf: File,
    opts?: { docType?: string; nom?: string | null; reference?: string | null }
  ): Observable<AiPdfScanResponse> {
    const form = new FormData();
    form.append('pdf', pdf, pdf.name || 'document.pdf');
    const params: Record<string, string> = {};
    if (opts?.docType) {
      params['docType'] = opts.docType;
    }
    if (opts?.nom?.trim()) {
      params['nom'] = opts.nom.trim();
    }
    if (opts?.reference?.trim()) {
      params['reference'] = opts.reference.trim();
    }
    return this.http.post<AiPdfScanResponse>(`${this.base}/pdf-scan`, form, { params });
  }

  /** Modèles chat disponibles en ligne pour un fournisseur (pas de catalogue en dur). */
  listModels(provider: string): Observable<AiModelsResponse> {
    return this.http.get<AiModelsResponse>(`${this.base}/models`, {
      params: { provider }
    });
  }

  private applyStatus(res: AiChatResponse): void {
    this.enabled.set(!!res.enabled);
    this.statusMessage.set(res.reply || null);
    const map: Record<string, boolean> = {};
    const list = res.providers ?? [];
    for (const p of list) {
      map[p.id] = !!p.hasApiKey;
    }
    // Si l'API ne renvoie pas encore la liste, marquer explicitement l'absence
    // pour éviter d'afficher tous les fournisseurs comme disponibles.
    this.providerKeyStatus.set(map);
    this.providers.set(list);
    this.statusLoaded.set(true);
  }
}
