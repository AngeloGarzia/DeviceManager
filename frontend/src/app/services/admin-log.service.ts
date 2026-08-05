import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AdminLogEntry {
  id: number;
  timestamp: string;
  level: string;
  logger: string;
  thread: string;
  message: string;
  throwable?: string | null;
}

export interface AdminLogList {
  totalCount: number;
  retentionMax: number;
  returned: number;
  items: AdminLogEntry[];
}

export interface AdminLogQuery {
  level?: string;
  logger?: string;
  q?: string;
  limit?: number;
}

/** Consultation des logs SLF4J (API admin uniquement). */
@Injectable({ providedIn: 'root' })
export class AdminLogService {
  private readonly base = `${environment.apiUrl}/api/logs`;

  constructor(private http: HttpClient) {}

  list(query: AdminLogQuery = {}): Observable<AdminLogList> {
    let params = new HttpParams();
    if (query.level) {
      params = params.set('level', query.level);
    }
    if (query.logger) {
      params = params.set('logger', query.logger);
    }
    if (query.q) {
      params = params.set('q', query.q);
    }
    if (query.limit != null) {
      params = params.set('limit', String(query.limit));
    }
    return this.http.get<AdminLogList>(this.base, { params });
  }

  clear(): Observable<void> {
    return this.http.delete<void>(this.base);
  }
}
