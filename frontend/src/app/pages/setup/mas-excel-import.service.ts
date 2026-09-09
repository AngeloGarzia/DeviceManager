import { Injectable } from '@angular/core';
import ExcelJS from 'exceljs';
import {
  MasExcelColumn,
  MasExcelRawRow,
  MasImportFieldKey,
  MasImportPreviewRow,
  DbDupPolicy,
  FileDupPolicy,
  normalizeHeader
} from './mas-excel-import.types';

@Injectable({ providedIn: 'root' })
export class MasExcelImportService {
  async parseWorkbook(file: File): Promise<{
    sheetName: string;
    columns: MasExcelColumn[];
    rows: MasExcelRawRow[];
  }> {
    const buffer = await file.arrayBuffer();
    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(buffer);
    const worksheet = workbook.worksheets[0];
    if (!worksheet) {
      throw new Error('Le fichier Excel ne contient aucune feuille.');
    }

    const headerRow = worksheet.getRow(1);
    const columns: MasExcelColumn[] = [];
    headerRow.eachCell({ includeEmpty: false }, (cell, colNumber) => {
      const header = this.cellToDisplay(cell.value).trim();
      if (header) {
        columns.push({ index: colNumber, header });
      }
    });
    if (columns.length === 0) {
      throw new Error('Aucune colonne détectée sur la première ligne.');
    }

    const rows: MasExcelRawRow[] = [];
    worksheet.eachRow({ includeEmpty: false }, (row, rowNumber) => {
      if (rowNumber === 1) {
        return;
      }
      const values: Record<number, unknown> = {};
      let hasValue = false;
      for (const col of columns) {
        const raw = row.getCell(col.index).value;
        values[col.index] = raw;
        if (this.cellToDisplay(raw).trim()) {
          hasValue = true;
        }
      }
      if (hasValue) {
        rows.push({ rowNumber, values });
      }
    });

    return { sheetName: worksheet.name, columns, rows };
  }

  buildPreview(params: {
    rows: MasExcelRawRow[];
    mapping: Partial<Record<MasImportFieldKey, number | null>>;
    enabledFields: Set<MasImportFieldKey>;
    fileDupPolicy: FileDupPolicy;
    dbDupPolicy: DbDupPolicy;
    existingNumeros: Map<string, number>;
  }): MasImportPreviewRow[] {
    const { rows, mapping, enabledFields, fileDupPolicy, dbDupPolicy, existingNumeros } = params;
    const parsed = rows.map((row) => this.mapRow(row, mapping, enabledFields));

    const byNumero = new Map<string, number[]>();
    parsed.forEach((p, idx) => {
      if (!p.numero) {
        return;
      }
      const key = p.numero.toLowerCase();
      const list = byNumero.get(key) ?? [];
      list.push(idx);
      byNumero.set(key, list);
    });

    const keepIdx = new Set<number>();
    for (const indexes of byNumero.values()) {
      if (indexes.length === 1) {
        keepIdx.add(indexes[0]);
        continue;
      }
      if (fileDupPolicy === 'keep-first') {
        keepIdx.add(indexes[0]);
      } else if (fileDupPolicy === 'keep-last') {
        keepIdx.add(indexes[indexes.length - 1]);
      }
      // skip-all: none kept
    }

    return parsed.map((p, idx) => {
      if (!p.numero) {
        return { ...p, status: 'invalid', message: 'N° machine manquant' };
      }
      if (enabledFields.has('marque') && !p.marque) {
        return { ...p, status: 'invalid', message: 'Marque manquante' };
      }

      const key = p.numero.toLowerCase();
      const fileDups = byNumero.get(key) ?? [];
      if (fileDups.length > 1 && !keepIdx.has(idx)) {
        return {
          ...p,
          status: 'dup-file',
          message:
            fileDupPolicy === 'skip-all'
              ? 'Doublon dans le fichier (ignoré)'
              : 'Doublon dans le fichier (autre ligne retenue)'
        };
      }

      const existingId = existingNumeros.get(key);
      if (existingId != null) {
        if (dbDupPolicy === 'skip') {
          return {
            ...p,
            status: 'dup-db-skip',
            message: 'Déjà en base (ignoré)',
            existingMasId: existingId
          };
        }
        return {
          ...p,
          status: 'dup-db-update',
          message: 'Déjà en base (mise à jour)',
          existingMasId: existingId
        };
      }

      return { ...p, status: 'ready', message: 'À créer' };
    });
  }

