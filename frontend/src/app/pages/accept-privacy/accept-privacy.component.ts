import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../services/auth.service';
import { PrivacyComponent } from '../privacy/privacy.component';
import { apiErrorMessage } from '../../shared/api-error';

/**
 * Acceptation obligatoire des mentions RGPD à la première connexion.
 */
@Component({
  selector: 'app-accept-privacy',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatProgressSpinnerModule,
    PrivacyComponent
  ],
  templateUrl: './accept-privacy.component.html',
  styleUrl: './accept-privacy.component.scss'
})
export class AcceptPrivacyComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly error = signal<string | null>(null);
  readonly loading = signal(false);

  readonly form = this.fb.nonNullable.group({
    accepted: [false, Validators.requiredTrue]
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Vous devez lire et accepter les mentions pour continuer.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.acceptPrivacy().subscribe({
      next: () => {
        this.loading.set(false);
        void this.router.navigate([this.auth.postLoginTarget()]);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, "Impossible d'enregistrer l'acceptation."));
      }
    });
  }
}
