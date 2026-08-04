import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AppUser } from '../../models/models';
import { UserService } from '../../services/user.service';
import { AuthService } from '../../services/auth.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';

/**
 * Liste des comptes utilisateurs du groupe (réservée aux administrateurs).
 * Permet la consultation et la suppression des comptes.
 */
@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatTableModule,
    MatProgressSpinnerModule,
    ConfirmDialogComponent
  ],
  templateUrl: './user-list.component.html',
  styleUrl: './user-list.component.scss'
})
export class UserListComponent implements OnInit {
  private readonly userService = inject(UserService);
  readonly auth = inject(AuthService);
  readonly items = signal<AppUser[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly confirmOpen = signal(false);
  pendingDelete: AppUser | null = null;
  readonly displayedColumns = ['name', 'email', 'username', 'role', 'atelier', 'createdAt', 'actions'];

  ngOnInit(): void {
    this.load();
  }

  /** Charge la liste des utilisateurs depuis l'API. */
  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.userService.list().subscribe({
      next: (data) => {
        this.items.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message || 'Chargement impossible.');
      }
    });
  }

  /** Libellé français du rôle utilisateur. */
  roleLabel(role: string): string {
    if (role === 'ADMIN') return 'Administrateur';
    if (role === 'TECHNICIEN' || role === 'TECH') return 'Technicien';
    return role;
  }

  /** Nom complet affiché ou identifiant de connexion en repli. */
  displayName(user: AppUser): string {
    const full = `${user.prenom || ''} ${user.nom || ''}`.trim();
    return full || user.username;
  }

  /** Demande confirmation avant suppression d'un compte. */
  askDelete(user: AppUser): void {
    this.pendingDelete = user;
    this.confirmOpen.set(true);
  }

  /** Supprime le compte sélectionné si l'utilisateur confirme. */
  confirmDelete(ok: boolean): void {
    this.confirmOpen.set(false);
    if (!ok || !this.pendingDelete) {
      this.pendingDelete = null;
      return;
    }
    const id = this.pendingDelete.id;
    this.pendingDelete = null;
    this.userService.delete(id).subscribe({
      next: () => this.load(),
      error: (err) => this.error.set(err?.error?.message || 'Suppression impossible.')
    });
  }
}
