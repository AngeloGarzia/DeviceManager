import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { TimelineEvent } from '../models/models';

@Injectable({ providedIn: 'root' })
export class TimelineService {
  private readonly base = `${environment.apiUrl}/api/timeline`;

  constructor(private http: HttpClient) {}

  list(opts?: {
    from?: string;
    to?: string;
    types?: string[];
    masId?: number;
  }): Observable<TimelineEvent[]> {
    let params = new HttpParams();
    if (opts?.from) {
      params = params.set('from', opts.from);
    }
    if (opts?.to) {
      params = params.set('to', opts.to);
    }
    if (opts?.types?.length) {
      for (const t of opts.types) {
        params = params.append('types', t);
      }
    }
    if (opts?.masId != null) {
      params = params.set('masId', String(opts.masId));
    }
    return this.http.get<TimelineEvent[]>(this.base, { params });
  }
}
