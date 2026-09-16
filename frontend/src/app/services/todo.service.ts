import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  TodoItem,
  TodoList,
  TodoModele,
  TodoModeleForm,
  TodoRecurrence,
  TodoRecurrenceForm,
  TodoTacheForm,
  TodoWeekCalendar
} from '../models/models';

@Injectable({ providedIn: 'root' })
export class TodoService {
  private readonly base = `${environment.apiUrl}/api/todos`;

  /** Nombre de tâches actives (ouvertes + en cours) pour le badge nav. */
  readonly pendingCount = signal(0);
  /** Occurrences échues non clôturées — warning Todo. */
  readonly overdueCount = signal(0);

  constructor(private http: HttpClient) {}

  list(all = false): Observable<TodoList> {
    let params = new HttpParams();
    if (all) {
      params = params.set('all', 'true');
    }
    return this.http.get<TodoList>(this.base, { params }).pipe(
      tap((data) => {
        const overdue = data.overdueCount ?? (data.items ?? []).filter((t) => t.overdue).length;
        this.overdueCount.set(overdue);
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
    this.list(false).subscribe({ error: () => {
      this.pendingCount.set(0);
      this.overdueCount.set(0);
    } });
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

  listRecurrences(): Observable<TodoRecurrence[]> {
    return this.http.get<TodoRecurrence[]>(`${this.base}/recurrences`);
  }

  listModeles(): Observable<TodoModele[]> {
    return this.http.get<TodoModele[]>(`${this.base}/modeles`);
  }

  createModele(payload: TodoModeleForm): Observable<TodoModele> {
    return this.http.post<TodoModele>(`${this.base}/modeles`, this.toModeleBody(payload));
  }

  updateModele(id: number, payload: TodoModeleForm): Observable<TodoModele> {
    return this.http.put<TodoModele>(`${this.base}/modeles/${id}`, this.toModeleBody(payload));
  }

  deleteModele(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/modeles/${id}`);
  }

  /** Crée une tâche ponctuelle à partir d'un modèle. */
  utiliserModele(id: number): Observable<TodoItem> {
    return this.http
      .post<TodoItem>(`${this.base}/modeles/${id}/utiliser`, {})
      .pipe(tap(() => this.refreshPendingCount()));
  }

  private toModeleBody(payload: TodoModeleForm) {
    return {
      titre: payload.titre.trim(),
      description: payload.description?.trim() || null,
      severite: payload.severite || 'MEDIUM',
      masId: payload.masId ?? null,
      position: payload.position ?? 0
    };
  }

  weekCalendar(): Observable<TodoWeekCalendar> {
    return this.http.get<TodoWeekCalendar>(`${this.base}/week-calendar`);
  }

  createRecurrence(payload: TodoRecurrenceForm): Observable<TodoRecurrence> {
    return this.http.post<TodoRecurrence>(`${this.base}/recurrences`, this.toRecurrenceBody(payload)).pipe(
      tap(() => this.refreshPendingCount())
    );
  }

  updateRecurrence(id: number, payload: TodoRecurrenceForm): Observable<TodoRecurrence> {
    return this.http.put<TodoRecurrence>(`${this.base}/recurrences/${id}`, this.toRecurrenceBody(payload)).pipe(
      tap(() => this.refreshPendingCount())
    );
  }

  setRecurrenceActive(id: number, active: boolean): Observable<TodoRecurrence> {
    return this.http
      .put<TodoRecurrence>(`${this.base}/recurrences/${id}/active`, { active })
      .pipe(tap(() => this.refreshPendingCount()));
  }

  deleteRecurrence(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/recurrences/${id}`);
  }

  private toRecurrenceBody(payload: TodoRecurrenceForm) {
    return {
      titre: payload.titre.trim(),
      description: payload.description?.trim() || null,
      severite: payload.severite || 'MEDIUM',
      masId: payload.masId ?? null,
      frequence: payload.frequence,
      intervalDays: payload.intervalDays ?? null,
      jourSemaine: payload.jourSemaine ?? null,
      jourMois: payload.jourMois ?? null,
      heureDue: payload.heureDue || '08:00:00',
      dateDebut: payload.dateDebut,
      dateFin: payload.dateFin || null,
      active: payload.active ?? true
    };
  }
}
