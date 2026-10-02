import { Injectable } from '@angular/core';
import { jsPDF } from 'jspdf';
import QRCode from 'qrcode';

/** Une étiquette à imprimer (contenu QR + légende). */
export interface QrLabelSlot {
  /** URL encodée dans le QR (ex. accès public règles de jeux). */
  url: string;
  /** Texte court sous le QR (ex. numéro MAS). */
  caption: string;
}

export interface QrLabelPdfOptions {
  /** Première case utilisée (1–48). Les précédentes restent vides. */
  firstSlot?: number;
  /** Décalage horizontal (mm) pour corriger l’imprimante. */
  offsetXMm?: number;
  /** Décalage vertical (mm). */
  offsetYMm?: number;
  /** Dessine le contour 30×30 mm (test sur papier blanc). */
  drawOutlines?: boolean;
}

/**
 * Planche A4 d’étiquettes adhésives 30×30 mm (6×8 = 48).
 * Génération 100 % navigateur (qrcode + jsPDF).
 */
@Injectable({ providedIn: 'root' })
export class QrLabelPdfService {
  static readonly PAGE_W_MM = 210;
  static readonly PAGE_H_MM = 297;
  static readonly LABEL_MM = 30;
  static readonly PITCH_MM = 32;
  static readonly MARGIN_X_MM = 10;
  static readonly MARGIN_Y_MM = 21.5;
  static readonly COLS = 6;
  static readonly ROWS = 8;
  static readonly SLOTS_PER_PAGE = 48;
  /** Un peu plus petit pour laisser 2 lignes de légende sous le QR. */
  static readonly QR_MM = 22;
  static readonly CORNER_RADIUS_MM = 1.5;

  /**
   * URL publique scannée sans connexion → règles de jeux de la MAS.
   * Point unique à adapter si le chemin change.
   */
  buildMasPublicRulesUrl(token: string, origin = window.location.origin): string {
    const t = (token || '').trim();
    if (!t) {
      throw new Error('Jeton public manquant pour la MAS.');
    }
    return `${origin.replace(/\/$/, '')}/public/r/${encodeURIComponent(t)}`;
  }

  /** Coordonnées coin haut-gauche d’une case (0-based col/row), hors décalage imprimante. */
  labelOriginMm(col: number, row: number): { x: number; y: number } {
    return {
      x: QrLabelPdfService.MARGIN_X_MM + col * QrLabelPdfService.PITCH_MM,
      y: QrLabelPdfService.MARGIN_Y_MM + row * QrLabelPdfService.PITCH_MM
    };
  }

  /**
   * Génère le PDF et le télécharge / ouvre.
   * @returns infos de contrôle (1ʳᵉ et dernière étiquette placées)
   */
  async generateAndDownload(
    slots: QrLabelSlot[],
    options: QrLabelPdfOptions = {},
    fileName = 'etiquettes-mas-qr.pdf'
  ): Promise<{ first: { x: number; y: number; page: number }; last: { x: number; y: number; page: number } | null }> {
    const doc = await this.buildPdf(slots, options);
    doc.save(fileName);
    return this.computeExtents(slots, options);
  }

