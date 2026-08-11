import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
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

  get(id: number): Observable<Intervention> {
    return this.http.get<Intervention>(`${this.base}/${id}`);
  }
}
