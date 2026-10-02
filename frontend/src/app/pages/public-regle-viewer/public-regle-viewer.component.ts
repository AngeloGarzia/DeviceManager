import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  PublicMasReglesService,
  PublicRegleJeux
} from '../../services/public-mas-regles.service';
import { PdfInlineViewerComponent } from '../../shared/pdf-inline-viewer.component';
import { apiErrorMessage } from '../../shared/api-error';

/**
 * Visionneuse publique PDF/image d'une règle (jeton QR, lecture seule).
 * PDF rendu en canvas (pdf.js) pour fonctionner aussi sur mobile.
 */
@Component({
  selector: 'app-public-regle-viewer',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    PdfInlineViewerComponent
  ],
  templateUrl: './public-regle-viewer.component.html',
  styleUrl: './public-regle-viewer.component.scss'
})
export class PublicRegleViewerComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly publicApi = inject(PublicMasReglesService);

  readonly loading = signal(true);
  readonly docLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly token = signal('');
  readonly regle = signal<PublicRegleJeux | null>(null);
  readonly masNumero = signal('');
  readonly objectUrl = signal<string | null>(null);
  readonly pdfBlob = signal<Blob | null>(null);

  readonly isImage = computed(() => {
    const it = this.regle();
    if (!it) {
      return false;
    }
    const ct = (it.contentType || '').toLowerCase();
    const name = (it.originalName || '').toLowerCase();
    return ct.startsWith('image/') || /\.(png|jpe?g|webp|gif)$/.test(name);
  });

  ngOnInit(): void {
    const token = (this.route.snapshot.paramMap.get('token') || '').trim();
    const regleId = Number(this.route.snapshot.paramMap.get('regleId'));
    this.token.set(token);
    if (!token || !Number.isFinite(regleId) || regleId <= 0) {
      this.loading.set(false);
      this.error.set('Lien invalide.');
      return;
    }
    this.publicApi.byToken(token).subscribe({
      next: (data) => {
        this.masNumero.set(data.masNumero);
        const found = (data.regles || []).find((r) => r.id === regleId) ?? null;
        this.regle.set(found);
        this.loading.set(false);
        if (!found) {
          this.error.set('Règle introuvable pour ce lien.');
          return;
        }
        this.loadDocument(token, regleId, found);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Lien invalide ou règles indisponibles.'));
      }
    });
  }

  ngOnDestroy(): void {
    this.revokeObjectUrl();
  }

  private loadDocument(token: string, regleId: number, item: PublicRegleJeux): void {
    this.docLoading.set(true);
    this.revokeObjectUrl();
    this.pdfBlob.set(null);
    this.publicApi.downloadFile(token, regleId).subscribe({
      next: (blob) => {
        this.docLoading.set(false);
        if (!blob || blob.size === 0 || (blob.type && blob.type.includes('json'))) {
          this.error.set('Document indisponible.');
          return;
        }
        const wantsImage = this.isImageContent(item, blob);
        const mime = wantsImage
          ? firstNonBlank(blob.type, item.contentType, 'image/jpeg')!
          : 'application/pdf';
        if (!item.contentType) {
          this.regle.update((cur) => (cur ? { ...cur, contentType: mime } : cur));
        }
        const typed = blob.type === mime ? blob : new Blob([blob], { type: mime });
        if (wantsImage) {
          this.objectUrl.set(URL.createObjectURL(typed));
        } else {
          this.pdfBlob.set(typed);
        }
      },
      error: (err) => {
        this.docLoading.set(false);
        this.error.set(apiErrorMessage(err, 'Document indisponible.'));
      }
    });
  }

  private isImageContent(item: PublicRegleJeux, blob: Blob): boolean {
    const ct = (item.contentType || blob.type || '').toLowerCase();
    const name = (item.originalName || '').toLowerCase();
    return ct.startsWith('image/') || /\.(png|jpe?g|webp|gif)$/.test(name);
  }

  private revokeObjectUrl(): void {
    const url = this.objectUrl();
    if (url) {
      URL.revokeObjectURL(url);
      this.objectUrl.set(null);
    }
  }
}

function firstNonBlank(...values: Array<string | null | undefined>): string | null {
  for (const v of values) {
    if (v && v.trim()) {
      return v.trim();
    }
  }
  return null;
}
