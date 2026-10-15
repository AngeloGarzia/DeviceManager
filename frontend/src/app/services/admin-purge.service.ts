import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AdminPurgePlanNode {
  entityType: string;
  entityId?: number | null;
  label: string;
  action: 'DELETE' | 'ARCHIVE' | 'DETACH' | 'SKIP' | string;
  detail?: string | null;
}

export interface AdminPurgePlan {
  rootType: string;
  rootId: number;
  rootLabel: string;
  nodes: AdminPurgePlanNode[];
  warnings: string[];
  blockers: string[];
  executable: boolean;
  confirmPhrase: string;
}

export interface AdminPurgeResult {
  rootType: string;
  rootId: number;
  applied: AdminPurgePlanNode[];
  message: string;
}

@Injectable({ providedIn: 'root' })
export class AdminPurgeService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/admin/purge`;

  preview(rootType: string, rootId: number): Observable<AdminPurgePlan> {
    const params = new HttpParams()
      .set('rootType', rootType)
      .set('rootId', String(rootId));
    return this.http.get<AdminPurgePlan>(`${this.base}/preview`, { params });
  }

  execute(rootType: string, rootId: number, confirmPhrase: string): Observable<AdminPurgeResult> {
    return this.http.post<AdminPurgeResult>(`${this.base}/execute`, {
      rootType,
      rootId,
      confirmPhrase
    });
  }
}
