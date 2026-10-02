import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { Device } from '../../models/models';
import { DeviceService } from '../../services/device.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { apiErrorMessage } from '../../shared/api-error';

/**
 * Page dédiée à l'inventaire des pièces détachées.
 */
@Component({
  selector: 'app-device-pieces',
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
    MatCheckboxModule,
    ConfirmDialogComponent
  ],
  templateUrl: './device-pieces.component.html',
  styleUrl: '../device-list/device-list.component.scss'
})
export class DevicePiecesComponent implements OnInit {
  private readonly deviceService = inject(DeviceService);

  readonly allItems = signal<Device[]>([]);
  readonly showObsolete = signal(false);
  readonly selectedId = signal<number | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly confirmOpen = signal(false);
  pendingDelete: Device | null = null;
  query = '';

  readonly displayedColumns: string[] = ['photo', 'nom', 'reference', 'stock', 'statut', 'sfm', 'mas', 'actions'];

  readonly items = computed(() => {
    const all = this.allItems();
    if (this.showObsolete()) {
      return all;
    }
    return all.filter((d) => !d.obsolete);
  });

  get total(): number {
    return this.items().length;
  }

  get obsoleteCount(): number {
    return this.allItems().filter((d) => d.obsolete).length;
  }

  get zeroStockCount(): number {
    return this.allItems().filter((d) => !d.obsolete && (d.stock ?? 0) <= 0).length;
  }

  ngOnInit(): void {
    this.load();
  }

  photoUrl(item: Device): string {
    return this.deviceService.resolvePhotoUrl(item.photoUrl);
  }

  onPhotoError(event: Event): void {
    this.deviceService.retryPhotoOnError(event);
  }

  select(item: Device): void {
    this.selectedId.set(item.id);
  }

  isSelected(item: Device): boolean {
    return this.selectedId() === item.id;
  }

  onShowObsoleteChange(checked: boolean): void {
    this.showObsolete.set(checked);
    const id = this.selectedId();
    if (id != null && !this.items().some((d) => d.id === id)) {
      this.selectedId.set(null);
    }
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.deviceService.list(this.query).subscribe({
      next: (data) => {
        this.allItems.set(data);
        const id = this.selectedId();
        if (id != null && !this.items().some((d) => d.id === id)) {
          this.selectedId.set(null);
        }
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les pièces détachées.');
        this.loading.set(false);
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
}
