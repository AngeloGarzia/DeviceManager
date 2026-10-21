import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { forkJoin, of, switchMap } from 'rxjs';
import { TodoNettoyageAnalyse, TodoNettoyageMasStatut } from '../../models/models';
import { TodoService } from '../../services/todo.service';
import { AuthService } from '../../services/auth.service';
import { SignaturePadComponent } from '../../shared/signature-pad.component';
import { apiErrorMessage } from '../../shared/api-error';

type SortMode = 'urgence' | 'numero' | 'recent';

/**
 * Tableau de bord nettoyage MAS : grille colorée + sélection multi-MAS → tâche clôturée.
 */
@Component({
  selector: 'app-mas-nettoyage',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatFormFieldModule,
    MatInputModule,
    SignaturePadComponent
  ],
  templateUrl: './mas-nettoyage.component.html',
  styleUrl: './mas-nettoyage.component.scss'
})
export class MasNettoyageComponent implements OnInit {
  private readonly todoService = inject(TodoService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly modalError = signal<string | null>(null);
  readonly success = signal<string | null>(null);
  readonly data = signal<TodoNettoyageAnalyse | null>(null);
  readonly query = signal('');
  readonly sortMode = signal<SortMode>('urgence');
  readonly niveauFilter = signal<string | 'ALL'>('ALL');

  /** MAS sélectionnées pour création rapide de tâche nettoyage. */
  readonly selectedIds = signal<Set<number>>(new Set());
  readonly modalOpen = signal(false);

  readonly selectedItems = computed(() => {
    const ids = this.selectedIds();
    const items = this.data()?.items ?? [];
    return items.filter((i) => ids.has(i.masId));
  });

  readonly selectedCount = computed(() => this.selectedIds().size);

  readonly filteredItems = computed(() => {
    const d = this.data();
    if (!d) {
      return [] as TodoNettoyageMasStatut[];
    }
    const q = this.query().trim().toLowerCase();
    const niveau = this.niveauFilter();
    let list = d.items ?? [];
    if (niveau !== 'ALL') {
      list = list.filter((i) => i.niveau === niveau);
    }
    if (q) {
      list = list.filter(
        (i) =>
          (i.masNumero || '').toLowerCase().includes(q) ||
          (i.marqueLabel || '').toLowerCase().includes(q)
      );
    }
    const mode = this.sortMode();
    const sorted = [...list];
    if (mode === 'numero') {
      sorted.sort((a, b) => (a.masNumero || '').localeCompare(b.masNumero || '', 'fr'));
    } else if (mode === 'recent') {
      sorted.sort((a, b) => b.freshnessScore - a.freshnessScore);
    } else {
      sorted.sort((a, b) => a.freshnessScore - b.freshnessScore);
    }
    return sorted;
  });

  readonly closeForm = this.fb.nonNullable.group({
    titre: ['', [Validators.required, Validators.maxLength(200)]],
    signataireClotureNom: ['', [Validators.required, Validators.maxLength(120)]],
    commentaireCloture: ['', Validators.maxLength(2000)],
    signatureCloture: [null as string | null, Validators.required]
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.todoService.analyseNettoyage().subscribe({
      next: (res) => {
        this.data.set(res);
        this.loading.set(false);
        // Retirer de la sélection les MAS disparues.
        const valid = new Set((res.items ?? []).map((i) => i.masId));
        this.selectedIds.update((cur) => new Set([...cur].filter((id) => valid.has(id))));
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Impossible de charger l’analyse nettoyage.'));
      }
    });
  }

  isSelected(masId: number): boolean {
    return this.selectedIds().has(masId);
  }

  toggleSelect(item: TodoNettoyageMasStatut, event?: Event): void {
    event?.preventDefault();
    this.selectedIds.update((cur) => {
      const next = new Set(cur);
      if (next.has(item.masId)) {
        next.delete(item.masId);
      } else {
        next.add(item.masId);
      }
      return next;
    });
    this.success.set(null);
  }

  clearSelection(): void {
    this.selectedIds.set(new Set());
  }

  openCreateModal(): void {
    const items = this.selectedItems();
    if (items.length === 0) {
      return;
    }
    const numeros = items.map((i) => i.masNumero).join(', ');
    const titre =
      items.length === 1
        ? `Nettoyage MAS ${items[0].masNumero}`
        : `Nettoyage MAS (${items.length}) — ${numeros}`;
    this.closeForm.reset({
      titre: titre.slice(0, 200),
      signataireClotureNom: this.auth.displayName() || '',
      commentaireCloture: '',
      signatureCloture: null
    });
    this.modalError.set(null);
    this.modalOpen.set(true);
  }

  closeModal(): void {
    if (this.saving()) {
      return;
    }
    this.modalOpen.set(false);
    this.modalError.set(null);
  }

  /**
   * Crée la tâche NETTOYAGE_MAS, coche toutes les MAS, clôture DONE + signature.
   */
  submitSignedNettoyage(): void {
    if (this.closeForm.invalid) {
      this.closeForm.markAllAsTouched();
      return;
    }
    const items = this.selectedItems();
    if (items.length === 0) {
      this.modalError.set('Sélectionnez au moins une MAS.');
      return;
    }
    const raw = this.closeForm.getRawValue();
    const masIds = items.map((i) => i.masId);
    this.saving.set(true);
    this.modalError.set(null);

    this.todoService
      .create({
        titre: raw.titre.trim(),
        description: null,
        severite: 'MEDIUM',
        typeTache: 'NETTOYAGE_MAS',
        masIds
      })
      .pipe(
        switchMap((todo) => {
          const coches = masIds.map((masId) =>
            this.todoService.setNettoyageCoche(todo.id, masId, true)
          );
          return (coches.length ? forkJoin(coches) : of([todo])).pipe(switchMap(() => of(todo)));
        }),
        switchMap((todo) =>
          this.todoService.updateStatus(todo.id, 'DONE', {
            signatureCloture: raw.signatureCloture,
            signataireClotureNom: raw.signataireClotureNom.trim(),
            commentaireCloture: raw.commentaireCloture?.trim() || null
          })
        )
      )
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.modalOpen.set(false);
          this.clearSelection();
          this.success.set(
            `Nettoyage enregistré et clôturé pour ${masIds.length} MAS.`
          );
          this.reload();
        },
        error: (err) => {
          this.saving.set(false);
          this.modalError.set(
            apiErrorMessage(err, 'Impossible de créer / clôturer la tâche de nettoyage.')
          );
        }
      });
  }

  /**
   * Vert (récent) → rouge écarlate (ancien / jamais).
   */
  tileStyle(item: TodoNettoyageMasStatut): Record<string, string> {
    const score = Math.max(0, Math.min(100, item.freshnessScore ?? 0));
    const hue = (score / 100) * 120;
    const lightness = score < 25 ? 38 : score < 55 ? 42 : 46;
    const bg = `hsl(${hue} 92% ${lightness}%)`;
    const fg = score < 45 ? '#fff7f7' : '#0f1f12';
    return {
      'background-color': bg,
      color: fg,
      'border-color': `hsl(${hue} 80% ${Math.max(22, lightness - 12)}%)`
    };
  }

  joursLabel(item: TodoNettoyageMasStatut): string {
    if (item.joursDepuis == null) {
      return 'Jamais nettoyée';
    }
    if (item.joursDepuis === 0) {
      return 'Aujourd’hui';
    }
    if (item.joursDepuis === 1) {
      return 'Il y a 1 jour';
    }
    return `Il y a ${item.joursDepuis} jours`;
  }

  setSort(mode: SortMode): void {
    this.sortMode.set(mode);
  }

  setNiveau(niveau: string | 'ALL'): void {
    this.niveauFilter.set(niveau);
  }

  bucketPct(count: number): number {
    const total = this.data()?.totalMas ?? 0;
    if (total <= 0) {
      return 0;
    }
    return Math.round((count * 1000) / total) / 10;
  }
}
