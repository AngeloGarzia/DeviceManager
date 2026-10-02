import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { TodoItem, TodoWeekCalendar, TodoWeekCalendarDay } from '../../models/models';
import { TodoService } from '../../services/todo.service';

/**
 * Accueil : raccourci Signaler + tuile Todo (calendrier hebdo).
 * L'inventaire des pièces détachées est sur /devices/pieces.
 */
@Component({
  selector: 'app-device-list',
  standalone: true,
  imports: [CommonModule, RouterLink, MatIconModule, MatCardModule],
  templateUrl: './device-list.component.html',
  styleUrl: './device-list.component.scss'
})
export class DeviceListComponent implements OnInit {
  private readonly todoService = inject(TodoService);

  readonly todos = signal<TodoItem[]>([]);
  readonly todosLoading = signal(false);
  readonly weekCalendar = signal<TodoWeekCalendar | null>(null);
  readonly weekCalendarLoading = signal(false);

  readonly todayIso = computed(() => {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  });

  readonly weekDays = computed(() => this.weekCalendar()?.days ?? []);

  get todoCount(): number {
    return this.todos().length;
  }

  ngOnInit(): void {
    this.loadTodos();
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

  dayQueryParams(day: TodoWeekCalendarDay): Record<string, string> {
    if (day.overdueCount > 0) {
      return { filter: 'OVERDUE', day: day.date };
    }
    return { day: day.date };
  }
}
