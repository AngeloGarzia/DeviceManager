import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { TodoItem, TodoWeekCalendar, TodoWeekCalendarDay } from '../../models/models';
import { TodoService } from '../../services/todo.service';
import { TodoQuickModalComponent } from '../../shared/todo-quick-modal.component';
import { TodoCreateModalComponent } from '../../shared/todo-create-modal.component';

/**
 * Accueil : raccourci Signaler + calendrier hebdo + liste des tâches simples.
 * L'inventaire des pièces détachées est sur /devices/pieces.
 */
@Component({
  selector: 'app-device-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatIconModule,
    MatCardModule,
    MatButtonModule,
    TodoQuickModalComponent,
    TodoCreateModalComponent
  ],
  templateUrl: './device-list.component.html',
  styleUrl: './device-list.component.scss'
})
export class DeviceListComponent implements OnInit {
  private readonly todoService = inject(TodoService);

  readonly todos = signal<TodoItem[]>([]);
  readonly todosLoading = signal(false);
  /** Tâches / nettoyages clôturés récents (rappel Accueil). */
  readonly recentDone = signal<TodoItem[]>([]);
  readonly recentDoneLoading = signal(false);
  readonly weekCalendar = signal<TodoWeekCalendar | null>(null);
  readonly weekCalendarLoading = signal(false);
  /** false = date croissante (plus ancienne d’abord), true = décroissante. */
  readonly dateSortDesc = signal(false);
  readonly selectedTodo = signal<TodoItem | null>(null);
  readonly showCreateModal = signal(false);

  private static readonly RECENT_DONE_LIMIT = 12;

  readonly todayIso = computed(() => {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  });

  readonly weekDays = computed(() => this.weekCalendar()?.days ?? []);

  /**
   * Tâches simples (ponctuelles, hors nettoyage multi-MAS) triées : urgence puis date.
   */
  readonly simpleTodosSorted = computed(() => {
    const desc = this.dateSortDesc();
    return [...this.todos()]
      .filter((t) => !t.recurrenceId && t.typeTache !== 'NETTOYAGE_MAS' && t.type !== 'NETTOYAGE_MAS')
      .sort((a, b) => {
        const sev = this.severityRank(a.severite || a.severity) - this.severityRank(b.severite || b.severity);
        if (sev !== 0) {
          return sev;
        }
        const da = this.taskDateMs(a);
        const db = this.taskDateMs(b);
        return desc ? db - da : da - db;
      });
  });

  get todoCount(): number {
    return this.todos().length;
  }

  /**
   * Petites tuiles : tâches simples + nettoyages DONE, plus récents d’abord.
   */
  readonly recentDoneTiles = computed(() => {
    return [...this.recentDone()]
      .filter((t) => t.statut === 'DONE' && !t.recurrenceId)
      .sort((a, b) => this.doneDateMs(b) - this.doneDateMs(a))
      .slice(0, DeviceListComponent.RECENT_DONE_LIMIT);
  });

  ngOnInit(): void {
    this.loadTodos();
    this.loadRecentDone();
    this.loadWeekCalendar();
  }

  loadTodos(): void {
    this.todosLoading.set(true);
    this.todoService.list().subscribe({
      next: (data) => {
        this.todos.set(data.items ?? []);
        this.todosLoading.set(false);
      },
      error: () => {
        this.todos.set([]);
        this.todosLoading.set(false);
      }
    });
  }

  loadRecentDone(): void {
    this.recentDoneLoading.set(true);
    this.todoService.list(true).subscribe({
      next: (data) => {
        this.recentDone.set(
          (data.items ?? []).filter((t) => t.statut === 'DONE' && !t.recurrenceId)
        );
        this.recentDoneLoading.set(false);
      },
      error: () => {
        this.recentDone.set([]);
        this.recentDoneLoading.set(false);
      }
    });
  }

