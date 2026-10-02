import { Component, OnInit, computed, inject, signal } from '@angular/core';
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
 * Utilise l'URL API directe (mobile-friendly) + bouton Ouvrir.
 */
@Component({
  selector: 'app-public-regle-viewer',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './public-regle-viewer.component.html',
  styleUrl: './public-regle-viewer.component.scss'
})
export class PublicRegleViewerComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly publicApi = inject(PublicMasReglesService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly token = signal('');
  readonly regle = signal<PublicRegleJeux | null>(null);
  readonly masNumero = signal('');

  readonly isImage = computed(() => {
    const it = this.regle();
    if (!it) {
      return false;
    }
    const ct = (it.contentType || '').toLowerCase();
    const name = (it.originalName || '').toLowerCase();
    return ct.startsWith('image/') || /\.(png|jpe?g|webp|gif)$/.test(name);
  });

  readonly fileUrl = computed((): string | null => {
    const token = this.token();
    const regle = this.regle();
    if (!token || !regle) {
      return null;
    }
    return this.publicApi.fileUrl(token, regle.id);
  });

  readonly safeEmbedUrl = computed((): SafeResourceUrl | null => {
    const url = this.fileUrl();
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
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Lien invalide ou règles indisponibles.'));
      }
    });
  }

  downloadName(): string {
    const r = this.regle();
    return r?.originalName || r?.label || 'regle-jeux.pdf';
  }
}
