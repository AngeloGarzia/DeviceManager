import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AppUserForm, AtelierSummary } from '../../models/models';
import { UserService } from '../../services/user.service';
import { AtelierService } from '../../services/atelier.service';
import { apiErrorMessage } from '../../shared/api-error';

/**
 * Formulaire de création ou modification d'un compte utilisateur.
 * Gère rôle, atelier préféré pour les techniciens et mot de passe.
 */
@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCardModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './user-form.component.html',
  styleUrl: './user-form.component.scss'
})
export class UserFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly userService = inject(UserService);
  private readonly atelierService = inject(AtelierService);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly ateliers = signal<AtelierSummary[]>([]);
  id: number | null = null;

  readonly form = this.fb.group({
    prenom: ['', [Validators.required, Validators.maxLength(80)]],
    nom: ['', [Validators.required, Validators.maxLength(80)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(160)]],
    username: ['', [Validators.required, Validators.maxLength(80)]],
    password: [''],
    role: ['TECHNICIEN', Validators.required],
    preferredAtelierId: [null as number | null]
  });

  /** Indique si le formulaire est en mode édition. */
  get isEdit(): boolean {
    return this.id !== null;
  }

  /** Indique si le rôle sélectionné est technicien (atelier requis). */
  get isTechnicien(): boolean {
    const role = this.form.controls.role.value;
    return role === 'TECHNICIEN' || role === 'TECH';
  }

  ngOnInit(): void {
    this.form.controls.role.valueChanges.subscribe(() => this.syncPreferredValidators());
    this.syncPreferredValidators();

    this.atelierService.list().subscribe({
      next: (list) => this.ateliers.set(list ?? []),
      error: () => this.ateliers.set([])
    });

    const rawId = this.route.snapshot.paramMap.get('id');
    if (!rawId) {
      this.form.controls.password.setValidators([Validators.required, Validators.minLength(6)]);
      this.form.controls.password.updateValueAndValidity();
      return;
    }
    this.id = Number(rawId);
    this.loading.set(true);
    this.userService.list().subscribe({
      next: (users) => {
        const user = users.find((u) => u.id === this.id);
        if (!user) {
          this.error.set('Compte introuvable.');
          this.loading.set(false);
          return;
        }
        this.form.patchValue({
          prenom: user.prenom || '',
          nom: user.nom || '',
          email: user.email || '',
          username: user.username,
          role: user.role,
          preferredAtelierId: user.preferredAtelierId ?? null,
          password: ''
        });
        this.syncPreferredValidators();
        this.form.controls.password.setValidators([Validators.minLength(6)]);
        this.form.controls.password.updateValueAndValidity();
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Compte introuvable.');
        this.loading.set(false);
      }
    });
  }

  /** Valide et enregistre le compte (création ou mise à jour). */
  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const payload: AppUserForm = {
      prenom: raw.prenom!.trim(),
      nom: raw.nom!.trim(),
      email: raw.email!.trim().toLowerCase(),
      username: raw.username!.trim(),
      role: raw.role!,
      preferredAtelierId: this.isTechnicien ? raw.preferredAtelierId : (raw.preferredAtelierId ?? null),
      ...(raw.password ? { password: raw.password } : {})
    };
    this.saving.set(true);
    this.error.set(null);
    const req$ = this.id
      ? this.userService.update(this.id, payload)
      : this.userService.create(payload);
    req$.subscribe({
      next: () => this.router.navigate(['/users']),
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Enregistrement impossible.'));
      }
    });
  }

  private syncPreferredValidators(): void {
    const control = this.form.controls.preferredAtelierId;
    if (this.isTechnicien) {
      control.setValidators([Validators.required]);
    } else {
      control.clearValidators();
    }
    control.updateValueAndValidity({ emitEvent: false });
  }
}
