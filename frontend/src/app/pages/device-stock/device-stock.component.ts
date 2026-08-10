import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule, NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatMenuModule } from '@angular/material/menu';
import { Device } from '../../models/models';
import { DeviceService } from '../../services/device.service';
import { DeviceStockExportService } from '../../services/device-stock-export.service';
import { StockGroup, StockGroupMode } from './device-stock.types';

export type { StockGroup, StockGroupMode } from './device-stock.types';

/**
 * Vue stock des pièces détachées, regroupées par SFM et/ou marque.
 * Permet le repliage des groupes et l'export Excel ou PDF.
 */
@Component({
  selector: 'app-device-stock',
  standalone: true,
  imports: [
    CommonModule,
    NgTemplateOutlet,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatMenuModule
  ],
  templateUrl: './device-stock.component.html',
  styleUrl: './device-stock.component.scss'
})
export class DeviceStockComponent implements OnInit {
  private readonly deviceService = inject(DeviceService);
  private readonly exportService = inject(DeviceStockExportService);

  readonly items = signal<Device[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly groupMode = signal<StockGroupMode>('sfm');
  readonly collapsed = signal<Record<string, boolean>>({});
  readonly savingStockIds = signal<Record<number, boolean>>({});
  /** Stock déjà persisté, pour ne sauvegarder que si la saisie a changé. */
  private readonly persistedStock = new Map<number, number>();
  query = '';

  readonly groups = computed(() => this.buildGroups(this.items(), this.groupMode()));

  ngOnInit(): void {
    this.load();
  }

  /** Nombre total de pièces dans le stock. */
  get total(): number {
    return this.items().length;
  }

  /** Compte les pièces d'un groupe, y compris ses sous-groupes. */
  countDevices(group: StockGroup): number {
    if (group.children?.length) {
      return group.children.reduce((n, c) => n + c.items.length, 0);
    }
    return group.items.length;
  }

  /** Charge les pièces selon le filtre de recherche courant. */
  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.deviceService.list(this.query).subscribe({
      next: (data) => {
        this.items.set(data);
        this.persistedStock.clear();
        for (const item of data) {
          this.persistedStock.set(item.id, item.stock ?? 0);
        }
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger le stock des pièces détachées.');
        this.loading.set(false);
      }
    });
  }

  /** Change le mode de regroupement et réinitialise l'état de repliage. */
  setGroupMode(mode: StockGroupMode): void {
    this.groupMode.set(mode);
    this.collapsed.set({});
  }

  /** Indique si un groupe est replié. */
  isCollapsed(key: string): boolean {
    return !!this.collapsed()[key];
  }

  /** Bascule l'état replié/déplié d'un groupe. */
  toggleGroup(key: string): void {
    this.collapsed.update((map) => ({ ...map, [key]: !map[key] }));
  }

  /** Déplie tous les groupes. */
  expandAll(): void {
    this.collapsed.set({});
  }

  /** Replie tous les groupes et sous-groupes. */
  collapseAll(): void {
    const next: Record<string, boolean> = {};
    for (const group of this.groups()) {
      next[group.key] = true;
      for (const child of group.children ?? []) {
        next[child.key] = true;
      }
    }
    this.collapsed.set(next);
  }

  /** Exporte le stock courant au format Excel. */
  exportExcel(): void {
    if (this.items().length === 0) {
      return;
    }
    void this.exportService.exportExcel(this.groups(), this.groupMode());
  }

  /** Exporte le stock courant au format PDF. */
  exportPdf(): void {
    if (this.items().length === 0) {
      return;
    }
    this.exportService.exportPdf(this.groups(), this.groupMode());
  }

  /** URL absolue de la photo d'une pièce. */
  photoUrl(item: Device): string {
    return this.deviceService.resolvePhotoUrl(item.photoUrl);
  }

  /** Relance le chargement si l'API Render est encore endormie. */
  onPhotoError(event: Event): void {
    this.deviceService.retryPhotoOnError(event);
  }

