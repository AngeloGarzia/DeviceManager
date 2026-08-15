import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Fit } from '../../models/models';
import { FitService } from '../../services/fit.service';
import { apiErrorMessage } from '../../shared/api-error';

/**
 * Liste des fiches FIT (inventaire / intervention technique) de l'atelier.
 */
@Component({
  selector: 'app-fit-list',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './fit-list.component.html',
  styleUrl: './fit-list.component.scss'
})
export class FitListComponent implements OnInit {
  private readonly fitService = inject(FitService);

  readonly items = signal<Fit[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.fitService.list().subscribe({
      next: (list) => {
        this.items.set(list);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Impossible de charger les FIT.'));
      }
    });
  }
}
