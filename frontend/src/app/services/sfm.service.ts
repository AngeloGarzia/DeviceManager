import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Sfm, SfmForm, SfmTechnicien } from '../models/models';

@Injectable({ providedIn: 'root' })
export class SfmService {
  private readonly base = `${environment.apiUrl}/api/sfm`;

  constructor(private http: HttpClient) {}

  list(q = ''): Observable<Sfm[]> {
    let params = new HttpParams();
    if (q.trim()) {
      params = params.set('q', q.trim());
    }
    return this.http.get<Sfm[]>(this.base, { params });
  }

  listTechniciens(): Observable<SfmTechnicien[]> {
    return this.http.get<SfmTechnicien[]>(`${this.base}/contacts/techniciens`);
  }

  get(id: number): Observable<Sfm> {
    return this.http.get<Sfm>(`${this.base}/${id}`);
  }

  create(payload: SfmForm): Observable<Sfm> {
    return this.http.post<Sfm>(this.base, payload);
  }

  update(id: number, payload: SfmForm): Observable<Sfm> {
    return this.http.put<Sfm>(`${this.base}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
