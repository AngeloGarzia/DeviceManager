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
 * Affichage initial = largeur adaptée à l’écran ; pinch / molette pour zoomer ensuite.
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
        gap: 0.5rem;
        padding: 0.5rem;
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

  @ViewChild('host', { static: true }) hostRef!: ElementRef<HTMLDivElement>;
  @ViewChild('pages', { static: true }) pagesRef!: ElementRef<HTMLDivElement>;

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly transformCss = signal('translate(0px, 0px) scale(1)');

  private pdfDoc: PDFDocumentProxy | null = null;
  private renderToken = 0;
  private viewReady = false;
  private lastFitWidth = 0;
  private resizeObserver: ResizeObserver | null = null;
  private resizeTimer: number | null = null;

  /** 1 = largeur écran ; >1 = zoom utilisateur. */
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
    const el = this.hostRef?.nativeElement;
    if (typeof ResizeObserver !== 'undefined' && el) {
      this.resizeObserver = new ResizeObserver(() => this.onHostResized());
      this.resizeObserver.observe(el);
    }
    void this.render(true);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['source'] && this.viewReady) {
      this.renderToken++;
      void this.pdfDoc?.destroy();
      this.pdfDoc = null;
      this.lastFitWidth = 0;
      this.resetView();
      void this.render(true);
    }
  }

  ngOnDestroy(): void {
    this.renderToken++;
    if (this.resizeTimer != null) {
      window.clearTimeout(this.resizeTimer);
    }
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
    void this.pdfDoc?.destroy();
    this.pdfDoc = null;
  }

  @HostListener('wheel', ['$event'])
  onWheel(event: WheelEvent): void {
    if (!this.zoomable || this.loading()) {
      return;
    }
    event.preventDefault();
    const host = this.hostRef?.nativeElement;
    const rect = host?.getBoundingClientRect() ?? { left: 0, top: 0 };
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
    const hostEl = this.hostRef?.nativeElement ?? null;
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

  private onHostResized(): void {
    if (!this.source || this.loading()) {
      return;
    }
    const width = this.measureFitWidth();
    if (width < 80) {
      return;
    }
    // Re-fit seulement si la largeur change vraiment (rotation / plein écran).
    if (Math.abs(width - this.lastFitWidth) < 8) {
      return;
    }
    if (this.resizeTimer != null) {
      window.clearTimeout(this.resizeTimer);
    }
    this.resizeTimer = window.setTimeout(() => {
      this.resizeTimer = null;
      this.resetView();
      void this.render(false);
    }, 120);
  }

  private zoomAt(mx: number, my: number, nextScale: number): void {
    // 1 = ajusté à l’écran ; zoom uniquement vers le haut.
    const clamped = Math.min(5, Math.max(1, nextScale));
    const ratio = clamped / this.scale;
    this.tx = mx - (mx - this.tx) * ratio;
    this.ty = my - (my - this.ty) * ratio;
    this.scale = clamped;
    if (clamped === 1) {
      this.tx = 0;
      this.ty = 0;
    }
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

  /** Largeur utile pour coller la page au viewport (padding inclus). */
  private measureFitWidth(): number {
    const shell = this.hostRef?.nativeElement;
    const pad = 16; // .pdf-inline__pages padding 0.5rem * 2
    const raw = shell?.clientWidth || window.innerWidth || 360;
    return Math.max(200, raw - pad);
  }

  private async render(showSpinner: boolean): Promise<void> {
    const token = ++this.renderToken;
    const host = this.pagesRef?.nativeElement;
    if (!host) {
      return;
    }
    host.innerHTML = '';

    if (!this.source) {
      void this.pdfDoc?.destroy();
      this.pdfDoc = null;
      this.loading.set(false);
      this.error.set(null);
      return;
    }

    if (showSpinner) {
      this.loading.set(true);
    }
    this.error.set(null);

    try {
      if (!this.pdfDoc) {
        const data =
          this.source instanceof Blob
            ? new Uint8Array(await this.source.arrayBuffer())
            : new Uint8Array(this.source);
        const task = getDocument({ data, disableAutoFetch: true, disableStream: true });
        const pdf = await task.promise;
        if (token !== this.renderToken) {
          void pdf.destroy();
          return;
        }
        this.pdfDoc = pdf;
      }

      const pdf = this.pdfDoc;
      if (!pdf) {
        return;
      }
      // Attendre un frame si le conteneur n’a pas encore sa taille (plein écran).
      let fitWidth = this.measureFitWidth();
      if (fitWidth < 220 && typeof window !== 'undefined') {
        await new Promise<void>((r) => requestAnimationFrame(() => r()));
        if (token !== this.renderToken) {
          return;
        }
        fitWidth = this.measureFitWidth();
      }
      this.lastFitWidth = fitWidth;
      const outputScale = Math.min(3, Math.max(1, window.devicePixelRatio || 1));

      for (let pageNum = 1; pageNum <= pdf.numPages; pageNum++) {
        if (token !== this.renderToken) {
          return;
        }
        const page = await pdf.getPage(pageNum);
        const unscaled = page.getViewport({ scale: 1 });
        // Largeur = écran ; le user zoome ensuite via le transform CSS.
        const pageScale = fitWidth / unscaled.width;
        const viewport = page.getViewport({ scale: pageScale });

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
