import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Fit, FitFromMasForm, FitLigneForm, FitSignataires } from '../models/models';

@Injectable({ providedIn: 'root' })
export class FitService {
  private readonly base = `${environment.apiUrl}/api/fit`;

  constructor(private http: HttpClient) {}

  list(): Observable<Fit[]> {
    return this.http.get<Fit[]>(this.base);
  }

  listByMas(masId: number): Observable<Fit[]> {
    const params = new HttpParams().set('masId', String(masId));
    return this.http.get<Fit[]>(this.base, { params });
  }

  listSignataires(): Observable<FitSignataires> {
    return this.http.get<FitSignataires>(`${this.base}/signataires`);
  }

  get(id: number): Observable<Fit> {
    return this.http.get<Fit>(`${this.base}/${id}`);
  }

  ensureFromMas(payload: FitFromMasForm): Observable<Fit> {
    return this.http.post<Fit>(`${this.base}/from-mas`, payload);
  }

  addLigne(fitId: number, payload: FitLigneForm): Observable<Fit> {
    return this.http.post<Fit>(`${this.base}/${fitId}/lignes`, payload);
  }
}
