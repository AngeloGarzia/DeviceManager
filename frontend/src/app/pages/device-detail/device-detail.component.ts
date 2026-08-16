import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Device } from '../../models/models';
import { DeviceService } from '../../services/device.service';
import { AuthService } from '../../services/auth.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { apiErrorMessage } from '../../shared/api-error';

/**
 * Fiche détaillée d'une pièce détachée.
 * Affiche les informations, la galerie photos et les actions de modification ou suppression.
 */
@Component({
  selector: 'app-device-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatProgressSpinnerModule,
    ConfirmDialogComponent
  ],
  templateUrl: './device-detail.component.html',
  styleUrl: './device-detail.component.scss'
})
export class DeviceDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly deviceService = inject(DeviceService);
  readonly auth = inject(AuthService);

  readonly item = signal<Device | null>(null);
  readonly prixHistory = signal<
    {
      id: number;
      unitPriceHt: number;
      currency: string;
      commandeId?: number | null;
      observedAt: string;
      confirmedBy: string;
      devisDesignation?: string | null;
      devisReference?: string | null;
    }[]
  >([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly confirmOpen = signal(false);

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.deviceService.get(id).subscribe({
      next: (data) => {
        this.item.set(data);
        this.loading.set(false);
        this.deviceService.prixHistory(id).subscribe({
          next: (hist) => this.prixHistory.set(hist),
          error: () => this.prixHistory.set([])
        });
      },
      error: () => {
        this.error.set('Pièce introuvable.');
        this.loading.set(false);
      }
    });
  }

  /** Ouvre la boîte de dialogue de confirmation de suppression. */
  askDelete(): void {
    this.confirmOpen.set(true);
  }

  /** URL absolue de la photo principale d'une pièce. */
  photoUrl(device: Device): string {
    return this.deviceService.resolvePhotoUrl(device.photoUrl);
  }

  /** Relance le chargement si l'API Render est encore endormie. */
  onPhotoError(event: Event): void {
    this.deviceService.retryPhotoOnError(event);
  }

  /** Liste des URLs de la galerie photos, triées par position. */
  galleryUrls(device: Device): string[] {
    if (device.photos && device.photos.length > 0) {
      return [...device.photos]
        .sort((a, b) => a.position - b.position)
        .map((p) => this.deviceService.resolvePhotoUrl(p.photoUrl))
        .filter((url) => !!url);
    }
    const single = this.photoUrl(device);
    return single ? [single] : [];
  }

  resolveDocumentUrl(fileUrl: string): string {
    return this.deviceService.resolveDocumentUrl(fileUrl);
  }

  isDocumentImage(doc: { originalName?: string | null; contentType?: string | null }): boolean {
    const ct = (doc.contentType || '').toLowerCase();
    const name = (doc.originalName || '').toLowerCase();
    return ct.startsWith('image/') || /\.(png|jpe?g|webp|gif)$/.test(name);
  }

  documentTypeLabel(type: string): string {
    switch (type) {
      case 'MANUAL':
        return 'Manuel';
      case 'DATASHEET':
        return 'Datasheet';
      case 'NOTICE':
        return 'Notice';
      default:
        return type;
    }
  }

  /** Supprime la pièce et retourne à la liste. */
  confirmDelete(): void {
    const current = this.item();
    if (!current) {
      return;
    }
    this.confirmOpen.set(false);
    this.deviceService.delete(current.id).subscribe({
      next: () => this.router.navigate(['/devices']),
      error: (err) => this.error.set(apiErrorMessage(err, 'Suppression impossible.'))
    });
  }
}
