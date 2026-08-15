import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TimelineColumn, TimelineEvent, TimelineEventType } from '../../models/models';
import { TimelineService } from '../../services/timeline.service';
import { apiErrorMessage } from '../../shared/api-error';

interface ColumnDef {
  id: TimelineColumn;
  label: string;
  icon: string;
  types: TimelineEventType[];
}

/**
 * Timeline en colonnes (swimlanes) : Commandes | Bons | Interventions | FIT | Stock.
 */
@Component({
  selector: 'app-order-timeline',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './order-timeline.component.html',
  styleUrl: './order-timeline.component.scss'
})
export class OrderTimelineComponent implements OnInit {
  private readonly timelineService = inject(TimelineService);

  readonly columns: ColumnDef[] = [
    {
      id: 'COMMANDES',
      label: 'Commandes',
      icon: 'shopping_cart',
      types: ['ORDER_REQUEST', 'ORDER_VALIDATED', 'ORDER_RECEIVED']
    },
    { id: 'BONS', label: "Bons d'intervention", icon: 'receipt_long', types: ['INTERVENTION'] },
    {
      id: 'INTERVENTIONS',
      label: 'Interventions techniques',
      icon: 'engineering',
      types: ['INTERVENTION_TECHNIQUE']
    },
    { id: 'FIT', label: 'FIT', icon: 'description', types: ['FIT'] },
    { id: 'STOCK', label: 'Stock', icon: 'tune', types: ['STOCK_ADJUSTMENT'] }
  ];

  readonly activeColumns = signal<Set<TimelineColumn>>(
    new Set(this.columns.map((c) => c.id))
  );
  readonly items = signal<TimelineEvent[]>([]);
  readonly expandedId = signal<string | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

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
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.timelineService.list().subscribe({
      next: (data) => {
        this.items.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'Impossible de charger la timeline.'));
        this.loading.set(false);
      }
    });
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
    switch (event.type) {
      case 'ORDER_REQUEST':
      case 'ORDER_VALIDATED':
      case 'ORDER_RECEIVED':
        return 'COMMANDES';
      case 'INTERVENTION':
        return 'BONS';
      case 'INTERVENTION_TECHNIQUE':
        return 'INTERVENTIONS';
      case 'FIT':
        return 'FIT';
      default:
        return 'STOCK';
    }
  }

  typeLabel(type: string): string {
    switch (type) {
      case 'ORDER_REQUEST':
        return 'Demande';
      case 'ORDER_VALIDATED':
        return 'Validation';
      case 'ORDER_RECEIVED':
        return 'Réception';
      case 'INTERVENTION':
        return 'Bon';
      case 'INTERVENTION_TECHNIQUE':
        return 'Intervention';
      case 'FIT':
        return 'FIT';
      case 'STOCK_ADJUSTMENT':
        return 'Ajustement';
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

  typeClass(type: string): string {
    switch (type) {
      case 'ORDER_REQUEST':
        return 'type-request';
      case 'ORDER_VALIDATED':
        return 'type-validated';
      case 'ORDER_RECEIVED':
        return 'type-received';
      case 'INTERVENTION':
        return 'type-bon';
      case 'INTERVENTION_TECHNIQUE':
        return 'type-technique';
      case 'FIT':
        return 'type-fit';
      case 'STOCK_ADJUSTMENT':
        return 'type-stock';
      default:
        return 'type-default';
    }
  }

  columnClass(col: TimelineColumn): string {
    switch (col) {
      case 'COMMANDES':
        return 'col-commandes';
      case 'BONS':
        return 'col-bons';
      case 'INTERVENTIONS':
        return 'col-interventions';
      case 'FIT':
        return 'col-fit';
      case 'STOCK':
        return 'col-stock';
      default:
        return '';
    }
  }

  linkFor(event: TimelineEvent): string | null {
    if (event.refType === 'ORDER') {
      return '/order-requests';
    }
    if (event.refType === 'INTERVENTION') {
      return '/devices/interventions';
    }
    if (event.refType === 'INTERVENTION_TECHNIQUE') {
      return '/mas/interventions';
    }
    if (event.refType === 'FIT' && event.refId != null) {
      return `/mas/fit/${event.refId}`;
    }
    if (event.refType === 'DEVICE' && event.refId != null) {
      return `/devices/${event.refId}`;
    }
    return null;
  }

  deltaLabel(delta?: number | null): string {
    if (delta == null) {
      return '';
    }
    return delta > 0 ? `+${delta}` : String(delta);
  }
}
