import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import {
  PublicMasReglesService,
  PublicRegleJeux
} from '../../services/public-mas-regles.service';
import { apiErrorMessage } from '../../shared/api-error';

/**
 * Visionneuse publique PDF/image d'une règle (jeton QR, lecture seule).
 */
@Component({
  selector: 'app-public-regle-viewer',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './public-regle-viewer.component.html',
  styleUrl: './public-regle-viewer.component.scss'
})
export class PublicRegleViewerComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly publicApi = inject(PublicMasReglesService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly loading = signal(true);
  readonly docLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly token = signal('');
  readonly regle = signal<PublicRegleJeux | null>(null);
  readonly masNumero = signal('');
  readonly objectUrl = signal<string | null>(null);

  readonly isImage = computed(() => {
    const it = this.regle();
    if (!it) {
      return false;
    }
    const ct = (it.contentType || '').toLowerCase();
    const name = (it.originalName || '').toLowerCase();
    return ct.startsWith('image/') || /\.(png|jpe?g|webp|gif)$/.test(name);
  });

  readonly safePdfUrl = computed((): SafeResourceUrl | null => {
    const url = this.objectUrl();
    if (!url || this.isImage()) {
      return null;
    }
    return this.sanitizer.bypassSecurityTrustResourceUrl(url);
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
    this.publicApi.downloadFile(token, regleId).subscribe({
      next: (blob) => {
        this.docLoading.set(false);
        if (!blob || blob.size === 0 || (blob.type && blob.type.includes('json'))) {
          this.error.set('Document indisponible.');
          return;
        }
        if (!item.contentType && blob.type) {
          this.regle.update((cur) => (cur ? { ...cur, contentType: blob.type } : cur));
        }
        this.objectUrl.set(URL.createObjectURL(blob));
      },
      error: (err) => {
        this.docLoading.set(false);
        this.error.set(apiErrorMessage(err, 'Document indisponible.'));
      }
    });
  }

  private revokeObjectUrl(): void {
    const url = this.objectUrl();
    if (url) {
      URL.revokeObjectURL(url);
      this.objectUrl.set(null);
    }
  }
}
