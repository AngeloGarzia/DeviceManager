import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { getDocument, GlobalWorkerOptions, type PDFDocumentProxy } from 'pdfjs-dist';

// Worker servi en asset (CSP worker-src 'self').
GlobalWorkerOptions.workerSrc = '/pdf.worker.min.mjs';

/**
 * Affiche un PDF page par page en canvas (compatible mobile / iOS, sans iframe).
 * Rendu haute densité (devicePixelRatio) pour un texte net sur mobile.
 */
@Component({
  selector: 'app-pdf-inline-viewer',
  standalone: true,
  imports: [CommonModule, MatProgressSpinnerModule],
  template: `
    <div class="pdf-inline" #host>
      @if (loading()) {
        <div class="pdf-inline__loading">
          <mat-spinner diameter="36"></mat-spinner>
          <span>Affichage du PDF…</span>
        </div>
      } @else if (error()) {
        <p class="pdf-inline__error">{{ error() }}</p>
      }
      <div class="pdf-inline__pages" #pages></div>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        width: 100%;
        height: 100%;
        min-height: inherit;
      }
      .pdf-inline {
        width: 100%;
        height: 100%;
        min-height: inherit;
        overflow: auto;
        -webkit-overflow-scrolling: touch;
        touch-action: pan-x pan-y;
        background: #111827;
      }
      .pdf-inline__pages {
        display: grid;
        gap: 0.75rem;
        padding: 0.75rem;
        justify-items: center;
        min-width: min-content;
      }
      .pdf-inline__pages canvas {
        display: block;
        width: auto;
        max-width: none;
        height: auto;
        background: #fff;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.35);
      }
      .pdf-inline__loading,
      .pdf-inline__error {
        display: grid;
        justify-items: center;
        gap: 0.75rem;
        padding: 2rem 1rem;
        color: #cbd5e1;
        font-size: 0.9rem;
        text-align: center;
      }
      .pdf-inline__error {
        color: #fecaca;
      }
    `
  ]
})
export class PdfInlineViewerComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input({ required: true }) source: Blob | ArrayBuffer | null = null;

  @ViewChild('pages', { static: true }) pagesRef!: ElementRef<HTMLDivElement>;

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  private pdfDoc: PDFDocumentProxy | null = null;
  private renderToken = 0;
  private viewReady = false;

  ngAfterViewInit(): void {
    this.viewReady = true;
    void this.render();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['source'] && this.viewReady) {
      void this.render();
    }
  }

  ngOnDestroy(): void {
    this.renderToken++;
    void this.pdfDoc?.destroy();
    this.pdfDoc = null;
  }

  private async render(): Promise<void> {
    const token = ++this.renderToken;
    const host = this.pagesRef?.nativeElement;
    if (!host) {
      return;
    }
    host.innerHTML = '';
    void this.pdfDoc?.destroy();
    this.pdfDoc = null;

    if (!this.source) {
      this.loading.set(false);
      this.error.set(null);
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    try {
      const data =
        this.source instanceof Blob ? new Uint8Array(await this.source.arrayBuffer()) : new Uint8Array(this.source);
      const task = getDocument({ data, disableAutoFetch: true, disableStream: true });
      const pdf = await task.promise;
      if (token !== this.renderToken) {
        void pdf.destroy();
        return;
      }
      this.pdfDoc = pdf;

      const containerWidth = Math.max(
        280,
        (host.parentElement?.clientWidth || host.clientWidth || window.innerWidth || 360) - 24
      );
      // Cap DPR à 3 pour limiter mémoire, mais assez pour écrans Retina.
      const outputScale = Math.min(3, Math.max(1, window.devicePixelRatio || 1));

      for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
        if (token !== this.renderToken) {
          return;
        }
        const page = await pdf.getPage(pageNum);
        const unscaled = page.getViewport({ scale: 1 });
        // Largeur CSS cible : au moins le conteneur ; sur mobile, +35 % pour lisibilité (scroll horizontal).
        const targetCssWidth =
          containerWidth < 640
            ? Math.min(unscaled.width * 2.2, Math.max(containerWidth, Math.round(containerWidth * 1.35)))
            : containerWidth;
        const scale = Math.min(2.5, targetCssWidth / unscaled.width);
        const viewport = page.getViewport({ scale });

        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d', { alpha: false });
        if (!ctx) {
          continue;
        }
        canvas.width = Math.floor(viewport.width * outputScale);
        canvas.height = Math.floor(viewport.height * outputScale);
        canvas.style.width = `${Math.floor(viewport.width)}px`;
        canvas.style.height = `${Math.floor(viewport.height)}px`;

        const transform = outputScale !== 1 ? [outputScale, 0, 0, outputScale, 0, 0] : undefined;
        host.appendChild(canvas);
        await page.render({ canvasContext: ctx, viewport, transform }).promise;
      }
      this.loading.set(false);
    } catch (e) {
      if (token !== this.renderToken) {
        return;
      }
      this.loading.set(false);
      this.error.set('Impossible d’afficher le PDF sur cet appareil.');
      console.error(e);
    }
  }
}