  private mapRow(
    row: MasExcelRawRow,
    mapping: Partial<Record<MasImportFieldKey, number | null>>,
    enabledFields: Set<MasImportFieldKey>
  ): MasImportPreviewRow {
    const read = (key: MasImportFieldKey): string => {
      if (!enabledFields.has(key)) {
        return '';
      }
      const col = mapping[key];
      if (col == null) {
        return '';
      }
      return this.cellToDisplay(row.values[col]).trim();
    };

    const numero = read('numero');
    const marque = read('marque');
    const denoValeurRaw = read('denoValeur');
    const tauxRaw = read('tauxRedistribution');

    return {
      rowNumber: row.rowNumber,
      numero,
      marque,
      numeroSocle: read('numeroSocle'),
      numeroSerie: read('numeroSerie'),
      typeMachine: read('typeMachine'),
      denoLabel: read('denoLabel'),
      denoValeur: this.parseNumber(denoValeurRaw),
      tauxRedistribution: this.parseNumber(tauxRaw),
      dateMiseEnService: this.parseDateIso(read('dateMiseEnService'), row.values[mapping.dateMiseEnService ?? -1]),
      dateCessation: this.parseDateIso(read('dateCessation'), row.values[mapping.dateCessation ?? -1]),
      status: 'ready',
      message: ''
    };
  }

  cellToDisplay(value: unknown): string {
    if (value == null) {
      return '';
    }
    if (value instanceof Date) {
      return value.toISOString().slice(0, 10);
    }
    if (typeof value === 'object' && value !== null && 'text' in value) {
      return String((value as { text?: unknown }).text ?? '');
    }
    if (typeof value === 'object' && value !== null && 'result' in value) {
      return this.cellToDisplay((value as { result?: unknown }).result);
    }
    if (typeof value === 'object' && value !== null && 'richText' in value) {
      const parts = (value as { richText?: Array<{ text?: string }> }).richText ?? [];
      return parts.map((p) => p.text ?? '').join('');
    }
    return String(value);
  }

  parseNumber(raw: string): number | null {
    if (!raw.trim()) {
      return null;
    }
    const n = Number(String(raw).replace(',', '.').replace(/[^\d.+-]/g, ''));
    return Number.isFinite(n) ? n : null;
  }

  parseDateIso(display: string, raw: unknown): string | null {
    if (raw instanceof Date && !Number.isNaN(raw.getTime())) {
      return raw.toISOString().slice(0, 10);
    }
    const t = display.trim();
    if (!t) {
      return null;
    }
    // Excel serial as string
    if (/^\d+(\.\d+)?$/.test(t)) {
      const serial = Number(t);
      if (serial > 20000 && serial < 80000) {
        const epoch = new Date(Date.UTC(1899, 11, 30));
        epoch.setUTCDate(epoch.getUTCDate() + Math.floor(serial));
        return epoch.toISOString().slice(0, 10);
      }
    }
    const dmy = t.match(/^(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})$/);
    if (dmy) {
      const dd = Number(dmy[1]);
      const mm = Number(dmy[2]);
      let yyyy = Number(dmy[3]);
      if (yyyy < 100) {
        yyyy += 2000;
      }
      const d = new Date(Date.UTC(yyyy, mm - 1, dd));
      if (!Number.isNaN(d.getTime())) {
        return d.toISOString().slice(0, 10);
      }
    }
    if (/^\d{4}-\d{2}-\d{2}/.test(t)) {
      return t.slice(0, 10);
    }
    const parsed = new Date(t);
    if (!Number.isNaN(parsed.getTime())) {
      return parsed.toISOString().slice(0, 10);
    }
    return null;
  }

  /** Expose for tests / header helpers. */
  normalizeHeader(raw: string): string {
    return normalizeHeader(raw);
  }
}
