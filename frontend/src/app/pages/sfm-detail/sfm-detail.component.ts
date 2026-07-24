import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Sfm } from '../../models/models';
import { SfmService } from '../../services/sfm.service';
import { AuthService } from '../../services/auth.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';

@Component({
  selector: 'app-sfm-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MatProgressSpinnerModule, ConfirmDialogComponent],
  templateUrl: './sfm-detail.component.html',
  styleUrl: './sfm-detail.component.scss'
})
export class SfmDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly sfmService = inject(SfmService);
  readonly auth = inject(AuthService);
  readonly item = signal<Sfm | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly confirmOpen = signal(false);

  ngOnInit(): void {
    this.sfmService.get(Number(this.route.snapshot.paramMap.get('id'))).subscribe({
      next: (data) => { this.item.set(data); this.loading.set(false); },
      error: () => { this.error.set('SFM introuvable.'); this.loading.set(false); }
    });
  }
  askDelete(): void { this.confirmOpen.set(true); }
  confirmDelete(): void {
    const current = this.item();
    if (!current) return;
    this.confirmOpen.set(false);
    this.sfmService.delete(current.id).subscribe({
      next: () => this.router.navigate(['/sfm']),
      error: (err) => this.error.set(err?.error?.message || 'Suppression impossible.')
    });
  }
}
