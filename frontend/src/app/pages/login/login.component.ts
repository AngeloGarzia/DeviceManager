import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../services/auth.service';
import { apiErrorMessage } from '../../shared/api-error';

/**
 * Page de connexion à DeviceManager.
 * Authentifie l'utilisateur et redirige vers la liste des pièces détachées
 * (ou le changement de mot de passe obligatoire).
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly error = signal<string | null>(null);
  readonly loading = signal(false);
  readonly showPassword = signal(false);

  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required]
  });

  constructor() {
    if (this.auth.isLoggedIn()) {
      void this.router.navigate([this.auth.mustChangePassword() ? '/change-password' : '/devices']);
      return;
    }
    if (this.route.snapshot.queryParamMap.get('reason') === 'expired') {
      this.error.set('Session expirée. Veuillez vous reconnecter.');
      return;
    }
    // Rechargement de /login avec cookie refresh encore valide → renvoyer vers l'app.
    this.auth.tryRestoreSession().subscribe((ok) => {
      if (ok) {
        void this.router.navigate([this.auth.mustChangePassword() ? '/change-password' : '/devices']);
      }
    });
  }

  /** Affiche ou masque le mot de passe. */
  togglePasswordVisibility(): void {
    this.showPassword.update((v) => !v);
  }

  /** Soumet les identifiants et gère les erreurs de connexion. */
  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { username, password } = this.form.getRawValue();
    this.auth.login({ username, password }).subscribe({
      next: () => {
        this.loading.set(false);
        void this.router.navigate([this.auth.mustChangePassword() ? '/change-password' : '/devices']);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Connexion impossible'));
      }
    });
  }
}
