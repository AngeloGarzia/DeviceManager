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
import { apiErrorMessage } from '../../shared/api-error';

/**
 * Liste des comptes utilisateurs du groupe (réservée aux administrateurs).
 * Permet la consultation, l'envoi d'un mail de bienvenue et la suppression.
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
  readonly success = signal<string | null>(null);
  readonly confirmOpen = signal(false);
  readonly confirmMailOpen = signal(false);
  readonly mailingId = signal<number | null>(null);
  pendingDelete: AppUser | null = null;
  pendingMail: AppUser | null = null;
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
        this.error.set(apiErrorMessage(err, 'Chargement impossible.'));
      }
    });
  }

  /** Libellé français du rôle utilisateur. */
  roleLabel(role: string): string {
    if (role === 'SUPER_ADMIN') return 'Super-administrateur';
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
      error: (err) => this.error.set(apiErrorMessage(err, 'Suppression impossible.'))
    });
  }

  /** Demande confirmation avant envoi du mail de bienvenue. */
  askWelcomeMail(user: AppUser): void {
    if (!user.email?.trim()) {
      this.error.set("Cet utilisateur n'a pas d'adresse e-mail.");
      return;
    }
    this.pendingMail = user;
    this.confirmMailOpen.set(true);
  }

  /** Envoie le mail de bienvenue si l'admin confirme. */
  confirmWelcomeMail(ok: boolean): void {
    this.confirmMailOpen.set(false);
    if (!ok || !this.pendingMail) {
      this.pendingMail = null;
      return;
    }
    const user = this.pendingMail;
    this.pendingMail = null;
    this.error.set(null);
    this.success.set(null);
    this.mailingId.set(user.id);
    this.userService.sendWelcomeMail(user.id).subscribe({
      next: (res) => {
        this.mailingId.set(null);
        this.success.set(
          res.message || `E-mail de bienvenue envoyé à ${user.email}.`
        );
      },
      error: (err) => {
        this.mailingId.set(null);
        this.error.set(apiErrorMessage(err, "Envoi de l'e-mail impossible."));
      }
    });
  }
}
