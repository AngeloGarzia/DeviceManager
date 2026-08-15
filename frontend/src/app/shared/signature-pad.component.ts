import {
  AfterViewInit,
  Component,
  ElementRef,
  ViewChild,
  forwardRef,
  input,
  signal
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/**
 * Zone de signature manuscrite (canvas) exposée comme ControlValueAccessor.
 * Valeur : data URL PNG, ou null si le pad est vide.
 */
@Component({
  selector: 'app-signature-pad',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule],
  template: `
    <div class="sig-wrap">
      <div class="sig-label">{{ label() }}</div>
      <canvas
        #canvas
        class="sig-canvas"
        [attr.width]="width()"
        [attr.height]="height()"
        (pointerdown)="onPointerDown($event)"
        (pointermove)="onPointerMove($event)"
        (pointerup)="onPointerUp($event)"
        (pointerleave)="onPointerUp($event)"
        (pointercancel)="onPointerUp($event)"
      ></canvas>
      <div class="sig-actions">
        <button mat-stroked-button type="button" (click)="clear()" [disabled]="disabled()">
          <mat-icon>clear</mat-icon>
          Effacer
        </button>
        @if (!hasStroke()) {
          <span class="sig-hint">Dessinez la signature dans le cadre</span>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .sig-wrap {
        display: grid;
        gap: 0.4rem;
      }
      .sig-label {
        font-size: 0.875rem;
        font-weight: 600;
        color: #0f172a;
      }
      .sig-canvas {
        width: 100%;
        max-width: 28rem;
        height: auto;
        touch-action: none;
        border: 1px solid #cbd5e1;
        border-radius: 0.65rem;
        background: #fff;
        cursor: crosshair;
      }
      .sig-canvas:disabled,
      :host(.disabled) .sig-canvas {
        opacity: 0.6;
        cursor: not-allowed;
      }
      .sig-actions {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 0.75rem;
      }
      .sig-hint {
        font-size: 0.75rem;
        color: #64748b;
      }
    `
  ],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => SignaturePadComponent),
      multi: true
    }
  ]
})
export class SignaturePadComponent implements ControlValueAccessor, AfterViewInit {
  readonly label = input('Signature');
  readonly width = input(560);
  readonly height = input(160);

  @ViewChild('canvas', { static: true }) canvasRef!: ElementRef<HTMLCanvasElement>;

  readonly hasStroke = signal(false);
  readonly disabled = signal(false);

  private drawing = false;
  private ctx: CanvasRenderingContext2D | null = null;
  private onChange: (value: string | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  ngAfterViewInit(): void {
    const canvas = this.canvasRef.nativeElement;
    this.ctx = canvas.getContext('2d');
    this.resetStyle();
  }

  writeValue(value: string | null): void {
    queueMicrotask(() => {
      if (!value) {
        this.clearCanvas(false);
        return;
      }
      const img = new Image();
      img.onload = () => {
        const canvas = this.canvasRef.nativeElement;
        const ctx = this.ctx;
        if (!ctx) {
          return;
        }
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        this.hasStroke.set(true);
      };
      img.src = value;
    });
  }

  registerOnChange(fn: (value: string | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  onPointerDown(event: PointerEvent): void {
    if (this.disabled() || !this.ctx) {
      return;
    }
    const canvas = this.canvasRef.nativeElement;
    canvas.setPointerCapture(event.pointerId);
    this.drawing = true;
    const { x, y } = this.coords(event);
    this.ctx.beginPath();
    this.ctx.moveTo(x, y);
    this.onTouched();
  }

  onPointerMove(event: PointerEvent): void {
    if (!this.drawing || !this.ctx || this.disabled()) {
      return;
    }
    const { x, y } = this.coords(event);
    this.ctx.lineTo(x, y);
    this.ctx.stroke();
    this.hasStroke.set(true);
  }

  onPointerUp(event: PointerEvent): void {
    if (!this.drawing) {
      return;
    }
    this.drawing = false;
    try {
      this.canvasRef.nativeElement.releasePointerCapture(event.pointerId);
    } catch {
      /* ignore */
    }
    this.emit();
  }

  clear(): void {
    this.clearCanvas(true);
  }

  private clearCanvas(emit: boolean): void {
    const canvas = this.canvasRef.nativeElement;
    const ctx = this.ctx;
    if (ctx) {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      this.resetStyle();
    }
    this.hasStroke.set(false);
    if (emit) {
      this.onChange(null);
      this.onTouched();
    }
  }

  private emit(): void {
    if (!this.hasStroke()) {
      this.onChange(null);
      return;
    }
    this.onChange(this.canvasRef.nativeElement.toDataURL('image/png'));
  }

  private coords(event: PointerEvent): { x: number; y: number } {
    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;
    return {
      x: (event.clientX - rect.left) * scaleX,
      y: (event.clientY - rect.top) * scaleY
    };
  }

  private resetStyle(): void {
    if (!this.ctx) {
      return;
    }
    this.ctx.lineWidth = 2.2;
    this.ctx.lineCap = 'round';
    this.ctx.lineJoin = 'round';
    this.ctx.strokeStyle = '#0f172a';
  }
}
