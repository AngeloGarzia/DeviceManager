import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Intervention, InterventionForm } from '../models/models';

@Injectable({ providedIn: 'root' })
export class InterventionService {
  private readonly base = `${environment.apiUrl}/api/interventions`;

  constructor(private http: HttpClient) {}

  create(payload: InterventionForm): Observable<Intervention> {
    return this.http.post<Intervention>(this.base, payload);
  }

  list(): Observable<Intervention[]> {
    return this.http.get<Intervention[]>(this.base);
  }

  listByMas(masId: number): Observable<Intervention[]> {
    const params = new HttpParams().set('masId', String(masId));
    return this.http.get<Intervention[]>(this.base, { params });
  }

  get(id: number): Observable<Intervention> {
    return this.http.get<Intervention>(`${this.base}/${id}`);
  }
}
