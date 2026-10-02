import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { Mas } from '../../models/models';
import { MasService } from '../../services/mas.service';
import { QrLabelPdfService, QrLabelSlot } from '../../services/qr-label-pdf.service';
import { apiErrorMessage } from '../../shared/api-error';

interface LabelRow {
  mas: Mas;
  selected: boolean;
  repeats: number;
}

/**
 * Impression planche A4 d’étiquettes QR MAS → règles de jeux (accès public).
 */
@Component({
  selector: 'app-mas-qr-labels',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './mas-qr-labels.component.html',
  styleUrl: './mas-qr-labels.component.scss'
})
export class MasQrLabelsComponent implements OnInit {
  private readonly masService = inject(MasService);
  private readonly qrPdf = inject(QrLabelPdfService);

  readonly loading = signal(false);
  readonly generating = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);
  readonly rows = signal<LabelRow[]>([]);

  firstSlot = 1;
  offsetXMm = 0;
  offsetYMm = 0;
  drawOutlines = false;

  readonly selectedCount = computed(() =>
    this.rows().reduce((n, r) => n + (r.selected ? Math.max(1, r.repeats || 1) : 0), 0)
  );

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.masService.list().subscribe({
      next: (list) => {
        const sorted = [...list].sort((a, b) =>
          a.numero.localeCompare(b.numero, 'fr', { sensitivity: 'base' })
        );
        this.rows.set(
          sorted.map((mas) => ({
            mas,
            selected: false,
            repeats: 1
          }))
        );
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Impossible de charger les MAS.'));
      }
    });
  }

  toggleAll(checked: boolean): void {
    this.rows.update((list) => list.map((r) => ({ ...r, selected: checked })));
  }

  setSelected(index: number, checked: boolean): void {
    this.rows.update((list) =>
      list.map((r, i) => (i === index ? { ...r, selected: checked } : r))
    );
  }

  setRepeats(index: number, value: number | string): void {
    const n = Math.max(1, Math.min(48, Number(value) || 1));
    this.rows.update((list) => list.map((r, i) => (i === index ? { ...r, repeats: n } : r)));
  }

  async generatePdf(): Promise<void> {
    this.error.set(null);
    this.success.set(null);
    const chosen = this.rows().filter((r) => r.selected);
    if (chosen.length === 0) {
      this.error.set('Sélectionnez au moins une MAS.');
      return;
    }

    this.generating.set(true);
    const tokenCalls = chosen.map((row) =>
      this.masService.ensurePublicAccessToken(row.mas.id).pipe(
        map((res) => ({ row, token: res.token })),
        catchError((err) => {
          this.error.set(
            apiErrorMessage(err, `Jeton public impossible pour ${row.mas.numero}.`)
          );
          return of(null);
        })
      )
    );

    forkJoin(tokenCalls).subscribe({
      next: async (results) => {
        if (results.some((r) => r == null)) {
          this.generating.set(false);
          return;
        }
        const slots: QrLabelSlot[] = [];
        for (const res of results) {
          if (!res) {
            continue;
          }
          const url = this.qrPdf.buildMasPublicRulesUrl(res.token);
          const caption = `${res.row.mas.numero}\nRègle de jeux`;
          const times = Math.max(1, res.row.repeats || 1);
          for (let i = 0; i < times; i++) {
            slots.push({ url, caption });
          }
        }
        try {
          const extents = await this.qrPdf.generateAndDownload(slots, {
            firstSlot: this.firstSlot,
            offsetXMm: this.offsetXMm,
            offsetYMm: this.offsetYMm,
            drawOutlines: this.drawOutlines
          });
          const last = extents.last;
          this.success.set(
            `PDF généré — ${slots.length} étiquette(s). ` +
              `1ʳᵉ: (${extents.first.x.toFixed(1)}, ${extents.first.y.toFixed(1)}) mm p.${extents.first.page}` +
              (last
                ? ` · dernière: (${last.x.toFixed(1)}, ${last.y.toFixed(1)}) mm p.${last.page}`
                : '')
          );
        } catch (e) {
          this.error.set(e instanceof Error ? e.message : 'Génération PDF impossible.');
        } finally {
          this.generating.set(false);
        }
      },
      error: (err) => {
        this.generating.set(false);
        this.error.set(apiErrorMessage(err, 'Génération PDF impossible.'));
      }
    });
  }
}
