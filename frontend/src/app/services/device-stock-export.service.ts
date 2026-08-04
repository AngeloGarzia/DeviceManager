import { Injectable, inject } from '@angular/core';
import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import * as XLSX from 'xlsx';
import { Device } from '../models/models';
import { AuthService } from './auth.service';
import { StockGroup, StockGroupMode } from '../pages/device-stock/device-stock.types';

export interface StockExportRow {
  groupeSfm: string;
  groupeMarque: string;
  nom: string;
  reference: string;
  sfm: string;
  mas: string;
  marque: string;
  statut: string;
  usage: string;
  dateAcquisition: string;
}

@Injectable({ providedIn: 'root' })
export class DeviceStockExportService {
  private readonly auth = inject(AuthService);

  exportExcel(groups: StockGroup[], mode: StockGroupMode): void {
    const rows = this.toRows(groups, mode);
    const sheetRows = rows.map((r) => this.toSheetRow(r, mode));
    const worksheet = XLSX.utils.json_to_sheet(sheetRows);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, 'Stock');
    XLSX.writeFile(workbook, this.fileName('xlsx'));
  }

  exportPdf(groups: StockGroup[], mode: StockGroupMode): void {
    const rows = this.toRows(groups, mode);
    const doc = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' });
    const atelier = this.auth.currentAtelier()?.nom || this.auth.currentAtelier()?.label || '';
    const title = 'Stock des pièces détachées';
    const subtitle = [
      atelier ? `Atelier : ${atelier}` : null,
      `Regroupement : ${this.groupModeLabel(mode)}`,
      `${rows.length} pièce(s)`,
      new Date().toLocaleString('fr-FR')
    ]
      .filter(Boolean)
      .join(' · ');

    doc.setFontSize(14);
    doc.text(title, 14, 14);
    doc.setFontSize(9);
    doc.setTextColor(90);
    doc.text(subtitle, 14, 20);
    doc.setTextColor(0);

    const columns = this.pdfColumns(mode);
    autoTable(doc, {
      startY: 24,
      head: [columns.map((c) => c.header)],
      body: rows.map((row) => columns.map((c) => row[c.key])),
      styles: { fontSize: 8, cellPadding: 1.5 },
      headStyles: { fillColor: [11, 107, 203], textColor: 255 },
      alternateRowStyles: { fillColor: [248, 250, 252] },
      margin: { left: 10, right: 10 }
    });

    doc.save(this.fileName('pdf'));
  }

  private toRows(groups: StockGroup[], mode: StockGroupMode): StockExportRow[] {
    const rows: StockExportRow[] = [];
    for (const group of groups) {
      if (mode === 'both' && group.children?.length) {
        for (const child of group.children) {
          for (const item of child.items) {
            rows.push(this.toRow(item, group.label, child.label));
          }
        }
        continue;
      }
      for (const item of group.items) {
        if (mode === 'sfm') {
          rows.push(this.toRow(item, group.label, this.marqueLabel(item)));
        } else if (mode === 'marque') {
          rows.push(this.toRow(item, this.sfmLabel(item), group.label));
        } else {
          rows.push(this.toRow(item, this.sfmLabel(item), this.marqueLabel(item)));
        }
      }
    }
    return rows;
  }

  private toRow(item: Device, groupeSfm: string, groupeMarque: string): StockExportRow {
    return {
      groupeSfm,
      groupeMarque,
      nom: item.nom || '',
      reference: item.reference || '',
      sfm: item.sfmNom || '',
      mas: item.masNumero || '',
      marque: this.marqueLabel(item),
      statut: item.obsolete ? 'Obsolète' : 'Active',
      usage: item.usage || '',
      dateAcquisition: item.dateAcquisition
        ? new Date(item.dateAcquisition).toLocaleDateString('fr-FR')
        : ''
    };
  }

  private toSheetRow(row: StockExportRow, mode: StockGroupMode): Record<string, string> {
    const base: Record<string, string> = {};
    if (mode === 'sfm' || mode === 'both') {
      base['Groupe SFM'] = row.groupeSfm;
    }
    if (mode === 'marque' || mode === 'both') {
      base['Groupe marque'] = row.groupeMarque;
    }
    base['Nom'] = row.nom;
    base['Référence'] = row.reference;
    base['SFM'] = row.sfm;
    base['MAS'] = row.mas;
    base['Marque'] = row.marque;
    base['Statut'] = row.statut;
    base['Usage'] = row.usage;
    base['Date acquisition'] = row.dateAcquisition;
    return base;
  }

  private pdfColumns(mode: StockGroupMode): Array<{ header: string; key: keyof StockExportRow }> {
    const cols: Array<{ header: string; key: keyof StockExportRow }> = [];
    if (mode === 'sfm' || mode === 'both') {
      cols.push({ header: 'Groupe SFM', key: 'groupeSfm' });
    }
    if (mode === 'marque' || mode === 'both') {
      cols.push({ header: 'Groupe marque', key: 'groupeMarque' });
    }
    cols.push(
      { header: 'Nom', key: 'nom' },
      { header: 'Référence', key: 'reference' },
      { header: 'SFM', key: 'sfm' },
      { header: 'MAS', key: 'mas' },
      { header: 'Marque', key: 'marque' },
      { header: 'Statut', key: 'statut' },
      { header: 'Acquisition', key: 'dateAcquisition' }
    );
    return cols;
  }

  private groupModeLabel(mode: StockGroupMode): string {
    switch (mode) {
      case 'sfm':
        return 'SFM';
      case 'marque':
        return 'Marque de MAS';
      case 'both':
        return 'SFM et marque';
      default:
        return 'Aucun';
    }
  }

  private fileName(ext: string): string {
    const stamp = new Date().toISOString().slice(0, 10);
    const atelier = this.auth.currentAtelier()?.nom?.replace(/[^\w\-]+/g, '_') || 'atelier';
    return `stock-pieces-${atelier}-${stamp}.${ext}`;
  }

  private marqueLabel(item: Device): string {
    return item.marqueLabel || item.marque || item.masMarque || 'Sans marque';
  }

  private sfmLabel(item: Device): string {
    return item.sfmNom?.trim() || 'Sans SFM';
  }
}
