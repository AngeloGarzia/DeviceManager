import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { Device, TodoItem } from '../../models/models';
import { DeviceService } from '../../services/device.service';
import { TodoService } from '../../services/todo.service';
import { AuthService } from '../../services/auth.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { apiErrorMessage } from '../../shared/api-error';

/**
 * Liste des pièces détachées (tableau) + vignette de la pièce sélectionnée,
 * et raccourci vers la page Todo.
 */
@Component({
  selector: 'app-device-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatTableModule,
    ConfirmDialogComponent
  ],
  templateUrl: './device-list.component.html',
  styleUrl: './device-list.component.scss'
})
export class DeviceListComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly deviceService = inject(DeviceService);
  private readonly todoService = inject(TodoService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly items = signal<Device[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly confirmOpen = signal(false);
  readonly tileOpen = signal(false);
  readonly todos = signal<TodoItem[]>([]);
  readonly todosLoading = signal(false);
  pendingDelete: Device | null = null;
  query = '';

  readonly displayedColumns: string[] = ['nom', 'reference', 'stock', 'statut', 'sfm', 'mas'];

  readonly selected = computed(() => {
    const id = this.selectedId();
    if (id == null) {
      return null;
    }
    return this.items().find((d) => d.id === id) ?? null;
  });

  photoUrl(item: Device): string {
    return this.deviceService.resolvePhotoUrl(item.photoUrl);
  }

  onPhotoError(event: Event): void {
    this.deviceService.retryPhotoOnError(event);
  }

  toggleTile(): void {
    this.tileOpen.update((open) => !open);
  }

  select(item: Device): void {
    this.selectedId.set(item.id);
  }

  isSelected(item: Device): boolean {
    return this.selectedId() === item.id;
  }

  ngOnInit(): void {
    this.load();
    this.loadTodos();
    this.route.queryParamMap.subscribe((params) => {
      if (params.get('open') === 'pieces') {
        this.tileOpen.set(true);
        void this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { open: null },
          queryParamsHandling: 'merge',
          replaceUrl: true
        });
      }
    });
  }

  get total(): number {
    return this.items().length;
  }

  get obsoleteCount(): number {
    return this.items().filter((d) => d.obsolete).length;
  }

  get zeroStockCount(): number {
    return this.items().filter((d) => (d.stock ?? 0) <= 0).length;
  }

  get todoCount(): number {
    return this.todos().length;
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.deviceService.list(this.query).subscribe({
      next: (data) => {
        this.items.set(data);
        this.syncSelection(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les pièces détachées.');
        this.loading.set(false);
      }
    });
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

  askDelete(item: Device): void {
    this.pendingDelete = item;
    this.confirmOpen.set(true);
  }

  cancelDelete(): void {
    this.pendingDelete = null;
    this.confirmOpen.set(false);
  }

  confirmDelete(): void {
    if (!this.pendingDelete) {
      return;
    }
    const id = this.pendingDelete.id;
    this.confirmOpen.set(false);
    this.deviceService.delete(id).subscribe({
      next: () => {
        this.pendingDelete = null;
        if (this.selectedId() === id) {
          this.selectedId.set(null);
        }
        this.load();
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Suppression impossible.'));
        this.pendingDelete = null;
      }
    });
  }

  private syncSelection(data: Device[]): void {
    if (data.length === 0) {
      this.selectedId.set(null);
      return;
    }
    const current = this.selectedId();
    if (current != null && data.some((d) => d.id === current)) {
      return;
    }
    this.selectedId.set(data[0].id);
  }
}
