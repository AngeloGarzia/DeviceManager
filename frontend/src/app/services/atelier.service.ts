import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AtelierRequest,
  AtelierResponsable,
  AtelierSummary,
  CasinoRequest,
  CasinoSummary
} from '../models/models';

@Injectable({ providedIn: 'root' })
export class AtelierService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/ateliers`;

  list(): Observable<AtelierSummary[]> {
    return this.http.get<AtelierSummary[]>(this.base);
  }

  listCasinos(): Observable<CasinoSummary[]> {
    return this.http.get<CasinoSummary[]>(`${this.base}/casinos`);
  }

  createCasino(payload: CasinoRequest): Observable<CasinoSummary> {
    return this.http.post<CasinoSummary>(`${this.base}/casinos`, payload);
  }

  updateCasino(id: number, payload: CasinoRequest): Observable<CasinoSummary> {
    return this.http.put<CasinoSummary>(`${this.base}/casinos/${id}`, payload);
  }

  deleteCasino(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/casinos/${id}`);
  }

  listUsers(): Observable<AtelierResponsable[]> {
    return this.http.get<AtelierResponsable[]>(`${this.base}/users`);
  }

  create(payload: AtelierRequest): Observable<AtelierSummary> {
    return this.http.post<AtelierSummary>(this.base, payload);
  }

  update(id: number, payload: AtelierRequest): Observable<AtelierSummary> {
    return this.http.put<AtelierSummary>(`${this.base}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
