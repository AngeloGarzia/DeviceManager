import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';
import { Sfm, VisiteQuadri, VisiteQuadriObligation } from '../../models/models';
import { VisiteQuadriService } from '../../services/visite-quadri.service';
import { SfmService } from '../../services/sfm.service';
import { apiErrorMessage } from '../../shared/api-error';

/**
 * Suivi des visites quadritrimestrielles : chaque SFM × marque tous les 4 mois.
 */
@Component({
  selector: 'app-visite-quadri',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatCardModule
  ],
  templateUrl: './visite-quadri.component.html',
  styleUrl: './visite-quadri.component.scss'
})
export class VisiteQuadriComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly visiteService = inject(VisiteQuadriService);
  private readonly sfmService = inject(SfmService);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);
  readonly obligations = signal<VisiteQuadriObligation[]>([]);
  readonly history = signal<VisiteQuadri[]>([]);
  readonly sfms = signal<Sfm[]>([]);
  readonly modalOpen = signal(false);

  readonly form = this.fb.group({
    sfmId: [null as number | null, Validators.required],
    marqueId: [null as number | null, Validators.required],
    dateVisite: [this.todayIso(), Validators.required],
    notes: ['']
  });

  ngOnInit(): void {
    this.reload();
    this.sfmService.list().subscribe({
      next: (list) => this.sfms.set(list ?? []),
      error: () => this.sfms.set([])
    });
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.visiteService.status().subscribe({
      next: (list) => {
        this.obligations.set(list ?? []);
        this.loading.set(false);
        this.visiteService.refreshWarningCount();
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Impossible de charger le statut des visites.'));
      }
    });
    this.visiteService.history().subscribe({
      next: (list) => this.history.set((list ?? []).slice(0, 20)),
      error: () => this.history.set([])
    });
  }

  marquesForSelectedSfm(): { id: number; label: string }[] {
    const sfmId = this.form.controls.sfmId.value;
    const sfm = this.sfms().find((s) => s.id === sfmId);
    return (sfm?.marques ?? []).map((m) => ({ id: m.id, label: m.label }));
  }

  onSfmChange(): void {
    this.form.patchValue({ marqueId: null });
  }

  openCreateModal(prefill?: VisiteQuadriObligation): void {
    this.success.set(null);
    this.error.set(null);
    this.form.reset({
      sfmId: prefill?.sfmId ?? null,
      marqueId: prefill?.marqueId ?? null,
      dateVisite: this.todayIso(),
      notes: ''
    });
    this.modalOpen.set(true);
  }

  closeModal(): void {
    this.modalOpen.set(false);
  }

  submitVisit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.visiteService
      .create({
        sfmId: Number(raw.sfmId),
        marqueId: Number(raw.marqueId),
        dateVisite: String(raw.dateVisite),
        notes: raw.notes?.trim() || null
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.modalOpen.set(false);
          this.success.set('Visite enregistrée.');
          this.reload();
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(apiErrorMessage(err, 'Enregistrement impossible.'));
        }
      });
  }

  levelLabel(level: string): string {
    if (level === 'OVERDUE') {
      return 'En retard';
    }
    if (level === 'WARN') {
      return '≤ 7 jours';
    }
    return 'À jour';
  }

  private todayIso(): string {
    return new Date().toISOString().slice(0, 10);
  }
}
