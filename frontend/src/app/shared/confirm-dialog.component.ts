import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [MatButtonModule, MatDialogModule],
  template: `
    @if (open) {
      <div class="fixed inset-0 z-[100] flex items-end justify-center bg-slate-900/40 p-4 sm:items-center" (click)="cancel.emit()">
        <div
          class="w-full max-w-md rounded-2xl border border-line bg-white p-5 shadow-soft"
          role="dialog"
          aria-modal="true"
          (click)="$event.stopPropagation()"
        >
          <h2 class="m-0 text-lg font-semibold text-ink">{{ title }}</h2>
          <p class="mt-2 text-sm leading-relaxed text-ink-soft">{{ message }}</p>
          <div class="mt-5 grid grid-cols-2 gap-3">
            <button mat-stroked-button type="button" (click)="cancel.emit()">{{ cancelLabel }}</button>
            <button mat-flat-button [color]="confirmColor" type="button" (click)="confirm.emit()">
              {{ confirmLabel }}
            </button>
          </div>
        </div>
      </div>
    }
  `
})
export class ConfirmDialogComponent {
  @Input() open = false;
  @Input() title = 'Confirmer';
  @Input() message = 'Cette action est irréversible.';
  @Input() confirmLabel = 'Supprimer';
  @Input() cancelLabel = 'Annuler';
  @Input() confirmColor: 'primary' | 'accent' | 'warn' = 'warn';
  @Output() confirm = new EventEmitter<void>();
  @Output() cancel = new EventEmitter<void>();
}
