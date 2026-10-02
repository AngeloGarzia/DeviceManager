import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RegleJeuxOption } from '../../models/models';
import { MasService } from '../../services/mas.service';
import { apiErrorMessage } from '../../shared/api-error';
import { PdfInlineViewerComponent } from '../../shared/pdf-inline-viewer.component';

/**
 * Page dédiée : affichage d'une règle de jeux dans un champ visionneuse (PDF ou image).
 * PDF rendu en canvas (pdf.js) pour mobile.
 */
@Component({
  selector: 'app-regle-jeux-viewer',
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
  templateUrl: './regle-jeux-viewer.component.html',
  styleUrl: './regle-jeux-viewer.component.scss'
})
export class RegleJeuxViewerComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly masService = inject(MasService);

  readonly loading = signal(true);
  readonly docLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly item = signal<RegleJeuxOption | null>(null);
  readonly objectUrl = signal<string | null>(null);
  readonly pdfBlob = signal<Blob | null>(null);

  readonly isImage = computed(() => {
    const it = this.item();
    if (!it) {
      return false;
    }
    const ct = (it.contentType || '').toLowerCase();
    const name = (it.originalName || '').toLowerCase();
    return ct.startsWith('image/') || /\.(png|jpe?g|webp|gif)$/.test(name);
  });

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isFinite(id) || id <= 0) {
      this.error.set('Règle de jeux introuvable.');
      this.loading.set(false);
      return;
    }
    this.load(id);
  }

  ngOnDestroy(): void {
    this.revokeObjectUrl();
  }

  back(): void {
    void this.router.navigate(['/mas/regles-jeux']);
  }

  private load(id: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.masService.getRegleJeux(id).subscribe({
      next: (item) => {
        this.item.set(item);
        this.loading.set(false);
        this.loadDocument(item);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Impossible de charger la règle de jeux.'));
      }
    });
  }

  private loadDocument(item: RegleJeuxOption): void {
    this.docLoading.set(true);
    this.revokeObjectUrl();
    this.pdfBlob.set(null);
    this.masService.downloadRegleJeuxPdf(item.id).subscribe({
      next: (blob) => {
        this.docLoading.set(false);
        if (!blob || blob.size === 0) {
          this.error.set('Document vide ou introuvable — remplacez le fichier.');
          return;
        }
        if (blob.type && blob.type.includes('json')) {
          this.error.set('Document introuvable dans le stockage — remplacez le fichier.');
          return;
        }
        if (!item.contentType && blob.type) {
          this.item.update((cur) => (cur ? { ...cur, contentType: blob.type } : cur));
        }
        const wantsImage = this.isImageContent(item, blob);
        if (wantsImage) {
          const mime = blob.type || item.contentType || 'image/jpeg';
          const typed = blob.type === mime ? blob : new Blob([blob], { type: mime });
          this.objectUrl.set(URL.createObjectURL(typed));
        } else {
          const typed =
            blob.type === 'application/pdf' ? blob : new Blob([blob], { type: 'application/pdf' });
          this.pdfBlob.set(typed);
        }
      },
      error: (err) => {
        this.docLoading.set(false);
        this.error.set(apiErrorMessage(err, 'Document introuvable — remplacez le fichier.'));
      }
    });
  }

  private isImageContent(item: RegleJeuxOption, blob: Blob): boolean {
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
