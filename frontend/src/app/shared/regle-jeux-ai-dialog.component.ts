import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

export interface RegleJeuxAiDialogConfirm {
  label: string;
  description: string | null;
}

/**
 * Modale de validation des informations extraites par l'IA d'un PDF de règle de jeux.
 */
@Component({
  selector: 'app-regle-jeux-ai-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './regle-jeux-ai-dialog.component.html'
})
export class RegleJeuxAiDialogComponent {
  private readonly fb = inject(FormBuilder);

  @Input() open = false;
  @Input() scanning = false;
  @Input() saving = false;
  @Input() fileName: string | null = null;
  @Input() scanNotes: string | null = null;
  @Output() dismissed = new EventEmitter<void>();
  @Output() confirmed = new EventEmitter<RegleJeuxAiDialogConfirm>();

  readonly form = this.fb.nonNullable.group({
    label: ['', [Validators.required, Validators.maxLength(200)]],
    description: ['', Validators.maxLength(500)]
  });

  private lastSuggestedKey = '';

  @Input()
  set suggested(value: { label?: string | null; description?: string | null } | null) {
    if (!value) {
      this.lastSuggestedKey = '';
      return;
    }
    const key = `${value.label ?? ''}|${value.description ?? ''}`;
    if (key === this.lastSuggestedKey) {
      return;
    }
    this.lastSuggestedKey = key;
    this.form.patchValue({
      label: value.label?.trim() || '',
      description: value.description?.trim() || ''
    });
  }

  submit(): void {
    if (this.scanning || this.saving || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    this.confirmed.emit({
      label: raw.label.trim(),
      description: raw.description?.trim() || null
    });
  }
}
