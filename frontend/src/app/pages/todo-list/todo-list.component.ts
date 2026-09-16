import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Intervention, InterventionTechnique, Mas, TodoItem, TodoRecurrence } from '../../models/models';
import { TodoService } from '../../services/todo.service';
import { MasService } from '../../services/mas.service';
import { InterventionTechniqueService } from '../../services/intervention-technique.service';
import { InterventionService } from '../../services/intervention.service';
import { AuthService } from '../../services/auth.service';
import { SignaturePadComponent } from '../../shared/signature-pad.component';
import { apiErrorMessage } from '../../shared/api-error';
import { MatCheckboxModule } from '@angular/material/checkbox';

type TodoFilter = 'ALL' | 'ACTIVE' | 'OVERDUE' | 'OPEN' | 'IN_PROGRESS' | 'DONE' | 'CANCELLED';

/**
 * Page Todo : cycle de vie complet des tâches (création → clôture signée) + règles récurrentes.
 */
@Component({
  selector: 'app-todo-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatCheckboxModule,
    SignaturePadComponent
  ],
  templateUrl: './todo-list.component.html',
  styleUrl: './todo-list.component.scss'
})
export class TodoListComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly todoService = inject(TodoService);
  private readonly masService = inject(MasService);
  private readonly interventionTechniqueService = inject(InterventionTechniqueService);
  private readonly interventionService = inject(InterventionService);

  readonly items = signal<TodoItem[]>([]);
  readonly recurrences = signal<TodoRecurrence[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly saving = signal(false);
  readonly showCreate = signal(false);
  readonly showRecurrenceCreate = signal(false);
  readonly linkingId = signal<number | null>(null);
  readonly closingId = signal<number | null>(null);
  readonly filter = signal<TodoFilter>('ACTIVE');
  readonly dayFilter = signal<string | null>(null);
  readonly query = signal('');
  readonly masses = signal<Mas[]>([]);
  readonly massesLoading = signal(false);
  readonly interventionsTech = signal<InterventionTechnique[]>([]);
  readonly bonsIntervention = signal<Intervention[]>([]);

  readonly filteredItems = computed(() => {
    const f = this.filter();
    const day = this.dayFilter();
    const q = this.normalize(this.query());
    let list = this.items();
    if (f === 'ACTIVE') {
      list = list.filter((t) => t.statut === 'OPEN' || t.statut === 'IN_PROGRESS');
    } else if (f === 'OVERDUE') {
      list = list.filter((t) => !!t.overdue);
    } else if (f !== 'ALL') {
      list = list.filter((t) => t.statut === f);
    }
    if (day) {
      list = list.filter((t) => (t.dueAt || '').startsWith(day));
    }
    if (!q) {
      return list;
    }
    return list.filter((t) => this.matchesQuery(t, q));
  });

  readonly counts = computed(() => {
    const list = this.items();
    return {
      all: list.length,
      active: list.filter((t) => t.statut === 'OPEN' || t.statut === 'IN_PROGRESS').length,
      overdue: list.filter((t) => !!t.overdue).length,
      open: list.filter((t) => t.statut === 'OPEN').length,
      inProgress: list.filter((t) => t.statut === 'IN_PROGRESS').length,
      done: list.filter((t) => t.statut === 'DONE').length,
      cancelled: list.filter((t) => t.statut === 'CANCELLED').length
    };
  });

  readonly todoForm = this.fb.nonNullable.group({
    titre: ['', [Validators.required, Validators.maxLength(200)]],
    description: ['', Validators.maxLength(2000)],
    severite: ['MEDIUM'],
    masId: [null as number | null]
  });

  readonly recurrenceForm = this.fb.nonNullable.group({
    titre: ['', [Validators.required, Validators.maxLength(200)]],
    description: ['', Validators.maxLength(2000)],
    severite: ['MEDIUM'],
    masId: [null as number | null],
    frequence: ['WEEKLY', Validators.required],
    intervalDays: [7 as number | null],
    jourSemaine: [1 as number | null],
    jourMois: [1 as number | null],
    heureDue: ['08:00'],
    dateDebut: ['', Validators.required],
    dateFin: [''],
    active: [true]
  });

  readonly linkForm = this.fb.nonNullable.group({
    interventionTechniqueId: [null as number | null],
    interventionId: [null as number | null]
  });

  readonly closeForm = this.fb.nonNullable.group({
    signataireClotureNom: ['', [Validators.required, Validators.maxLength(120)]],
    commentaireCloture: ['', Validators.maxLength(2000)],
    signatureCloture: [null as string | null, Validators.required]
  });

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const filterParam = params.get('filter');
    if (filterParam === 'OVERDUE' || filterParam === 'ACTIVE' || filterParam === 'OPEN'
        || filterParam === 'IN_PROGRESS' || filterParam === 'DONE' || filterParam === 'CANCELLED'
        || filterParam === 'ALL') {
      this.filter.set(filterParam);
    }
    const day = params.get('day');
    if (day && /^\d{4}-\d{2}-\d{2}$/.test(day)) {
      this.dayFilter.set(day);
    }
    this.load();
    this.loadRecurrences();
    this.loadMasses();
  }

  setFilter(f: TodoFilter): void {
    this.filter.set(f);
    this.dayFilter.set(null);
    this.closingId.set(null);
    this.linkingId.set(null);
  }

  onQueryChange(value: string): void {
    this.query.set(value);
  }

  clearQuery(): void {
    this.query.set('');
  }

  private normalize(value: string | null | undefined): string {
    return (value || '')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .trim();
  }

  private matchesQuery(item: TodoItem, q: string): boolean {
    const haystack = this.normalize(
      [
        item.titre,
        item.title,
        item.description,
        item.commentaireCloture,
        item.severite,
        item.statut,
        this.statutLabel(item.statut),
        this.severityLabel(item.severite || item.severity || ''),
        item.responsableCreation,
        item.createdByDisplayName,
        item.createdByUsername,
        item.responsableCloture,
        item.signataireClotureNom,
        item.completedByDisplayName,
        item.completedByUsername,
        item.masNumero,
        item.interventionTechniqueLabel,
        item.interventionNumero,
        item.dateCreation,
        item.createdAt,
        item.dateCloture,
        item.completedAt
      ]
        .filter(Boolean)
        .join(' ')
    );
    return haystack.includes(q);
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.todoService.list(true).subscribe({
      next: (data) => {
        this.items.set(data.items ?? []);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Impossible de charger les tâches.'));
      }
    });
  }

  loadRecurrences(): void {
    this.todoService.listRecurrences().subscribe({
      next: (list) => this.recurrences.set(list ?? []),
      error: () => this.recurrences.set([])
    });
  }

  loadMasses(): void {
    this.massesLoading.set(true);
    this.masService.list().subscribe({
      next: (list) => {
        this.masses.set(
          [...list].sort((a, b) => a.numero.localeCompare(b.numero, 'fr', { sensitivity: 'base' }))
        );
        this.massesLoading.set(false);
      },
      error: () => {
        this.masses.set([]);
        this.massesLoading.set(false);
      }
    });
  }

  openCreate(): void {
    this.showCreate.set(true);
    this.showRecurrenceCreate.set(false);
    this.todoForm.reset({ titre: '', description: '', severite: 'MEDIUM', masId: null });
    this.loadMasses();
  }

  cancelCreate(): void {
    this.showCreate.set(false);
  }

  openRecurrenceCreate(): void {
    this.showRecurrenceCreate.set(true);
    this.showCreate.set(false);
    const today = new Date().toISOString().slice(0, 10);
    this.recurrenceForm.reset({
      titre: '',
      description: '',
      severite: 'MEDIUM',
      masId: null,
      frequence: 'WEEKLY',
      intervalDays: 7,
      jourSemaine: 1,
      jourMois: 1,
      heureDue: '08:00',
      dateDebut: today,
      dateFin: '',
      active: true
    });
    this.loadMasses();
  }

  cancelRecurrenceCreate(): void {
    this.showRecurrenceCreate.set(false);
  }

  submitRecurrenceCreate(): void {
    if (this.recurrenceForm.invalid) {
      this.recurrenceForm.markAllAsTouched();
      return;
    }
    const raw = this.recurrenceForm.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.todoService
      .createRecurrence({
        titre: raw.titre,
        description: raw.description || null,
        severite: raw.severite,
        masId: raw.masId,
        frequence: raw.frequence,
        intervalDays: raw.frequence === 'INTERVAL_DAYS' ? raw.intervalDays : null,
        jourSemaine: raw.frequence === 'WEEKLY' ? raw.jourSemaine : null,
        jourMois: raw.frequence === 'MONTHLY' ? raw.jourMois : null,
        heureDue: raw.heureDue ? `${raw.heureDue}:00`.slice(0, 8) : '08:00:00',
        dateDebut: raw.dateDebut,
        dateFin: raw.dateFin || null,
        active: raw.active
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.showRecurrenceCreate.set(false);
          this.loadRecurrences();
          this.load();
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(apiErrorMessage(err, 'Création de la récurrence impossible.'));
        }
      });
  }

  toggleRecurrenceActive(rule: TodoRecurrence): void {
    this.saving.set(true);
    this.todoService.setRecurrenceActive(rule.id, !rule.active).subscribe({
      next: () => {
        this.saving.set(false);
        this.loadRecurrences();
        this.load();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Mise à jour de la récurrence impossible.'));
      }
    });
  }

  deleteRecurrence(rule: TodoRecurrence): void {
    if (!confirm(`Supprimer la récurrence « ${rule.titre} » ? Les occurrences déjà créées restent.`)) {
      return;
    }
    this.saving.set(true);
    this.todoService.deleteRecurrence(rule.id).subscribe({
      next: () => {
        this.saving.set(false);
        this.loadRecurrences();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Suppression de la récurrence impossible.'));
      }
    });
  }

  frequenceLabel(freq: string): string {
    switch (freq) {
      case 'DAILY':
        return 'Quotidienne';
      case 'WEEKLY':
        return 'Hebdomadaire';
      case 'MONTHLY':
        return 'Mensuelle';
      case 'INTERVAL_DAYS':
        return 'Tous les N jours';
      default:
        return freq;
    }
  }

  jourSemaineLabel(day?: number | null): string {
    const labels = ['', 'Lundi', 'Mardi', 'Mercredi', 'Jeudi', 'Vendredi', 'Samedi', 'Dimanche'];
    return day && day >= 1 && day <= 7 ? labels[day] : '—';
  }

  submitCreate(): void {
    if (this.todoForm.invalid) {
      this.todoForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const raw = this.todoForm.getRawValue();
    this.todoService
      .create({
        titre: raw.titre,
        description: raw.description || null,
        severite: raw.severite,
        masId: raw.masId
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.showCreate.set(false);
          this.filter.set('ACTIVE');
          this.load();
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(apiErrorMessage(err, 'Création de la tâche impossible.'));
        }
      });
  }

  setStatus(item: TodoItem, statut: string): void {
    if (statut === 'DONE') {
      this.startClose(item);
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.todoService.updateStatus(item.id, statut).subscribe({
      next: () => {
        this.saving.set(false);
        this.load();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Changement de statut impossible.'));
      }
    });
  }

  startClose(item: TodoItem): void {
    this.linkingId.set(null);
    this.closingId.set(item.id);
    this.closeForm.reset({
      signataireClotureNom: this.auth.displayName() || this.auth.username() || '',
      commentaireCloture: '',
      signatureCloture: null
    });
  }

  cancelClose(): void {
    this.closingId.set(null);
  }

  submitClose(todoId: number): void {
    if (this.closeForm.invalid) {
      this.closeForm.markAllAsTouched();
      this.error.set('Signature et nom du responsable de clôture obligatoires.');
      return;
    }
    const raw = this.closeForm.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.todoService
      .updateStatus(todoId, 'DONE', {
        signatureCloture: raw.signatureCloture,
        signataireClotureNom: raw.signataireClotureNom.trim(),
        commentaireCloture: raw.commentaireCloture.trim() || null
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.closingId.set(null);
          this.filter.set('DONE');
          this.load();
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(apiErrorMessage(err, 'Clôture impossible.'));
        }
      });
  }

  startLink(item: TodoItem): void {
    this.closingId.set(null);
    this.linkingId.set(item.id);
    this.linkForm.patchValue({
      interventionTechniqueId: item.interventionTechniqueId ?? null,
      interventionId: item.interventionId ?? null
    });
    if (this.interventionsTech().length === 0) {
      this.interventionTechniqueService.list().subscribe({
        next: (list) =>
          this.interventionsTech.set(
            [...list].sort((a, b) => (b.dateIntervention || '').localeCompare(a.dateIntervention || ''))
          )
      });
    }
    if (this.bonsIntervention().length === 0) {
      this.interventionService.list().subscribe({
        next: (list) => this.bonsIntervention.set(list)
      });
    }
  }

  cancelLink(): void {
    this.linkingId.set(null);
  }

  submitLink(todoId: number): void {
    const raw = this.linkForm.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.todoService
      .linkIntervention(todoId, {
        interventionTechniqueId: raw.interventionTechniqueId,
        interventionId: raw.interventionId,
        clearTechnique: raw.interventionTechniqueId == null,
        clearBon: raw.interventionId == null
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.linkingId.set(null);
          this.load();
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(apiErrorMessage(err, 'Rattachement impossible.'));
        }
      });
  }

  deleteTodo(item: TodoItem): void {
    if (!confirm(`Supprimer la tâche « ${item.titre} » ?`)) {
      return;
    }
    this.saving.set(true);
    this.todoService.delete(item.id).subscribe({
      next: () => {
        this.saving.set(false);
        this.load();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Suppression impossible.'));
      }
    });
  }

  formatDateTime(value?: string | null): string {
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

  severityLabel(severity: string): string {
    switch (severity) {
      case 'HIGH':
        return 'Urgent';
      case 'MEDIUM':
        return 'Normal';
      default:
        return 'Faible';
    }
  }

  statutLabel(statut: string): string {
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
        return statut;
    }
  }

  masOptionLabel(mas: Mas): string {
    const marque = mas.marqueLabel || mas.marque;
    return marque ? `${mas.numero} — ${marque}` : mas.numero;
  }

  techOptionLabel(it: InterventionTechnique): string {
    const date = it.dateIntervention
      ? new Date(it.dateIntervention).toLocaleDateString('fr-FR')
      : '';
    return `#${it.id} — MAS ${it.masNumero || '?'} — ${it.motif || 'Intervention'}${date ? ' (' + date + ')' : ''}`;
  }

  bonOptionLabel(bon: Intervention): string {
    return `${bon.numero} — ${bon.motif || 'Bon'}`;
  }
}
