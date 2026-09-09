import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatRadioModule } from '@angular/material/radio';
import { firstValueFrom } from 'rxjs';
import { AtelierSummary, DenoOption, Mas, MasForm, MarqueMasOption, RegleJeuxOption } from '../../models/models';
import { AuthService } from '../../services/auth.service';
import { MasService } from '../../services/mas.service';
import { apiErrorMessage } from '../../shared/api-error';
import { MasExcelImportService } from './mas-excel-import.service';
import {
  DbDupPolicy,
  FileDupPolicy,
  MAS_IMPORT_FIELDS,
  MasExcelColumn,
  MasExcelRawRow,
  MasImportFieldKey,
  MasImportPreviewRow,
  suggestColumnMappings
} from './mas-excel-import.types';

@Component({
  selector: 'app-mas-excel-import',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatRadioModule
  ],
  templateUrl: './mas-excel-import.component.html',
  styleUrl: './mas-excel-import.component.scss'
})
export class MasExcelImportComponent implements OnInit {
  /** Ateliers disponibles (fournis par la page Setup). */
  readonly ateliers = input.required<AtelierSummary[]>();

  private readonly parser = inject(MasExcelImportService);
  private readonly masService = inject(MasService);
  private readonly auth = inject(AuthService);

  readonly fields = MAS_IMPORT_FIELDS;

  readonly fileName = signal<string | null>(null);
  readonly sheetName = signal<string | null>(null);
  readonly columns = signal<MasExcelColumn[]>([]);
  readonly rawRows = signal<MasExcelRawRow[]>([]);
  readonly mapping = signal<Partial<Record<MasImportFieldKey, number | null>>>({});
  readonly enabledFields = signal<Set<MasImportFieldKey>>(
    new Set(MAS_IMPORT_FIELDS.map((f) => f.key))
  );

  readonly atelierId = signal<number | null>(null);
  readonly regleJeuxId = signal<number | null>(null);
  readonly fileDupPolicy = signal<FileDupPolicy>('keep-first');
  readonly dbDupPolicy = signal<DbDupPolicy>('skip');
  readonly createMissingCatalog = signal(true);

