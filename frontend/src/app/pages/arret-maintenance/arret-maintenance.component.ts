import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin } from 'rxjs';
import { ArretMaintenance, Mas } from '../../models/models';
import { ArretMaintenanceService } from '../../services/arret-maintenance.service';
import { AuthService } from '../../services/auth.service';
import { MasService } from '../../services/mas.service';
import { SignaturePadComponent } from '../../shared/signature-pad.component';
import { apiErrorMessage } from '../../shared/api-error';

@Component({
  selector: 'app-arret-maintenance',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule,
    SignaturePadComponent
  ],
  templateUrl: './arret-maintenance.component.html',
  styleUrl: './arret-maintenance.component.scss'
})
export class ArretMaintenanceComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly arretService = inject(ArretMaintenanceService);
  private readonly masService = inject(MasService);
  private readonly auth = inject(AuthService);

  readonly masses = signal<Mas[]>([]);
  readonly active = signal<ArretMaintenance[]>([]);
  readonly history = signal<ArretMaintenance[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);
  readonly repriseId = signal<number | null>(null);

  readonly arretForm = this.fb.group({
    masId: [null as number | null, Validators.required],
    dateHeureArret: [this.nowLocalInput()],
    motifArret: ['', [Validators.required, Validators.maxLength(500)]],
    registreTechniqueAJour: [false],
    signatureArret: [null as string | null],
    signataireArretNom: [this.auth.displayName()]
  });

  readonly repriseForm = this.fb.group({
    dateHeureReprise: [this.nowLocalInput()],
    signatureRedemarrage: [null as string | null],
    signataireRedemarrageNom: [this.auth.displayName()]
  });

  ngOnInit(): void {
    this.load();
    this.arretForm.controls.registreTechniqueAJour.valueChanges.subscribe((checked) => {
      const ctrl = this.arretForm.controls.signatureArret;
      if (checked) {
        ctrl.setValidators([Validators.required]);
      } else {
        ctrl.clearValidators();
        ctrl.setValue(null);
      }
      ctrl.updateValueAndValidity();
    });
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      masses: this.masService.list(),
      active: this.arretService.listActive(),
      history: this.arretService.history()
    }).subscribe({
      next: ({ masses, active, history }) => {
        const activeMasIds = new Set(active.map((a) => a.masId));
        this.masses.set(
          [...masses]
            .filter((m) => !activeMasIds.has(m.id))
            .sort((a, b) => a.numero.localeCompare(b.numero, 'fr', { numeric: true }))
        );
        this.active.set(active);
        this.history.set(history.filter((h) => !h.actif));
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Impossible de charger les arrêts maintenance.'));
      }
    });
  }

  submitArret(): void {
    this.error.set(null);
    this.success.set(null);
    if (this.arretForm.invalid) {
      this.arretForm.markAllAsTouched();
      this.error.set('Complétez la MAS, le motif et la signature si le registre technique est à jour.');
      return;
    }
    const v = this.arretForm.getRawValue();
    if (v.masId == null) {
      return;
    }
    if (v.registreTechniqueAJour && !v.signatureArret) {
      this.error.set('Signature d’arrêt obligatoire lorsque le registre technique est à jour.');
      return;
    }
    this.saving.set(true);
    this.arretService
      .declareArret({
        masId: v.masId,
        motifArret: (v.motifArret || '').trim(),
        dateHeureArret: this.toIsoDateTime(v.dateHeureArret),
        registreTechniqueAJour: !!v.registreTechniqueAJour,
        signatureArret: v.signatureArret,
        signataireArretNom: v.signataireArretNom?.trim() || this.auth.displayName()
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.success.set('Arrêt pour maintenance enregistré.');
          this.arretForm.reset({
            masId: null,
            dateHeureArret: this.nowLocalInput(),
            motifArret: '',
            registreTechniqueAJour: false,
            signatureArret: null,
            signataireArretNom: this.auth.displayName()
          });
          this.load();
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(apiErrorMessage(err, 'Enregistrement de l’arrêt impossible.'));
        }
      });
  }

  openReprise(item: ArretMaintenance): void {
    this.repriseId.set(item.id);
    this.repriseForm.reset({
      dateHeureReprise: this.nowLocalInput(),
      signatureRedemarrage: null,
      signataireRedemarrageNom: this.auth.displayName()
    });
    const sig = this.repriseForm.controls.signatureRedemarrage;
    if (item.registreTechniqueAJour) {
      sig.setValidators([Validators.required]);
    } else {
      sig.clearValidators();
    }
    sig.updateValueAndValidity();
  }

  cancelReprise(): void {
    this.repriseId.set(null);
  }

  submitReprise(item: ArretMaintenance): void {
    this.error.set(null);
    this.success.set(null);
    if (item.registreTechniqueAJour && !this.repriseForm.controls.signatureRedemarrage.value) {
      this.error.set('Signature de redémarrage obligatoire (registre technique à jour).');
      return;
    }
    const v = this.repriseForm.getRawValue();
    this.saving.set(true);
    this.arretService
      .declareReprise(item.id, {
        dateHeureReprise: this.toIsoDateTime(v.dateHeureReprise),
        signatureRedemarrage: v.signatureRedemarrage,
        signataireRedemarrageNom: v.signataireRedemarrageNom?.trim() || this.auth.displayName()
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.success.set(`Reprise enregistrée pour la MAS ${item.masNumero}.`);
          this.repriseId.set(null);
          this.load();
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(apiErrorMessage(err, 'Enregistrement de la reprise impossible.'));
        }
      });
  }

  formatDateTime(value?: string | null): string {
    return this.arretService.formatDateTime(value);
  }

  masLabel(mas: Mas): string {
    const marque = (mas.marqueLabel || mas.marque || '').trim();
    return marque ? `${mas.numero} — ${marque}` : mas.numero;
  }

  private nowLocalInput(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  private toIsoDateTime(value?: string | null): string | null {
    if (!value) {
      return null;
    }
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) {
      return null;
    }
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
  }
}