  loadWeekCalendar(): void {
    this.weekCalendarLoading.set(true);
    this.todoService.weekCalendar().subscribe({
      next: (data) => {
        this.weekCalendar.set(data);
        this.weekCalendarLoading.set(false);
      },
      error: () => {
        this.weekCalendar.set(null);
        this.weekCalendarLoading.set(false);
      }
    });
  }

  openTodoModal(item: TodoItem): void {
    this.selectedTodo.set(item);
  }

  closeTodoModal(): void {
    this.selectedTodo.set(null);
  }

  openCreateModal(): void {
    this.showCreateModal.set(true);
  }

  closeCreateModal(): void {
    this.showCreateModal.set(false);
  }

  onTodoCreated(item: TodoItem): void {
    this.todos.update((list) => [item, ...list.filter((t) => t.id !== item.id)]);
    this.showCreateModal.set(false);
  }

  onTodoUpdated(updated: TodoItem): void {
    this.todos.update((list) =>
      list.map((t) => (t.id === updated.id ? { ...t, ...updated } : t))
    );
    if (updated.statut === 'DONE' || updated.statut === 'CANCELLED') {
      this.todos.update((list) => list.filter((t) => t.id !== updated.id));
      this.selectedTodo.set(null);
      if (updated.statut === 'DONE') {
        this.recentDone.update((list) => {
          const without = list.filter((t) => t.id !== updated.id);
          return [updated, ...without];
        });
      }
    } else {
      this.selectedTodo.set(updated);
    }
  }

  isNettoyageDone(item: TodoItem): boolean {
    return item.typeTache === 'NETTOYAGE_MAS' || item.type === 'NETTOYAGE_MAS';
  }

  doneLink(item: TodoItem): string {
    return this.isNettoyageDone(item) ? '/mas/nettoyage' : '/todos';
  }

  doneQueryParams(item: TodoItem): Record<string, string> {
    return this.isNettoyageDone(item) ? {} : { filter: 'DONE' };
  }

  doneMeta(item: TodoItem): string {
    if (this.isNettoyageDone(item)) {
      const n = item.nettoyageCocheCount;
      const total = item.nettoyageTotalCount;
      if (n != null && total != null && total > 0) {
        return `${n}/${total} MAS`;
      }
      return 'Nettoyage';
    }
    if (item.masNumero) {
      return `MAS ${item.masNumero}`;
    }
    return item.signataireClotureNom || item.responsableCloture || 'Terminée';
  }

  dayQueryParams(day: TodoWeekCalendarDay): Record<string, string> {
    if (day.overdueCount > 0) {
      return { filter: 'OVERDUE', day: day.date };
    }
    return { day: day.date };
  }

  toggleDateSort(): void {
    this.dateSortDesc.update((v) => !v);
  }

  severityLabel(severity?: string | null): string {
    switch (severity) {
      case 'HIGH':
        return 'Urgent';
      case 'MEDIUM':
        return 'Normal';
      case 'LOW':
        return 'Faible';
      default:
        return 'Normal';
    }
  }

  formatDate(value?: string | null): string {
    if (!value) {
      return '—';
    }
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) {
      return value;
    }
    return d.toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric'
    });
  }

  taskDisplayDate(item: TodoItem): string | null {
    return item.dueAt || item.createdAt || item.dateCreation || null;
  }

  private severityRank(severity?: string | null): number {
    switch ((severity || 'MEDIUM').toUpperCase()) {
      case 'HIGH':
        return 0;
      case 'MEDIUM':
        return 1;
      case 'LOW':
        return 2;
      default:
        return 1;
    }
  }

  private taskDateMs(item: TodoItem): number {
    const raw = this.taskDisplayDate(item);
    if (!raw) {
      return 0;
    }
    const t = new Date(raw).getTime();
    return Number.isNaN(t) ? 0 : t;
  }

  private doneDateMs(item: TodoItem): number {
    const raw = item.completedAt || item.dateCloture || item.updatedAt || null;
    if (!raw) {
      return 0;
    }
    const t = new Date(raw).getTime();
    return Number.isNaN(t) ? 0 : t;
  }
}
