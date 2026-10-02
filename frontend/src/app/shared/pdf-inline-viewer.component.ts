import {
  AfterViewInit,
  Component,
  ElementRef,
  HostListener,
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
 * Haute densité (devicePixelRatio) + pinch-to-zoom / pan tactile.
 */
@Component({
  selector: 'app-pdf-inline-viewer',
  standalone: true,
  imports: [CommonModule, MatProgressSpinnerModule],
  template: `
    <div
      class="pdf-inline"
      #host
      (touchstart)="onTouchStart($event)"
      (touchmove)="onTouchMove($event)"
      (touchend)="onTouchEnd($event)"
      (touchcancel)="onTouchEnd($event)"
    >
      @if (loading()) {
        <div class="pdf-inline__loading">
          <mat-spinner diameter="36"></mat-spinner>
          <span>Affichage du PDF…</span>
        </div>
      } @else if (error()) {
        <p class="pdf-inline__error">{{ error() }}</p>
      }
      <div
        class="pdf-inline__pages"
        #pages
        [style.transform]="transformCss()"
        [style.transformOrigin]="'0 0'"
      ></div>
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
        overflow: hidden;
        touch-action: none;
        background: #111827;
        position: relative;
        user-select: none;
        -webkit-user-select: none;
      }
      .pdf-inline__pages {
        display: grid;
        gap: 0.75rem;
        padding: 0.75rem;
        justify-items: center;
        min-width: min-content;
        will-change: transform;
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
        position: absolute;
        inset: 0;
        z-index: 2;
        display: grid;
        place-content: center;
        justify-items: center;
        gap: 0.75rem;
        padding: 2rem 1rem;
        color: #cbd5e1;
        font-size: 0.9rem;
        text-align: center;
        background: #111827;
      }
      .pdf-inline__error {
        color: #fecaca;
      }
    `
  ]
})
export class PdfInlineViewerComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input({ required: true }) source: Blob | ArrayBuffer | null = null;
  /** Active le pinch-zoom / pan (recommandé mobile plein écran). */
  @Input() zoomable = true;

  @ViewChild('pages', { static: true }) pagesRef!: ElementRef<HTMLDivElement>;

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly transformCss = signal('translate(0px, 0px) scale(1)');

  private pdfDoc: PDFDocumentProxy | null = null;
  private renderToken = 0;
  private viewReady = false;

  private scale = 1;
  private tx = 0;
  private ty = 0;
  private pointers = new Map<number, { x: number; y: number }>();
  private pinchStartDist = 0;
  private pinchStartScale = 1;
  private panStartX = 0;
  private panStartY = 0;
  private panOriginTx = 0;
  private panOriginTy = 0;

  ngAfterViewInit(): void {
    this.viewReady = true;
    void this.render();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['source'] && this.viewReady) {
      this.resetView();
      void this.render();
    }
  }

  ngOnDestroy(): void {
    this.renderToken++;
    void this.pdfDoc?.destroy();
    this.pdfDoc = null;
  }

  @HostListener('wheel', ['$event'])
  onWheel(event: WheelEvent): void {
    if (!this.zoomable || this.loading()) {
      return;
    }
    event.preventDefault();
    const host = event.currentTarget as HTMLElement | null;
    const rect = (host ?? (event.target as HTMLElement)).getBoundingClientRect?.()
      ?? { left: 0, top: 0 };
    const mx = event.clientX - rect.left;
    const my = event.clientY - rect.top;
    const factor = event.deltaY < 0 ? 1.08 : 1 / 1.08;
    this.zoomAt(mx, my, this.scale * factor);
  }

  onTouchStart(event: TouchEvent): void {
    if (!this.zoomable) {
      return;
    }
    for (let i = 0; i < event.changedTouches.length; i++) {
      const t = event.changedTouches.item(i)!;
      this.pointers.set(t.identifier, { x: t.clientX, y: t.clientY });
    }
    if (this.pointers.size === 1) {
      const p = [...this.pointers.values()][0];
      this.panStartX = p.x;
      this.panStartY = p.y;
      this.panOriginTx = this.tx;
      this.panOriginTy = this.ty;
    } else if (this.pointers.size >= 2) {
      const [a, b] = [...this.pointers.values()];
      this.pinchStartDist = Math.hypot(b.x - a.x, b.y - a.y) || 1;
      this.pinchStartScale = this.scale;
    }
  }

  onTouchMove(event: TouchEvent): void {
    if (!this.zoomable || this.pointers.size === 0) {
      return;
    }
    event.preventDefault();
    for (let i = 0; i < event.changedTouches.length; i++) {
      const t = event.changedTouches.item(i)!;
      if (this.pointers.has(t.identifier)) {
        this.pointers.set(t.identifier, { x: t.clientX, y: t.clientY });
      }
    }
    const hostEl = (event.currentTarget as HTMLElement) ?? null;
    const rect = hostEl?.getBoundingClientRect();

    if (this.pointers.size >= 2 && rect) {
      const [a, b] = [...this.pointers.values()];
      const dist = Math.hypot(b.x - a.x, b.y - a.y) || 1;
      const next = this.pinchStartScale * (dist / this.pinchStartDist);
      const mx = (a.x + b.x) / 2 - rect.left;
      const my = (a.y + b.y) / 2 - rect.top;
      this.zoomAt(mx, my, next);
    } else if (this.pointers.size === 1) {
      const p = [...this.pointers.values()][0];
      this.tx = this.panOriginTx + (p.x - this.panStartX);
      this.ty = this.panOriginTy + (p.y - this.panStartY);
      this.applyTransform();
    }
  }

  onTouchEnd(event: TouchEvent): void {
    for (let i = 0; i < event.changedTouches.length; i++) {
      const t = event.changedTouches.item(i)!;
      this.pointers.delete(t.identifier);
    }
    if (this.pointers.size === 1) {
      const p = [...this.pointers.values()][0];
      this.panStartX = p.x;
      this.panStartY = p.y;
      this.panOriginTx = this.tx;
      this.panOriginTy = this.ty;
    } else if (this.pointers.size >= 2) {
      const [a, b] = [...this.pointers.values()];
      this.pinchStartDist = Math.hypot(b.x - a.x, b.y - a.y) || 1;
      this.pinchStartScale = this.scale;
    }
  }

  private zoomAt(mx: number, my: number, nextScale: number): void {
    const clamped = Math.min(5, Math.max(1, nextScale));
    const ratio = clamped / this.scale;
    this.tx = mx - (mx - this.tx) * ratio;
    this.ty = my - (my - this.ty) * ratio;
    this.scale = clamped;
    this.applyTransform();
  }

  private resetView(): void {
    this.scale = 1;
    this.tx = 0;
    this.ty = 0;
    this.pointers.clear();
    this.applyTransform();
  }

  private applyTransform(): void {
    this.transformCss.set(`translate(${this.tx}px, ${this.ty}px) scale(${this.scale})`);
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

      const shell = host.parentElement;
      const containerWidth = Math.max(
        280,
        (shell?.clientWidth || host.clientWidth || window.innerWidth || 360) - 16
      );
      const outputScale = Math.min(3, Math.max(1, window.devicePixelRatio || 1));

      for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
        if (token !== this.renderToken) {
          return;
        }
        const page = await pdf.getPage(pageNum);
        const unscaled = page.getViewport({ scale: 1 });
        // Plein écran mobile : largeur ≈ écran, net via DPR ; zoom utilisateur ensuite.
        const targetCssWidth = Math.min(unscaled.width * 2.4, containerWidth);
        const scale = Math.min(2.5, Math.max(0.8, targetCssWidth / unscaled.width));
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
