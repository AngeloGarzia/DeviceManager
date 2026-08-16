import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin } from 'rxjs';
import { Mas, TimelineColumn, TimelineEvent, TimelineEventType } from '../../models/models';
import { MasService } from '../../services/mas.service';
import { TimelineService } from '../../services/timeline.service';
import { apiErrorMessage } from '../../shared/api-error';

interface ColumnDef {
  id: TimelineColumn;
  label: string;
  icon: string;
}

/**
 * Suivi MAS : timeline multi-colonnes (bons, interventions techniques, FIT).
 */
@Component({
  selector: 'app-mas-suivi',
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
  templateUrl: './mas-suivi.component.html',
  styleUrl: './mas-suivi.component.scss'
})
export class MasSuiviComponent implements OnInit {
  private readonly masService = inject(MasService);
  private readonly timelineService = inject(TimelineService);
  private readonly route = inject(ActivatedRoute);

  readonly columns: ColumnDef[] = [
    { id: 'BONS', label: "Bons d'intervention", icon: 'receipt_long' },
    { id: 'INTERVENTIONS', label: 'Interventions techniques', icon: 'engineering' },
    { id: 'FIT', label: 'FIT', icon: 'description' }
  ];

  readonly masses = signal<Mas[]>([]);
  /** MAS ayant déjà des données de suivi (bons / interventions / FIT). */
  readonly masIdsWithSuivi = signal<Set<number>>(new Set());
  readonly items = signal<TimelineEvent[]>([]);
  readonly selectedMas = signal<Mas | null>(null);
  readonly activeColumns = signal<Set<TimelineColumn>>(
    new Set(this.columns.map((c) => c.id))
  );
  readonly expandedId = signal<string | null>(null);
  readonly loadingMas = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly masCtrl = new FormControl<number | null>(null);

  readonly visibleColumns = computed(() =>
    this.columns.filter((c) => this.activeColumns().has(c.id))
  );

  readonly visibleItems = computed(() => {
    const active = this.activeColumns();
    return this.items().filter((e) => active.has(this.columnOf(e)));
  });

  readonly gridTemplate = computed(() => {
    const n = Math.max(1, this.visibleColumns().length);
    return `repeat(${n}, minmax(12rem, 1fr))`;
  });

  ngOnInit(): void {
    this.loadingMas.set(true);
    forkJoin({
      list: this.masService.list(),
      withSuivi: this.timelineService.masIdsWithSuivi()
    }).subscribe({
      next: ({ list, withSuivi }) => {
        const suiviSet = new Set((withSuivi ?? []).filter((id) => id != null));
        this.masIdsWithSuivi.set(suiviSet);
        this.masses.set(this.sortMasses(list, suiviSet));
        this.loadingMas.set(false);
        const raw = this.route.snapshot.queryParamMap.get('masId');
        const fromQuery = raw ? Number(raw) : NaN;
        if (Number.isFinite(fromQuery) && list.some((m) => m.id === fromQuery)) {
          this.masCtrl.setValue(fromQuery);
          this.onMasSelected(fromQuery);
        }
      },
      error: (err) => {
        this.loadingMas.set(false);
        this.error.set(apiErrorMessage(err, 'Impossible de charger les MAS.'));
      }
    });

    this.masCtrl.valueChanges.subscribe((id) => this.onMasSelected(id));
  }

  hasSuivi(masId: number): boolean {
    return this.masIdsWithSuivi().has(masId);
  }

  private sortMasses(list: Mas[], suiviSet: Set<number>): Mas[] {
    const byNumero = (a: Mas, b: Mas) =>
      a.numero.localeCompare(b.numero, 'fr', { numeric: true });
    const withData = list.filter((m) => suiviSet.has(m.id)).sort(byNumero);
    const without = list.filter((m) => !suiviSet.has(m.id)).sort(byNumero);
    return [...withData, ...without];
  }

  onMasSelected(masId: number | null): void {
    this.expandedId.set(null);
    this.error.set(null);
    if (masId == null) {
      this.selectedMas.set(null);
      this.items.set([]);
      return;
    }
    const mas = this.masses().find((m) => m.id === masId) ?? null;
    this.selectedMas.set(mas);
    this.loading.set(true);
    this.timelineService
      .list({
        masId,
        types: ['INTERVENTION', 'INTERVENTION_TECHNIQUE', 'FIT']
      })
      .subscribe({
        next: (data) => {
          this.items.set(data);
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          this.items.set([]);
          this.error.set(apiErrorMessage(err, 'Impossible de charger le suivi.'));
        }
      });
  }

  reload(): void {
    this.onMasSelected(this.masCtrl.value);
  }

  isActive(col: TimelineColumn): boolean {
    return this.activeColumns().has(col);
  }

  toggleColumn(col: TimelineColumn): void {
    this.activeColumns.update((set) => {
      const next = new Set(set);
      if (next.has(col)) {
        if (next.size > 1) {
          next.delete(col);
        }
      } else {
        next.add(col);
      }
      return next;
    });
  }

  toggleAll(): void {
    this.activeColumns.set(new Set(this.columns.map((c) => c.id)));
  }

  columnOf(event: TimelineEvent): TimelineColumn {
    if (event.column) {
      return event.column as TimelineColumn;
    }
    switch (event.type as TimelineEventType) {
      case 'INTERVENTION':
        return 'BONS';
      case 'INTERVENTION_TECHNIQUE':
        return 'INTERVENTIONS';
      case 'FIT':
        return 'FIT';
      default:
        return 'BONS';
    }
  }

  columnClass(col: TimelineColumn): string {
    switch (col) {
      case 'BONS':
        return 'col-bons';
      case 'INTERVENTIONS':
        return 'col-interventions';
      case 'FIT':
        return 'col-fit';
      default:
        return '';
    }
  }

  typeClass(type: string): string {
    switch (type) {
      case 'INTERVENTION':
        return 'type-bon';
      case 'INTERVENTION_TECHNIQUE':
        return 'type-technique';
      case 'FIT':
        return 'type-fit';
      default:
        return 'type-default';
    }
  }

  typeLabel(type: string): string {
    switch (type) {
      case 'INTERVENTION':
        return 'Bon';
      case 'INTERVENTION_TECHNIQUE':
        return 'Intervention';
      case 'FIT':
        return 'FIT';
      default:
        return type;
    }
  }

  eventKey(event: TimelineEvent, index: number): string {
    return `${event.type}-${event.refId ?? 'x'}-${event.at}-${index}`;
  }

  toggleExpand(key: string): void {
    this.expandedId.update((cur) => (cur === key ? null : key));
  }

  linkFor(event: TimelineEvent): string | null {
    if (event.refType === 'INTERVENTION') {
      return '/devices/interventions';
    }
    if (event.refType === 'INTERVENTION_TECHNIQUE') {
      return '/mas/interventions';
    }
    if (event.refType === 'FIT' && event.refId != null) {
      return `/mas/fit/${event.refId}`;
    }
    return null;
  }

  masOptionLabel(mas: Mas): string {
    const marque = (mas.marqueLabel || mas.marque || '').trim();
    return marque ? `${mas.numero} — ${marque}` : mas.numero;
  }

  masMarquePart(mas: Mas): string {
    return (mas.marqueLabel || mas.marque || '').trim();
  }

  deltaLabel(delta?: number | null): string {
    if (delta == null) {
      return '';
    }
    return delta > 0 ? `+${delta}` : String(delta);
  }
}
