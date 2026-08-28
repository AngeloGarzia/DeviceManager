import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { DenoOption, Mas, MasForm, MarqueMasOption, RegleJeuxOption } from '../models/models';

export interface AiRegleJeuxScanResponse {
  enabled: boolean;
  label?: string | null;
  description?: string | null;
  notes?: string | null;
}

@Injectable({ providedIn: 'root' })
export class MasService {
  private readonly base = `${environment.apiUrl}/api/mas`;

  constructor(private http: HttpClient) {}

  list(q = ''): Observable<Mas[]> {
    let params = new HttpParams();
    if (q.trim()) {
      params = params.set('q', q.trim());
    }
    return this.http.get<Mas[]>(this.base, { params });
  }

  listMarques(): Observable<MarqueMasOption[]> {
    return this.http.get<MarqueMasOption[]>(`${this.base}/marques`);
  }

  createMarque(label: string): Observable<MarqueMasOption> {
    return this.http.post<MarqueMasOption>(`${this.base}/marques`, { label });
  }

  listDenos(): Observable<DenoOption[]> {
    return this.http.get<DenoOption[]>(`${this.base}/denos`);
  }

  createDeno(valeur: number, label?: string): Observable<DenoOption> {
    return this.http.post<DenoOption>(`${this.base}/denos`, {
      valeur,
      label: label?.trim() || null
    });
  }

  listReglesJeux(): Observable<RegleJeuxOption[]> {
    return this.http.get<RegleJeuxOption[]>(`${this.base}/regles-jeux`);
  }

  analyzeRegleJeuxPdf(file: File): Observable<AiRegleJeuxScanResponse> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<AiRegleJeuxScanResponse>(`${this.base}/regles-jeux/analyze`, form);
  }

  createRegleJeux(label: string, file: File, description?: string | null): Observable<RegleJeuxOption> {
    const form = new FormData();
    form.append('label', label.trim());
    if (description?.trim()) {
      form.append('description', description.trim());
    }
    form.append('file', file, file.name);
    return this.http.post<RegleJeuxOption>(`${this.base}/regles-jeux`, form);
  }

  updateRegleJeux(id: number, label: string, description?: string | null): Observable<RegleJeuxOption> {
    return this.http.put<RegleJeuxOption>(`${this.base}/regles-jeux/${id}`, {
      label: label.trim(),
      description: description?.trim() || null
    });
  }

  replaceRegleJeuxDocument(id: number, file: File): Observable<RegleJeuxOption> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<RegleJeuxOption>(`${this.base}/regles-jeux/${id}/document`, form);
  }

  deleteRegleJeux(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/regles-jeux/${id}`);
  }

  getRegleJeux(id: number): Observable<RegleJeuxOption> {
    return this.http.get<RegleJeuxOption>(`${this.base}/regles-jeux/${id}`);
  }

  linkRegleJeuxMas(regleId: number, masIds: number[]): Observable<RegleJeuxOption> {
    return this.http.put<RegleJeuxOption>(`${this.base}/regles-jeux/${regleId}/mas`, { masIds });
  }

  get(id: number): Observable<Mas> {
    return this.http.get<Mas>(`${this.base}/${id}`);
  }

  create(payload: MasForm): Observable<Mas> {
    return this.http.post<Mas>(this.base, payload);
  }

  update(id: number, payload: MasForm): Observable<Mas> {
    return this.http.put<Mas>(`${this.base}/${id}`, payload);
  }

  /** Associe un bon de destruction (PDF ou image) à une MAS détruite. */
  attachBonDestruction(id: number, file: File): Observable<Mas> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<Mas>(`${this.base}/${id}/bon-destruction`, form);
  }

  resolveFileUrl(fileUrl?: string | null): string {
    if (!fileUrl) {
      return '';
    }
    if (fileUrl.startsWith('http')) {
      return fileUrl;
    }
    return `${environment.apiUrl}${fileUrl}`;
  }
}
