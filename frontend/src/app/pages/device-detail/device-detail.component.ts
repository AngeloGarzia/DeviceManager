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
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly confirmOpen = signal(false);

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.deviceService.get(id).subscribe({
      next: (data) => {
        this.item.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Pièce introuvable.');
        this.loading.set(false);
      }
    });
  }

  askDelete(): void {
    this.confirmOpen.set(true);
  }

  photoUrl(device: Device): string {
    return this.deviceService.resolvePhotoUrl(device.photoUrl);
  }

  galleryUrls(device: Device): string[] {
    if (device.photos && device.photos.length > 0) {
      return [...device.photos]
        .sort((a, b) => a.position - b.position)
        .map((p) => this.deviceService.resolvePhotoUrl(p.photoUrl))
        .filter((url) => !!url);
    }
    const primary = this.photoUrl(device);
    return primary ? [primary] : [];
  }

  confirmDelete(): void {
    const current = this.item();
    if (!current) {
      return;
    }
    this.confirmOpen.set(false);
    this.deviceService.delete(current.id).subscribe({
      next: () => this.router.navigate(['/devices']),
      error: (err) => this.error.set(err?.error?.message || 'Suppression impossible.')
    });
  }
}
