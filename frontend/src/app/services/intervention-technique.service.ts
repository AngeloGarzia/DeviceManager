import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { InterventionTechnique, InterventionTechniqueForm } from '../models/models';

@Injectable({ providedIn: 'root' })
export class InterventionTechniqueService {
  private readonly base = `${environment.apiUrl}/api/interventions-techniques`;

  constructor(private http: HttpClient) {}

  create(payload: InterventionTechniqueForm): Observable<InterventionTechnique[]> {
    return this.http.post<InterventionTechnique[]>(this.base, payload);
  }

  list(): Observable<InterventionTechnique[]> {
    return this.http.get<InterventionTechnique[]>(this.base);
  }

  listByMas(masId: number): Observable<InterventionTechnique[]> {
    const params = new HttpParams().set('masId', String(masId));
    return this.http.get<InterventionTechnique[]>(this.base, { params });
  }

  get(id: number): Observable<InterventionTechnique> {
    return this.http.get<InterventionTechnique>(`${this.base}/${id}`);
  }
}
