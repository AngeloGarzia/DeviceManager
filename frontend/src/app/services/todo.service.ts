import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { TodoItem, TodoList, TodoTacheForm } from '../models/models';

@Injectable({ providedIn: 'root' })
export class TodoService {
  private readonly base = `${environment.apiUrl}/api/todos`;

  /** Nombre de tâches actives (ouvertes + en cours) pour le badge nav. */
  readonly pendingCount = signal(0);

  constructor(private http: HttpClient) {}

  list(all = false): Observable<TodoList> {
    let params = new HttpParams();
    if (all) {
      params = params.set('all', 'true');
    }
    return this.http.get<TodoList>(this.base, { params }).pipe(
      tap((data) => {
        if (!all) {
          this.pendingCount.set(data.count ?? data.items?.length ?? 0);
        } else {
          const active = (data.items ?? []).filter(
            (t) => t.statut === 'OPEN' || t.statut === 'IN_PROGRESS'
          ).length;
          this.pendingCount.set(active);
        }
      })
    );
  }

  refreshPendingCount(): void {
    this.list(false).subscribe({ error: () => this.pendingCount.set(0) });
  }

  create(payload: TodoTacheForm): Observable<TodoItem> {
    return this.http
      .post<TodoItem>(`${this.base}`, {
        titre: payload.titre.trim(),
        description: payload.description?.trim() || null,
        severite: payload.severite || 'MEDIUM',
        masId: payload.masId ?? null
      })
      .pipe(tap(() => this.refreshPendingCount()));
  }

  updateStatus(
    id: number,
    statut: string,
    opts?: {
      signatureCloture?: string | null;
      signataireClotureNom?: string | null;
      commentaireCloture?: string | null;
      dateHeureCloture?: string | null;
    }
  ): Observable<TodoItem> {
    return this.http
      .put<TodoItem>(`${this.base}/${id}/statut`, {
        statut,
        signatureCloture: opts?.signatureCloture ?? null,
        signataireClotureNom: opts?.signataireClotureNom ?? null,
        commentaireCloture: opts?.commentaireCloture ?? null,
        dateHeureCloture: opts?.dateHeureCloture ?? null
      })
      .pipe(tap(() => this.refreshPendingCount()));
  }

  linkIntervention(
    id: number,
    body: {
      interventionTechniqueId?: number | null;
      interventionId?: number | null;
      clearTechnique?: boolean;
      clearBon?: boolean;
    }
  ): Observable<TodoItem> {
    return this.http.put<TodoItem>(`${this.base}/${id}/intervention`, body);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`).pipe(tap(() => this.refreshPendingCount()));
  }
}
