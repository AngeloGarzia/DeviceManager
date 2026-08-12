import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TimelineEvent, TimelineEventType } from '../../models/models';
import { TimelineService } from '../../services/timeline.service';
import { apiErrorMessage } from '../../shared/api-error';

interface TypeFilter {
  type: TimelineEventType;
  label: string;
  icon: string;
}

/**
 * Timeline visuelle : demandes, validations, réceptions, interventions, stock.
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

  readonly filters: TypeFilter[] = [
    { type: 'ORDER_REQUEST', label: 'Demandes', icon: 'mail' },
    { type: 'ORDER_VALIDATED', label: 'Validations', icon: 'verified' },
    { type: 'ORDER_RECEIVED', label: 'Réceptions', icon: 'inventory' },
    { type: 'INTERVENTION', label: 'Interventions', icon: 'handyman' },
    { type: 'STOCK_ADJUSTMENT', label: 'Stock', icon: 'tune' }
  ];

  readonly activeTypes = signal<Set<TimelineEventType>>(
    new Set(this.filters.map((f) => f.type))
  );
  readonly items = signal<TimelineEvent[]>([]);
  readonly expandedId = signal<string | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly visibleItems = computed(() => {
    const active = this.activeTypes();
    return this.items().filter((e) => active.has(e.type as TimelineEventType));
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

  isActive(type: TimelineEventType): boolean {
    return this.activeTypes().has(type);
  }

  toggleType(type: TimelineEventType): void {
    this.activeTypes.update((set) => {
      const next = new Set(set);
      if (next.has(type)) {
        if (next.size > 1) {
          next.delete(type);
        }
      } else {
        next.add(type);
      }
      return next;
    });
  }

  toggleAll(): void {
    this.activeTypes.set(new Set(this.filters.map((f) => f.type)));
  }

  eventKey(event: TimelineEvent, index: number): string {
    return `${event.type}-${event.refId ?? 'x'}-${event.at}-${index}`;
  }

  toggleExpand(key: string): void {
    this.expandedId.update((cur) => (cur === key ? null : key));
  }

  typeMeta(type: string): TypeFilter {
    return (
      this.filters.find((f) => f.type === type) ?? {
        type: type as TimelineEventType,
        label: type,
        icon: 'event'
      }
    );
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
        return 'type-intervention';
      case 'STOCK_ADJUSTMENT':
        return 'type-stock';
      default:
        return 'type-default';
    }
  }

  linkFor(event: TimelineEvent): string | null {
    if (event.refType === 'ORDER') {
      return '/order-requests';
    }
    if (event.refType === 'INTERVENTION') {
      return '/devices/interventions';
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
