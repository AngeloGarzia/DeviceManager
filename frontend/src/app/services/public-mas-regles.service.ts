import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface PublicRegleJeux {
  id: number;
  label: string;
  description?: string | null;
  originalName?: string | null;
  contentType?: string | null;
  fileSize?: number | null;
}

export interface PublicMasRegles {
  masNumero: string;
  marqueLabel?: string | null;
  regles: PublicRegleJeux[];
}

/**
 * API publique anonyme — consultation règles de jeux via jeton QR.
 */
@Injectable({ providedIn: 'root' })
export class PublicMasReglesService {
  private readonly base = `${environment.apiUrl}/api/public`;

  constructor(private http: HttpClient) {}

  byToken(token: string): Observable<PublicMasRegles> {
    return this.http.get<PublicMasRegles>(`${this.base}/r/${encodeURIComponent(token)}`);
  }

  downloadFile(token: string, regleId: number): Observable<Blob> {
    return this.http.get(`${this.base}/r/${encodeURIComponent(token)}/regles/${regleId}/file`, {
      responseType: 'blob'
    });
  }
}