  async buildPdf(slots: QrLabelSlot[], options: QrLabelPdfOptions = {}): Promise<jsPDF> {
    const firstSlot = Math.min(48, Math.max(1, options.firstSlot ?? 1));
    const offsetX = options.offsetXMm ?? 0;
    const offsetY = options.offsetYMm ?? 0;
    const drawOutlines = Boolean(options.drawOutlines);

    const doc = new jsPDF({
      orientation: 'portrait',
      unit: 'mm',
      format: 'a4'
    });

    let slotIndex = firstSlot - 1; // 0-based global
    let currentPage = 0;

    for (let i = 0; i < slots.length; i++) {
      const pageIndex = Math.floor(slotIndex / QrLabelPdfService.SLOTS_PER_PAGE);
      while (currentPage < pageIndex) {
        doc.addPage();
        currentPage++;
      }

      const onPage = slotIndex % QrLabelPdfService.SLOTS_PER_PAGE;
      const col = onPage % QrLabelPdfService.COLS;
      const row = Math.floor(onPage / QrLabelPdfService.COLS);
      const origin = this.labelOriginMm(col, row);
      const x = origin.x + offsetX;
      const y = origin.y + offsetY;

      if (drawOutlines) {
        doc.setDrawColor(180);
        doc.setLineWidth(0.2);
        doc.roundedRect(
          x,
          y,
          QrLabelPdfService.LABEL_MM,
          QrLabelPdfService.LABEL_MM,
          QrLabelPdfService.CORNER_RADIUS_MM,
          QrLabelPdfService.CORNER_RADIUS_MM,
          'S'
        );
      }

      const item = slots[i];
      const qrDataUrl = await QRCode.toDataURL(item.url, {
        errorCorrectionLevel: 'M',
        margin: 1,
        width: 256,
        color: { dark: '#000000', light: '#ffffff' }
      });

      const qrPad = (QrLabelPdfService.LABEL_MM - QrLabelPdfService.QR_MM) / 2;
      const qrX = x + qrPad;
      const qrY = y + 0.8;
      doc.addImage(qrDataUrl, 'PNG', qrX, qrY, QrLabelPdfService.QR_MM, QrLabelPdfService.QR_MM);

      const lines = this.captionLines(item.caption || '');
      doc.setTextColor(20);
      const cx = x + QrLabelPdfService.LABEL_MM / 2;
      const line1Y = y + QrLabelPdfService.LABEL_MM - 4.6;
      const line2Y = y + QrLabelPdfService.LABEL_MM - 1.6;
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(6.5);
      doc.text(lines[0], cx, line1Y, { align: 'center', baseline: 'bottom' });
      if (lines[1]) {
        doc.setFont('helvetica', 'normal');
        doc.setFontSize(5.5);
        doc.text(lines[1], cx, line2Y, { align: 'center', baseline: 'bottom' });
      }

      slotIndex++;
    }

    return doc;
  }

  /** Coordonnées de contrôle (avec décalages) pour la 1ʳᵉ et la dernière étiquette remplie. */
  computeExtents(
    slots: QrLabelSlot[],
    options: QrLabelPdfOptions = {}
  ): { first: { x: number; y: number; page: number }; last: { x: number; y: number; page: number } | null } {
    const firstSlot = Math.min(48, Math.max(1, options.firstSlot ?? 1));
    const offsetX = options.offsetXMm ?? 0;
    const offsetY = options.offsetYMm ?? 0;
    if (slots.length === 0) {
      const o = this.labelOriginMm(0, 0);
      return { first: { x: o.x + offsetX, y: o.y + offsetY, page: 1 }, last: null };
    }
    const firstIndex = firstSlot - 1;
    const lastIndex = firstIndex + slots.length - 1;
    return {
      first: this.slotToXY(firstIndex, offsetX, offsetY),
      last: this.slotToXY(lastIndex, offsetX, offsetY)
    };
  }

  private slotToXY(
    slotIndex: number,
    offsetX: number,
    offsetY: number
  ): { x: number; y: number; page: number } {
    const page = Math.floor(slotIndex / QrLabelPdfService.SLOTS_PER_PAGE) + 1;
    const onPage = slotIndex % QrLabelPdfService.SLOTS_PER_PAGE;
    const col = onPage % QrLabelPdfService.COLS;
    const row = Math.floor(onPage / QrLabelPdfService.COLS);
    const o = this.labelOriginMm(col, row);
    return { x: o.x + offsetX, y: o.y + offsetY, page };
  }

  /** Ligne 1 = numéro MAS, ligne 2 = « Règle de jeux » (si fourni). */
  private captionLines(value: string): [string, string | null] {
    const parts = value
      .split(/\r?\n/)
      .map((p) => p.trim())
      .filter(Boolean);
    if (parts.length >= 2) {
      return [this.truncateCaption(parts[0], 16), this.truncateCaption(parts[1], 18)];
    }
    const single = this.truncateCaption(parts[0] || '', 16);
    return [single, 'Règle de jeux'];
  }

  private truncateCaption(value: string, maxChars: number): string {
    const t = value.trim();
    if (t.length <= maxChars) {
      return t;
    }
    return `${t.slice(0, Math.max(1, maxChars - 1))}…`;
  }
}
