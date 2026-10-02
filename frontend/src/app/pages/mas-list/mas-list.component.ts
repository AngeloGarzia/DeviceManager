import { AfterViewChecked, Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Mas } from '../../models/models';
import { MasService } from '../../services/mas.service';
import { AuthService } from '../../services/auth.service';
import { masStatutBadgeClass, masStatutLabel } from '../../shared/mas-statut';

/**
 * Liste des MAS de l'atelier.
 * Consultation et édition (changement de statut) — pas de suppression en base.
 */
@Component({
  selector: 'app-mas-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatCardModule, MatTableModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './mas-list.component.html',
  styleUrl: './mas-list.component.scss'
})
export class MasListComponent implements OnInit, AfterViewChecked {
  private static readonly SELECTED_KEY = 'dm-mas-list-selected-id';

  readonly auth = inject(AuthService);
  readonly items = signal<Mas[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly selectedId = signal<number | null>(MasListComponent.readStoredSelectedId());
  query = '';
  readonly displayedColumns = ['numero', 'socle', 'marque', 'deno', 'taux', 'statut', 'jeux', 'actions'];
  readonly statutLabel = masStatutLabel;
  readonly statutBadgeClass = masStatutBadgeClass;

  /** Une seule restauration de scroll/focus après chargement. */
  private pendingFocus = false;

  constructor(private masService: MasService) {}

  ngOnInit(): void {
    this.load();
  }

  ngAfterViewChecked(): void {
    if (!this.pendingFocus || this.loading()) {
      return;
    }
    this.pendingFocus = false;
    this.focusSelectedRow();
  }

  /** Nombre total de MAS affichées. */
  get total(): number {
    return this.items().length;
  }

  /** Nombre de MAS marquées comme utilisées. */
  get usedCount(): number {
    return this.items().filter((m) => m.utilise).length;
  }

  /** Règles de jeux rattachées à une MAS (triées par libellé). */
  reglesOf(item: Mas): { id: number; label: string }[] {
    const fromObjects = (item.reglesJeux || [])
      .filter((r) => r?.id != null && (r.label || '').trim())
      .map((r) => ({ id: r.id, label: r.label.trim() }));
    if (fromObjects.length > 0) {
      return fromObjects;
    }
    return (item.regleJeuxIds || []).map((id) => ({ id, label: `Règle #${id}` }));
  }

  /** Sélectionne une MAS (surlignage + mémorisation pour le retour Voir/Éditer). */
  selectMas(id: number): void {
    this.selectedId.set(id);
    try {
      sessionStorage.setItem(MasListComponent.SELECTED_KEY, String(id));
    } catch {
      /* ignore quota / private mode */
    }
  }

  isSelected(id: number): boolean {
    return this.selectedId() === id;
  }

  /** Charge les MAS selon le filtre de recherche courant. */
  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.masService.list(this.query).subscribe({
      next: (data) => {
        this.items.set(data);
        this.loading.set(false);
        if (this.selectedId() != null) {
          this.pendingFocus = true;
        }
      },
      error: () => {
        this.error.set('Impossible de charger les MAS.');
        this.loading.set(false);
      }
    });
  }

  private focusSelectedRow(): void {
    const id = this.selectedId();
    if (id == null) {
      return;
    }
    const candidates = [
      document.getElementById(`mas-row-${id}`),
      document.getElementById(`mas-card-${id}`)
    ].filter((el): el is HTMLElement => el != null);
    const el =
      candidates.find((node) => node.getClientRects().length > 0) ?? candidates[0] ?? null;
    if (!el) {
      return;
    }
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    if (typeof el.focus === 'function') {
      el.focus({ preventScroll: true });
    }
  }

  private static readStoredSelectedId(): number | null {
    try {
      const raw = sessionStorage.getItem(MasListComponent.SELECTED_KEY);
      if (!raw) {
        return null;
      }
      const id = Number(raw);
      return Number.isFinite(id) && id > 0 ? id : null;
    } catch {
      return null;
    }
  }
}
