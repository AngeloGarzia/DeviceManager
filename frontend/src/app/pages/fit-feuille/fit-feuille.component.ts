import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Fit, FitLigne, Mas } from '../../models/models';
import { FitService } from '../../services/fit.service';
import { MasService } from '../../services/mas.service';
import { TimelineService } from '../../services/timeline.service';
import { apiErrorMessage } from '../../shared/api-error';

/** Nombre de lignes vides affichées sous l'historique (aspect formulaire papier). */
const EMPTY_ROW_COUNT = 8;

/**
 * Feuille FIT visuelle (modèle n° 34) : en-tête machine + tableau chronologique.
 */
@Component({
  selector: 'app-fit-feuille',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './fit-feuille.component.html',
  styleUrl: './fit-feuille.component.scss'
})
export class FitFeuilleComponent implements OnInit {
  private readonly fitService = inject(FitService);
  private readonly masService = inject(MasService);
  private readonly timelineService = inject(TimelineService);
  private readonly route = inject(ActivatedRoute);

  readonly masses = signal<Mas[]>([]);
  readonly masIdsWithSuivi = signal<Set<number>>(new Set());
  readonly fit = signal<Fit | null>(null);
  readonly loadingMas = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly masCtrl = new FormControl<number | null>(null);

  /** Lignes triées chronologiquement (plus anciennes en haut). */
  readonly chronologicalLignes = computed(() => {
    const lignes = this.fit()?.lignes ?? [];
    return [...lignes].sort((a, b) => {
      const da = a.dateOperation || '';
      const db = b.dateOperation || '';
      if (da !== db) {
        return da.localeCompare(db);
      }
      return (a.id ?? 0) - (b.id ?? 0);
    });
  });

  readonly emptyRows = computed(() => {
    const filled = this.chronologicalLignes().length;
    const need = Math.max(EMPTY_ROW_COUNT - filled, filled === 0 ? EMPTY_ROW_COUNT : 2);
    return Array.from({ length: need }, (_, i) => i);
  });

  ngOnInit(): void {
    this.loadingMas.set(true);
    forkJoin({
      list: this.masService.list(),
      withSuivi: this.timelineService.masIdsWithSuivi().pipe(catchError(() => of([] as number[])))
    }).subscribe({
      next: ({ list, withSuivi }) => {
        const suiviSet = new Set((withSuivi ?? []).filter((id) => id != null));
        this.masIdsWithSuivi.set(suiviSet);
        this.masses.set(this.sortMasses(list, suiviSet));
        this.loadingMas.set(false);

        const masFromQuery = Number(this.route.snapshot.queryParamMap.get('masId'));
        const fitFromQuery = Number(this.route.snapshot.queryParamMap.get('fitId'));
        if (Number.isFinite(fitFromQuery) && fitFromQuery > 0) {
          this.loadByFitId(fitFromQuery);
        } else if (Number.isFinite(masFromQuery) && list.some((m) => m.id === masFromQuery)) {
          this.masCtrl.setValue(masFromQuery);
          this.loadByMasId(masFromQuery);
        }
      },
      error: (err) => {
        this.loadingMas.set(false);
        this.error.set(apiErrorMessage(err, 'Impossible de charger les MAS.'));
      }
    });

    this.masCtrl.valueChanges.subscribe((id) => {
      if (id == null) {
        this.fit.set(null);
        this.error.set(null);
        return;
      }
      this.loadByMasId(id);
    });
  }

  hasSuivi(masId: number): boolean {
    return this.masIdsWithSuivi().has(masId);
  }

  masMarquePart(mas: Mas): string {
    return (mas.marqueLabel || mas.marque || '').trim();
  }

  formatDate(value?: string | null): string {
    if (!value) {
      return '';
    }
    const d = value.length >= 10 ? value.slice(0, 10) : value;
    const [y, m, day] = d.split('-');
    if (y && m && day) {
      return `${day}/${m}/${y}`;
    }
    return value;
  }

  formatMoney(value?: number | null): string {
    if (value == null || Number.isNaN(Number(value))) {
      return '';
    }
    return Number(value).toLocaleString('fr-FR', {
      minimumFractionDigits: 0,
      maximumFractionDigits: 4
    });
  }

  formatTaux(value?: number | null): string {
    if (value == null || Number.isNaN(Number(value))) {
      return '';
    }
    return `${Number(value).toLocaleString('fr-FR', { maximumFractionDigits: 2 })} %`;
  }

  print(): void {
    window.print();
  }

  private loadByMasId(masId: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.fitService.listByMas(masId).subscribe({
      next: (list) => {
        this.fit.set(list[0] ?? null);
        this.loading.set(false);
        if (!list.length) {
          this.error.set('Aucune fiche FIT pour cette MAS. Créez-en une depuis Fiches FIT.');
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.fit.set(null);
        this.error.set(apiErrorMessage(err, 'Impossible de charger la fiche FIT.'));
      }
    });
  }

  private loadByFitId(fitId: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.fitService.get(fitId).subscribe({
      next: (fit) => {
        this.fit.set(fit);
        this.loading.set(false);
        if (fit.masId != null) {
          this.masCtrl.setValue(fit.masId, { emitEvent: false });
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Impossible de charger la fiche FIT.'));
      }
    });
  }

  private sortMasses(list: Mas[], suiviSet: Set<number>): Mas[] {
    const byNumero = (a: Mas, b: Mas) =>
      a.numero.localeCompare(b.numero, 'fr', { numeric: true });
    return [
      ...list.filter((m) => suiviSet.has(m.id)).sort(byNumero),
      ...list.filter((m) => !suiviSet.has(m.id)).sort(byNumero)
    ];
  }

  trackLigne(_index: number, ligne: FitLigne): number {
    return ligne.id;
  }
}
