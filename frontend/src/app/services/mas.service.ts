import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Mas, MasForm, MarqueMasOption } from '../models/models';

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

  get(id: number): Observable<Mas> {
    return this.http.get<Mas>(`${this.base}/${id}`);
  }

  create(payload: MasForm): Observable<Mas> {
    return this.http.post<Mas>(this.base, payload);
  }

  update(id: number, payload: MasForm): Observable<Mas> {
    return this.http.put<Mas>(`${this.base}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
