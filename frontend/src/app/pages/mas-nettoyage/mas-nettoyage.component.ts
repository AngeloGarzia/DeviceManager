import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TodoNettoyageAnalyse, TodoNettoyageMasStatut } from '../../models/models';
import { TodoService } from '../../services/todo.service';
import { apiErrorMessage } from '../../shared/api-error';

type SortMode = 'urgence' | 'numero' | 'recent';

/**
 * Tableau de bord nettoyage MAS : grille colorée (vert → rouge écarlate) + analyse %.
 */
@Component({
  selector: 'app-mas-nettoyage',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './mas-nettoyage.component.html',
  styleUrl: './mas-nettoyage.component.scss'
})
export class MasNettoyageComponent implements OnInit {
  private readonly todoService = inject(TodoService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly data = signal<TodoNettoyageAnalyse | null>(null);
  readonly query = signal('');
  readonly sortMode = signal<SortMode>('urgence');
  readonly niveauFilter = signal<string | 'ALL'>('ALL');

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
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Impossible de charger l’analyse nettoyage.'));
      }
    });
  }

  /**
   * Vert (récent) → rouge écarlate (ancien / jamais).
   * score 100 → hue 120 (vert) ; score 0 → hue 0 (écarlate).
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

  niveauLabel(niveau: string): string {
    switch (niveau) {
      case 'RECENT':
        return 'Récent (≤ 7 j)';
      case 'OK':
        return 'OK (≤ 30 j)';
      case 'ATTENTION':
        return 'Attention (≤ 60 j)';
      case 'URGENT':
        return 'Urgent';
      case 'CRITIQUE':
        return 'Critique';
      case 'JAMAIS':
        return 'Jamais nettoyée';
      default:
        return niveau;
    }
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