  readonly regles = signal<RegleJeuxOption[]>([]);
  readonly preview = signal<MasImportPreviewRow[]>([]);
  readonly loadingFile = signal(false);
  readonly analyzing = signal(false);
  readonly importing = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);
  readonly progress = signal<{ done: number; total: number } | null>(null);

  readonly stats = computed(() => {
    const rows = this.preview();
    return {
      total: rows.length,
      ready: rows.filter((r) => r.status === 'ready').length,
      update: rows.filter((r) => r.status === 'dup-db-update').length,
      skipped: rows.filter((r) => r.status === 'dup-file' || r.status === 'dup-db-skip').length,
      invalid: rows.filter((r) => r.status === 'invalid').length
    };
  });

  readonly canAnalyze = computed(
    () =>
      this.rawRows().length > 0 &&
      this.atelierId() != null &&
      this.mapping().numero != null &&
      this.enabledFields().has('numero')
  );

  readonly canImport = computed(() => {
    const s = this.stats();
    return (
      this.atelierId() != null &&
      this.regleJeuxId() != null &&
      !this.importing() &&
      (s.ready > 0 || s.update > 0)
    );
  });

  ngOnInit(): void {
    const current = this.auth.atelierId();
    this.atelierId.set(current);
    this.masService.listReglesJeux().subscribe({
      next: (list) => this.regles.set(list),
      error: () => this.regles.set([])
    });
  }

  isFieldEnabled(key: MasImportFieldKey): boolean {
    return this.enabledFields().has(key);
  }

  toggleField(key: MasImportFieldKey, checked: boolean): void {
    if ((key === 'numero' || key === 'marque') && !checked) {
      return;
    }
    const next = new Set(this.enabledFields());
    if (checked) {
      next.add(key);
    } else {
      next.delete(key);
    }
    this.enabledFields.set(next);
    this.preview.set([]);
  }

  onMappingChange(key: MasImportFieldKey, colIndex: number | null): void {
    this.mapping.update((m) => ({ ...m, [key]: colIndex }));
    this.preview.set([]);
  }

  async onFileSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.error.set(null);
    this.success.set(null);
    this.preview.set([]);
    this.loadingFile.set(true);
    try {
      const parsed = await this.parser.parseWorkbook(file);
      this.fileName.set(file.name);
      this.sheetName.set(parsed.sheetName);
      this.columns.set(parsed.columns);
      this.rawRows.set(parsed.rows);
      this.mapping.set(suggestColumnMappings(parsed.columns));
    } catch (err) {
      this.error.set(err instanceof Error ? err.message : 'Lecture du fichier impossible');
      this.fileName.set(null);
      this.columns.set([]);
      this.rawRows.set([]);
    } finally {
      this.loadingFile.set(false);
    }
  }

  async analyze(): Promise<void> {
    if (!this.canAnalyze()) {
      return;
    }
    const atelierId = this.atelierId();
    if (atelierId == null) {
      return;
    }
    this.error.set(null);
    this.success.set(null);
    this.analyzing.set(true);
    const previous = this.auth.atelierId();
    try {
      this.auth.setAtelierId(atelierId);
      const existing = await firstValueFrom(this.masService.list());
      const existingNumeros = new Map<string, number>();
      for (const m of existing) {
        existingNumeros.set(m.numero.trim().toLowerCase(), m.id);
      }
      const rows = this.parser.buildPreview({
        rows: this.rawRows(),
        mapping: this.mapping(),
        enabledFields: this.enabledFields(),
        fileDupPolicy: this.fileDupPolicy(),
        dbDupPolicy: this.dbDupPolicy(),
        existingNumeros
      });
      this.preview.set(rows);
    } catch (err) {
      this.error.set(apiErrorMessage(err, 'Analyse impossible'));
      this.preview.set([]);
    } finally {
      this.auth.setAtelierId(previous);
      this.analyzing.set(false);
    }
  }

  async importRows(): Promise<void> {
    if (!this.canImport()) {
      return;
    }
    const atelierId = this.atelierId()!;
    const regleId = this.regleJeuxId()!;
    const toProcess = this.preview().filter(
      (r) => r.status === 'ready' || r.status === 'dup-db-update'
    );
    if (toProcess.length === 0) {
      return;
    }

    this.error.set(null);
    this.success.set(null);
    this.importing.set(true);
    this.progress.set({ done: 0, total: toProcess.length });
    const previous = this.auth.atelierId();

    let created = 0;
    let updated = 0;
    let failed = 0;
    const errors: string[] = [];

    try {
      this.auth.setAtelierId(atelierId);
      let marques = await firstValueFrom(this.masService.listMarques());
      let denos = await firstValueFrom(this.masService.listDenos());

      for (let i = 0; i < toProcess.length; i++) {
        const row = toProcess[i];
        try {
          const marqueId = await this.resolveMarqueId(row.marque, marques, (list) => {
            marques = list;
          });
          if (marqueId == null) {
            throw new Error(`Marque introuvable : ${row.marque || '—'}`);
          }
          const denoId = await this.resolveDenoId(row, denos, (list) => {
            denos = list;
          });
          const payload = this.toMasForm(row, marqueId, denoId, regleId);
          if (row.status === 'dup-db-update' && row.existingMasId != null) {
            const existing = await firstValueFrom(this.masService.get(row.existingMasId));
            const merged = this.mergeUpdate(existing, payload);
            await firstValueFrom(this.masService.update(row.existingMasId, merged));
            updated++;
          } else {
            await firstValueFrom(this.masService.create(payload));
            created++;
          }
        } catch (err) {
          failed++;
          errors.push(`Ligne ${row.rowNumber} (${row.numero}): ${apiErrorMessage(err, 'échec')}`);
        }
        this.progress.set({ done: i + 1, total: toProcess.length });
      }

      const parts = [
        created ? `${created} créée(s)` : null,
        updated ? `${updated} mise(s) à jour` : null,
        failed ? `${failed} échec(s)` : null
      ].filter(Boolean);
      this.success.set(parts.join(' · ') || 'Aucune modification');
      if (errors.length) {
        this.error.set(errors.slice(0, 8).join('\n') + (errors.length > 8 ? `\n… +${errors.length - 8}` : ''));
      }
      await this.analyze();
    } catch (err) {
      this.error.set(apiErrorMessage(err, 'Import interrompu'));
    } finally {
      this.auth.setAtelierId(previous);
      this.importing.set(false);
      this.progress.set(null);
    }
  }

  statusClass(status: MasImportPreviewRow['status']): string {
    switch (status) {
      case 'ready':
        return 'ok';
      case 'dup-db-update':
        return 'upd';
      case 'dup-file':
      case 'dup-db-skip':
        return 'skip';
      default:
        return 'bad';
    }
  }

  private toMasForm(
    row: MasImportPreviewRow,
    marqueId: number,
    denoId: number | null,
    regleId: number
  ): MasForm {
    const enabled = this.enabledFields();
    const statut: MasForm['statut'] = row.dateCessation ? 'VENDUE' : 'UTILISEE';
    return {
      numero: row.numero,
      numeroSocle: enabled.has('numeroSocle') ? row.numeroSocle || null : null,
      numeroSerie: enabled.has('numeroSerie') ? row.numeroSerie || null : null,
      typeMachine: enabled.has('typeMachine') ? row.typeMachine || null : null,
      tauxRedistribution: enabled.has('tauxRedistribution') ? row.tauxRedistribution : null,
      dateMiseEnService: enabled.has('dateMiseEnService') ? row.dateMiseEnService : null,
      dateCessation: enabled.has('dateCessation') ? row.dateCessation : null,
      destinationMachineUsagee: null,
      marqueId,
      denoId: enabled.has('denoLabel') || enabled.has('denoValeur') ? denoId : null,
      multiDeno: false,
      statut,
      utilise: statut === 'UTILISEE',
      regleJeuxIds: [regleId]
    };
  }

  private mergeUpdate(existing: Mas, incoming: MasForm): MasForm {
    const enabled = this.enabledFields();
    return {
      numero: existing.numero,
      numeroSocle: enabled.has('numeroSocle') ? incoming.numeroSocle : existing.numeroSocle,
      numeroSerie: enabled.has('numeroSerie') ? incoming.numeroSerie : existing.numeroSerie,
      typeMachine: enabled.has('typeMachine') ? incoming.typeMachine : existing.typeMachine,
      tauxRedistribution: enabled.has('tauxRedistribution')
        ? incoming.tauxRedistribution
        : existing.tauxRedistribution,
      dateMiseEnService: enabled.has('dateMiseEnService')
        ? incoming.dateMiseEnService
        : existing.dateMiseEnService,
      dateCessation: enabled.has('dateCessation') ? incoming.dateCessation : existing.dateCessation,
      destinationMachineUsagee: existing.destinationMachineUsagee,
      marqueId: enabled.has('marque') ? incoming.marqueId : existing.marqueId,
      denoId:
        enabled.has('denoLabel') || enabled.has('denoValeur') ? incoming.denoId : existing.denoId,
      multiDeno: existing.multiDeno,
      statut: existing.statut,
      utilise: existing.utilise,
      regleJeuxIds:
        existing.regleJeuxIds?.length ? existing.regleJeuxIds : incoming.regleJeuxIds
    };
  }

  private async resolveMarqueId(
    label: string,
    marques: MarqueMasOption[],
    replace: (list: MarqueMasOption[]) => void
  ): Promise<number | null> {
    const key = label.trim().toLowerCase();
    if (!key) {
      return null;
    }
    const found = marques.find((m) => m.label.trim().toLowerCase() === key);
    if (found) {
      return found.id;
    }
    if (!this.createMissingCatalog()) {
      return null;
    }
    const created = await firstValueFrom(this.masService.createMarque(label.trim()));
    replace([...marques, created]);
    return created.id;
  }

  private async resolveDenoId(
    row: MasImportPreviewRow,
    denos: DenoOption[],
    replace: (list: DenoOption[]) => void
  ): Promise<number | null> {
    const enabled = this.enabledFields();
    if (!enabled.has('denoLabel') && !enabled.has('denoValeur')) {
      return null;
    }
    const label = row.denoLabel.trim();
    const valeur = row.denoValeur;
    if (!label && valeur == null) {
      return null;
    }
    if (label) {
      const byLabel = denos.find((d) => d.label.trim().toLowerCase() === label.toLowerCase());
      if (byLabel) {
        return byLabel.id;
      }
    }
    if (valeur != null) {
      const byVal = denos.find((d) => Math.abs(Number(d.valeur) - valeur) < 0.00001);
      if (byVal) {
        return byVal.id;
      }
    }
    if (!this.createMissingCatalog() || valeur == null) {
      return null;
    }
    const created = await firstValueFrom(this.masService.createDeno(valeur, label || undefined));
    replace([...denos, created]);
    return created.id;
  }
}
