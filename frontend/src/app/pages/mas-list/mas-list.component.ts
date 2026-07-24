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
import { Mas } from '../../models/models';
import { MasService } from '../../services/mas.service';
import { AuthService } from '../../services/auth.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';

@Component({
  selector: 'app-mas-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink, MatButtonModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatCardModule, MatTableModule,
    MatProgressSpinnerModule, ConfirmDialogComponent
  ],
  templateUrl: './mas-list.component.html',
  styleUrl: './mas-list.component.scss'
})
export class MasListComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly items = signal<Mas[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly confirmOpen = signal(false);
  pendingDelete: Mas | null = null;
  query = '';
  readonly displayedColumns = ['numero', 'marque', 'statut', 'actions'];

  constructor(private masService: MasService) {}

  ngOnInit(): void { this.load(); }
  get total(): number { return this.items().length; }
  get usedCount(): number { return this.items().filter((m) => m.utilise).length; }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.masService.list(this.query).subscribe({
      next: (data) => { this.items.set(data); this.loading.set(false); },
      error: () => { this.error.set('Impossible de charger les MAS.'); this.loading.set(false); }
    });
  }

  askDelete(item: Mas): void { this.pendingDelete = item; this.confirmOpen.set(true); }
  cancelDelete(): void { this.pendingDelete = null; this.confirmOpen.set(false); }
  confirmDelete(): void {
    if (!this.pendingDelete) return;
    const id = this.pendingDelete.id;
    this.confirmOpen.set(false);
    this.masService.delete(id).subscribe({
      next: () => { this.pendingDelete = null; this.load(); },
      error: (err) => { this.error.set(err?.error?.message || 'Suppression impossible.'); this.pendingDelete = null; }
    });
  }
}
