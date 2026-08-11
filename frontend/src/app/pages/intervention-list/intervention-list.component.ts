import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Intervention } from '../../models/models';
import { DeviceService } from '../../services/device.service';
import { InterventionService } from '../../services/intervention.service';
import { apiErrorMessage } from '../../shared/api-error';

/**
 * Archive des bons d'intervention (consommations de pièces détachées).
 */
@Component({
  selector: 'app-intervention-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './intervention-list.component.html',
  styleUrl: './intervention-list.component.scss'
})
export class InterventionListComponent implements OnInit {
  private readonly interventionService = inject(InterventionService);
  private readonly deviceService = inject(DeviceService);

  readonly items = signal<Intervention[]>([]);
  readonly expandedId = signal<number | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.interventionService.list().subscribe({
      next: (data) => {
        this.items.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Impossible de charger les bons d’intervention.'));
        this.loading.set(false);
      }
    });
  }

  toggle(id: number): void {
    this.expandedId.update((current) => (current === id ? null : id));
  }

  photoUrl(url?: string | null): string {
    return this.deviceService.resolvePhotoUrl(url);
  }
}
