import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { DenoOption, Mas, MasForm, MarqueMasOption } from '../models/models';

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