  /** Indique si la sauvegarde du stock est en cours pour une pièce. */
  isSavingStock(id: number): boolean {
    return !!this.savingStockIds()[id];
  }

  /** Met à jour la quantité affichée pendant la saisie. */
  onStockDraft(item: Device, value: number | string | null): void {
    const n = Number(value);
    item.stock = Number.isFinite(n) ? Math.max(0, Math.trunc(n)) : 0;
  }

  /** Enregistre la quantité en stock saisie pour une pièce. */
  saveStock(item: Device): void {
    const next = Math.max(0, Math.trunc(Number(item.stock) || 0));
    item.stock = next;
    const previous = this.persistedStock.get(item.id) ?? 0;
    if (next === previous || this.isSavingStock(item.id)) {
      return;
    }
    this.savingStockIds.update((map) => ({ ...map, [item.id]: true }));
    this.deviceService.updateStock(item.id, next).subscribe({
      next: (updated) => {
        this.persistedStock.set(item.id, updated.stock);
        this.items.update((list) =>
          list.map((d) => (d.id === item.id ? { ...d, stock: updated.stock } : d))
        );
        this.savingStockIds.update((map) => {
          const rest = { ...map };
          delete rest[item.id];
          return rest;
        });
      },
      error: () => {
        item.stock = previous;
        this.error.set(`Impossible de mettre à jour le stock de « ${item.nom} ».`);
        this.savingStockIds.update((map) => {
          const rest = { ...map };
          delete rest[item.id];
          return rest;
        });
      }
    });
  }

  /** Libellé de marque affiché pour une pièce. */
  marqueLabel(item: Device): string {
    return item.marqueLabel || item.marque || item.masMarque || 'Sans marque';
  }

  /** Libellé SFM affiché pour une pièce. */
  sfmLabel(item: Device): string {
    return item.sfmNom?.trim() || 'Sans SFM';
  }

  private buildGroups(items: Device[], mode: StockGroupMode): StockGroup[] {
    if (mode === 'none') {
      return [
        {
          key: 'all',
          label: 'Toutes les pièces',
          items: [...items].sort((a, b) => a.nom.localeCompare(b.nom, 'fr'))
        }
      ];
    }

    if (mode === 'sfm') {
      return this.groupBy(items, (d) => this.sfmKey(d), (d) => this.sfmLabel(d));
    }

    if (mode === 'marque') {
      return this.groupBy(items, (d) => this.marqueKey(d), (d) => this.marqueLabel(d));
    }

    // both: SFM → marque
    const bySfm = this.groupBy(items, (d) => this.sfmKey(d), (d) => this.sfmLabel(d));
    return bySfm.map((sfmGroup) => ({
      ...sfmGroup,
      items: [],
      children: this.groupBy(
        sfmGroup.items,
        (d) => `${sfmGroup.key}::${this.marqueKey(d)}`,
        (d) => this.marqueLabel(d)
      )
    }));
  }

  private groupBy(
    items: Device[],
    keyFn: (d: Device) => string,
    labelFn: (d: Device) => string
  ): StockGroup[] {
    const map = new Map<string, StockGroup>();
    for (const item of items) {
      const key = keyFn(item);
      let group = map.get(key);
      if (!group) {
        group = { key, label: labelFn(item), items: [] };
        map.set(key, group);
      }
      group.items.push(item);
    }
    return [...map.values()]
      .map((g) => ({
        ...g,
        items: g.items.sort((a, b) => a.nom.localeCompare(b.nom, 'fr'))
      }))
      .sort((a, b) => a.label.localeCompare(b.label, 'fr'));
  }

  private sfmKey(item: Device): string {
    return item.sfmId != null ? `sfm-${item.sfmId}` : 'sfm-none';
  }

  private marqueKey(item: Device): string {
    if (item.marqueId != null) {
      return `marque-${item.marqueId}`;
    }
    const label = this.marqueLabel(item);
    return `marque-${label.toLowerCase()}`;
  }
}
