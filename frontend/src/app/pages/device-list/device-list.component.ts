import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Device } from '../../models/models';
import { DeviceService } from '../../services/device.service';
import { AuthService } from '../../services/auth.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';

/**
 * Liste des pièces détachées de l'atelier courant.
 * Permet la recherche, la consultation et la suppression des pièces.
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
    MatChipsModule,
    MatCardModule,
    MatTableModule,
    MatProgressSpinnerModule,
    ConfirmDialogComponent
  ],
  templateUrl: './device-list.component.html',
  styleUrl: './device-list.component.scss'
})
export class DeviceListComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly items = signal<Device[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly confirmOpen = signal(false);
  pendingDelete: Device | null = null;
  query = '';
  readonly displayedColumns = ['nom', 'reference', 'sfm', 'mas', 'marque', 'date', 'statut', 'actions'];

  constructor(private deviceService: DeviceService) {}

  /** URL absolue de la photo principale d'une pièce. */
  photoUrl(item: Device): string {
    return this.deviceService.resolvePhotoUrl(item.photoUrl);
  }

  /** Relance le chargement si l'API Render est encore endormie. */
  onPhotoError(event: Event): void {
    this.deviceService.retryPhotoOnError(event);
  }

  ngOnInit(): void {
    this.load();
  }

  /** Nombre total de pièces affichées. */
  get total(): number {
    return this.items().length;
  }

  /** Nombre de pièces marquées comme obsolètes. */
  get obsoleteCount(): number {
    return this.items().filter((d) => d.obsolete).length;
  }

  /** Charge les pièces selon le filtre de recherche courant. */
  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.deviceService.list(this.query).subscribe({
      next: (data) => {
        this.items.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les pièces détachées.');
        this.loading.set(false);
      }
    });
  }

  /** Demande confirmation avant suppression d'une pièce. */
  askDelete(item: Device): void {
    this.pendingDelete = item;
    this.confirmOpen.set(true);
  }

  /** Annule la suppression en cours. */
  cancelDelete(): void {
    this.pendingDelete = null;
    this.confirmOpen.set(false);
  }

  /** Supprime la pièce sélectionnée après confirmation. */
  confirmDelete(): void {
    if (!this.pendingDelete) {
      return;
    }
    const id = this.pendingDelete.id;
    this.confirmOpen.set(false);
    this.deviceService.delete(id).subscribe({
      next: () => {
        this.pendingDelete = null;
        this.load();
      },
      error: (err) => {
        this.error.set(err?.error?.message || 'Suppression impossible.');
        this.pendingDelete = null;
      }
    });
  }
}
