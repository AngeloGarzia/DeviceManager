import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [MatButtonModule, MatDialogModule],
  template: `
    @if (open) {
      <div
        class="fixed inset-0 z-[100] flex items-end justify-center bg-slate-900/40 p-4 sm:items-center"
        role="button"
        tabindex="0"
        aria-label="Fermer la boîte de dialogue"
        (click)="dismissed.emit()"
        (keydown.enter)="dismissed.emit()"
        (keydown.space)="dismissed.emit()"
      >
        <div
          class="w-full max-w-md rounded-2xl border border-line bg-white p-5 shadow-soft"
          role="dialog"
          aria-modal="true"
          tabindex="-1"
          (click)="$event.stopPropagation()"
          (keydown)="$event.stopPropagation()"
        >
          <h2 class="m-0 text-lg font-semibold text-ink">{{ title }}</h2>
          <p class="mt-2 text-sm leading-relaxed text-ink-soft">{{ message }}</p>
          <div class="mt-5 grid grid-cols-2 gap-3">
            <button mat-stroked-button type="button" (click)="dismissed.emit()">{{ cancelLabel }}</button>
            <button mat-flat-button [color]="confirmColor" type="button" (click)="confirmed.emit()">
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
  @Output() confirmed = new EventEmitter<void>();
  @Output() dismissed = new EventEmitter<void>();
}
