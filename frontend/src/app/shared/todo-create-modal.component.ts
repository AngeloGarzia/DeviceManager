import { Component, OnInit, inject, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Mas, TodoItem } from '../models/models';
import { MasService } from '../services/mas.service';
import { TodoService } from '../services/todo.service';
import { apiErrorMessage } from './api-error';

/**
 * Modale Accueil « Signaler » : création rapide d'une tâche STANDARD.
 */
@Component({
  selector: 'app-todo-create-modal',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './todo-create-modal.component.html',
  styleUrl: './todo-create-modal.component.scss'
})
export class TodoCreateModalComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly todoService = inject(TodoService);
  private readonly masService = inject(MasService);

  readonly closed = output<void>();
  readonly created = output<TodoItem>();

  readonly saving = signal(false);
  readonly loadingMas = signal(false);
  readonly error = signal<string | null>(null);
  readonly masses = signal<Mas[]>([]);

  readonly form = this.fb.nonNullable.group({
    titre: ['', [Validators.required, Validators.maxLength(200)]],
    description: ['', Validators.maxLength(2000)],
    severite: ['MEDIUM' as 'HIGH' | 'MEDIUM' | 'LOW'],
    masId: [null as number | null]
  });

  ngOnInit(): void {
    this.loadingMas.set(true);
    this.masService.list().subscribe({
      next: (list) => {
        this.masses.set(list ?? []);
        this.loadingMas.set(false);
      },
      error: () => {
        this.masses.set([]);
        this.loadingMas.set(false);
      }
    });
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

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Le titre est obligatoire.');
      return;
    }
    const raw = this.form.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.todoService
      .create({
        titre: raw.titre.trim(),
        description: raw.description.trim() || null,
        severite: raw.severite,
        typeTache: 'STANDARD',
        masId: raw.masId
      })
      .subscribe({
        next: (item) => {
          this.saving.set(false);
          this.created.emit(item);
          this.closed.emit();
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(apiErrorMessage(err, 'Création de la tâche impossible.'));
        }
      });
  }

  masOptionLabel(mas: Mas): string {
    const marque = (mas.marqueLabel || mas.marque || '').trim();
    return marque ? `${mas.numero} — ${marque}` : mas.numero;
  }
}
