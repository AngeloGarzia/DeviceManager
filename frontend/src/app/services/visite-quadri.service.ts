import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { VisiteQuadri, VisiteQuadriForm, VisiteQuadriObligation } from '../models/models';

@Injectable({ providedIn: 'root' })
export class VisiteQuadriService {
  private readonly base = `${environment.apiUrl}/api/visites-quadri`;
  readonly warningCount = signal(0);

  constructor(private http: HttpClient) {}

  status(): Observable<VisiteQuadriObligation[]> {
    return this.http.get<VisiteQuadriObligation[]>(`${this.base}/status`);
  }

  history(sfmId?: number | null, marqueId?: number | null): Observable<VisiteQuadri[]> {
    let params = new HttpParams();
    if (sfmId != null) {
      params = params.set('sfmId', String(sfmId));
    }
    if (marqueId != null) {
      params = params.set('marqueId', String(marqueId));
    }
    return this.http.get<VisiteQuadri[]>(this.base, { params });
  }

  create(payload: VisiteQuadriForm): Observable<VisiteQuadri> {
    return this.http.post<VisiteQuadri>(this.base, payload).pipe(
      tap(() => this.refreshWarningCount())
    );
  }

  refreshWarningCount(): void {
    this.http.get<{ count: number }>(`${this.base}/warning-count`).subscribe({
      next: (res) => this.warningCount.set(Number(res?.count) || 0),
      error: () => this.warningCount.set(0)
    });
  }
}
