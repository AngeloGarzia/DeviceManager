import { Component, computed, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TodoItem } from '../models/models';
import { TodoService } from '../services/todo.service';
import { AuthService } from '../services/auth.service';
import { SignaturePadComponent } from './signature-pad.component';
import { apiErrorMessage } from './api-error';

/**
 * Modale rapide Accueil : détail tâche + Démarrer / Terminer (signature).
 */
@Component({
  selector: 'app-todo-quick-modal',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    SignaturePadComponent
  ],
  templateUrl: './todo-quick-modal.component.html',
  styleUrl: './todo-quick-modal.component.scss'
})
export class TodoQuickModalComponent {
  private readonly todoService = inject(TodoService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly todo = input.required<TodoItem>();
  readonly closed = output<void>();
  readonly updated = output<TodoItem>();

  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly closingMode = signal(false);

  readonly current = signal<TodoItem | null>(null);

  readonly item = computed(() => this.current() ?? this.todo());

  readonly closeForm = this.fb.nonNullable.group({
    signataireClotureNom: ['', [Validators.required, Validators.maxLength(120)]],
    commentaireCloture: ['', Validators.maxLength(2000)],
    signatureCloture: [null as string | null, Validators.required]
  });

  severityLabel(severity?: string | null): string {
    switch ((severity || '').toUpperCase()) {
      case 'HIGH':
        return 'Urgent';
      case 'MEDIUM':
        return 'Normal';
      case 'LOW':
        return 'Faible';
      default:
        return 'Normal';
    }
  }

  statutLabel(statut?: string | null): string {
    switch (statut) {
      case 'OPEN':
        return 'Ouverte';
      case 'IN_PROGRESS':
        return 'En cours';
      case 'DONE':
        return 'Clôturée';
      case 'CANCELLED':
        return 'Annulée';
      default:
        return statut || '—';
    }
  }

  formatDate(value?: string | null): string {
    if (!value) {
      return '—';
    }
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) {
      return value;
    }
    return d.toLocaleString('fr-FR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  photoUrl(url?: string | null): string {
    return this.todoService.resolvePhotoUrl(url);
  }

  close(): void {
    if (this.saving()) {
      return;
    }
    this.closed.emit();
  }

  onBackdropClick(): void {
    this.close();
  }

  startClosing(): void {
    this.error.set(null);
    this.closeForm.reset({
      signataireClotureNom: this.auth.displayName() || this.auth.username() || '',
      commentaireCloture: '',
      signatureCloture: null
    });
    this.closingMode.set(true);
  }

  cancelClosing(): void {
    this.closingMode.set(false);
    this.error.set(null);
  }

  setStatus(statut: 'OPEN' | 'IN_PROGRESS'): void {
    const t = this.item();
    this.saving.set(true);
    this.error.set(null);
    this.todoService.updateStatus(t.id, statut).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.current.set(updated);
        this.updated.emit(updated);
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Mise à jour du statut impossible.'));
      }
    });
  }

  submitClose(): void {
    if (this.closeForm.invalid) {
      this.closeForm.markAllAsTouched();
      this.error.set('Signature et nom du signataire obligatoires.');
      return;
    }
    const t = this.item();
    const raw = this.closeForm.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.todoService
      .updateStatus(t.id, 'DONE', {
        signatureCloture: raw.signatureCloture,
        signataireClotureNom: raw.signataireClotureNom.trim(),
        commentaireCloture: raw.commentaireCloture.trim() || null
      })
      .subscribe({
        next: (updated) => {
          this.saving.set(false);
          this.closingMode.set(false);
          this.updated.emit(updated);
          this.closed.emit();
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(apiErrorMessage(err, 'Clôture impossible.'));
        }
      });
  }
}
