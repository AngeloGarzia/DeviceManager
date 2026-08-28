import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ArretMaintenance,
  ArretMaintenanceAlert,
  ArretMaintenanceForm,
  ArretMaintenanceRepriseForm
} from '../models/models';

@Injectable({ providedIn: 'root' })
export class ArretMaintenanceService {
  private readonly base = `${environment.apiUrl}/api/arrets-maintenance`;

  readonly alert = signal<ArretMaintenanceAlert>({ count: 0, items: [] });

  readonly alertLabel = computed(() => {
    const summary = this.alert();
    if (summary.count <= 0) {
      return '';
    }
    if (summary.count === 1) {
      const item = summary.items[0];
      return `MAS ${item.masNumero} — arrêt maintenance depuis ${this.formatDateTime(item.dateHeureArret)}`;
    }
    return `${summary.count} MAS arrêtées pour maintenance`;
  });

  readonly alertTooltip = computed(() => {
    const summary = this.alert();
    if (summary.count <= 0) {
      return '';
    }
    return summary.items
      .map((item) => `${item.masNumero} — depuis ${this.formatDateTime(item.dateHeureArret)}`)
      .join('\n');
  });

  constructor(private http: HttpClient) {}

  listActive(): Observable<ArretMaintenance[]> {
    return this.http.get<ArretMaintenance[]>(`${this.base}/active`);
  }

  history(): Observable<ArretMaintenance[]> {
    return this.http.get<ArretMaintenance[]>(this.base);
  }

  declareArret(payload: ArretMaintenanceForm): Observable<ArretMaintenance> {
    return this.http.post<ArretMaintenance>(this.base, payload).pipe(
      tap(() => this.refreshAlert())
    );
  }

  declareReprise(id: number, payload: ArretMaintenanceRepriseForm): Observable<ArretMaintenance> {
    return this.http.put<ArretMaintenance>(`${this.base}/${id}/reprise`, payload).pipe(
      tap(() => this.refreshAlert())
    );
  }

  refreshAlert(): void {
    this.http.get<ArretMaintenanceAlert>(`${this.base}/alert`).subscribe({
      next: (res) =>
        this.alert.set({
          count: Number(res?.count) || 0,
          items: res?.items ?? []
        }),
      error: () => this.alert.set({ count: 0, items: [] })
    });
  }

  formatDateTime(value?: string | null): string {
    if (!value) {
      return '—';
    }
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) {
      return value;
    }
    return d.toLocaleString('fr-FR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }
}
