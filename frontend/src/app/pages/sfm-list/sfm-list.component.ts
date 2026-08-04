import { Component, OnInit, inject, signal } from '@angular/core';
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
import { Sfm } from '../../models/models';
import { SfmService } from '../../services/sfm.service';
import { AuthService } from '../../services/auth.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';

/**
 * Liste des SFM (fournisseurs de pièces) de l'atelier.
 * Permet la recherche, la consultation et la suppression des SFM.
 */
@Component({
  selector: 'app-sfm-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatCardModule, MatTableModule,
    MatProgressSpinnerModule, ConfirmDialogComponent
  ],
  templateUrl: './sfm-list.component.html',
  styleUrl: './sfm-list.component.scss'
})
export class SfmListComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly items = signal<Sfm[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly confirmOpen = signal(false);
  pendingDelete: Sfm | null = null;
  query = '';
  readonly displayedColumns = ['nom', 'marques', 'responsable', 'telephone', 'email', 'actions'];

  constructor(private sfmService: SfmService) {}
  ngOnInit(): void { this.load(); }
  /** Nombre total de SFM affichés. */
  get total(): number { return this.items().length; }

  /** Libellé des marques associées à un SFM, séparées par des virgules. */
  marquesLabel(item: Sfm): string {
    return item.marques?.map((m) => m.label).join(', ') || '—';
  }

  /** Charge les SFM selon le filtre de recherche courant. */
  load(): void {
    this.loading.set(true); this.error.set(null);
    this.sfmService.list(this.query).subscribe({
      next: (data) => { this.items.set(data); this.loading.set(false); },
      error: () => { this.error.set('Impossible de charger les SFM.'); this.loading.set(false); }
    });
  }
  /** Demande confirmation avant suppression d'un SFM. */
  askDelete(item: Sfm): void { this.pendingDelete = item; this.confirmOpen.set(true); }
  /** Annule la suppression en cours. */
  cancelDelete(): void { this.pendingDelete = null; this.confirmOpen.set(false); }
  /** Supprime le SFM sélectionné après confirmation. */
  confirmDelete(): void {
    if (!this.pendingDelete) return;
    const id = this.pendingDelete.id;
    this.confirmOpen.set(false);
    this.sfmService.delete(id).subscribe({
      next: () => { this.pendingDelete = null; this.load(); },
      error: (err) => { this.error.set(err?.error?.message || 'Suppression impossible.'); this.pendingDelete = null; }
    });
  }
}
