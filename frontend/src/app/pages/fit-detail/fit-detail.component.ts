import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Fit, FitSignataire, Intervention } from '../../models/models';
import { AuthService } from '../../services/auth.service';
import { FitService } from '../../services/fit.service';
import { InterventionService } from '../../services/intervention.service';
import { SignaturePadComponent } from '../../shared/signature-pad.component';
import { apiErrorMessage } from '../../shared/api-error';

/**
 * Détail d'une FIT : historique + ajout d'une ligne signée (admin + technicien).
 */
@Component({
  selector: 'app-fit-detail',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule,
    SignaturePadComponent
  ],
  templateUrl: './fit-detail.component.html',
  styleUrl: './fit-detail.component.scss'
})
export class FitDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly fitService = inject(FitService);
  private readonly interventionService = inject(InterventionService);
  private readonly auth = inject(AuthService);

  readonly fit = signal<Fit | null>(null);
  readonly interventions = signal<Intervention[]>([]);
  readonly admins = signal<FitSignataire[]>([]);
  readonly techniciens = signal<FitSignataire[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);
  readonly showForm = signal(false);

  readonly form = this.fb.group({
    dateOperation: [this.today(), Validators.required],
    numeroSocle: [''],
    numeroEmplacement: [''],
    motifNatureOperations: ['', [Validators.required, Validators.maxLength(2000)]],
    interventionId: [null as number | null],
    signatureAdmin: [null as string | null, Validators.required],
    signatureTechnicien: [null as string | null, Validators.required],
    signataireAdminId: [null as number | null, Validators.required],
    signataireTechnicienId: [null as number | null, Validators.required]
  });

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isFinite(id)) {
      void this.router.navigate(['/mas/fit']);
      return;
    }
    this.loadSignataires();
    this.load(id);
  }

  load(id: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.fitService.get(id).subscribe({
      next: (fit) => {
        this.fit.set(fit);
        this.loading.set(false);
        this.loadInterventions(fit.masId ?? null);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'FIT introuvable.'));
      }
    });
  }

  private loadInterventions(masId: number | null): void {
    if (masId == null) {
      this.interventionService.list().subscribe({
        next: (list) => this.interventions.set(list),
        error: () => this.interventions.set([])
      });
      return;
    }
    this.interventionService.listByMas(masId).subscribe({
      next: (list) => this.interventions.set(list),
      error: () => this.interventions.set([])
    });
  }

  interventionLabel(item: Intervention): string {
    const date = item.dateIntervention
      ? new Date(item.dateIntervention).toLocaleDateString('fr-FR')
      : '';
    return `${item.numero}${date ? ' — ' + date : ''}${item.motif ? ' — ' + item.motif : ''}`;
  }

  submitLigne(): void {
    this.error.set(null);
    this.success.set(null);
    const fit = this.fit();
    if (!fit) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Complétez le motif, les signataires et les deux signatures dessinées.');
      return;
    }
    const v = this.form.getRawValue();
    if (!v.signatureAdmin || !v.signatureTechnicien) {
      this.error.set('Les signatures admin et technicien sont obligatoires (dessin).');
      return;
    }
    const adminNom = this.displayNameById(this.admins(), v.signataireAdminId);
    const techNom = this.displayNameById(this.techniciens(), v.signataireTechnicienId);
    if (!adminNom || !techNom) {
      this.error.set('Sélectionnez un signataire admin et un technicien.');
      return;
    }
    this.saving.set(true);
    this.fitService
      .addLigne(fit.id, {
        dateOperation: v.dateOperation || this.today(),
        numeroSocle: v.numeroSocle?.trim() || null,
        numeroEmplacement: v.numeroEmplacement?.trim() || null,
        motifNatureOperations: (v.motifNatureOperations || '').trim(),
        signatureAdmin: v.signatureAdmin,
        signatureTechnicien: v.signatureTechnicien,
        signataireAdminNom: adminNom,
        signataireTechnicienNom: techNom,
        interventionId: v.interventionId
      })
      .subscribe({
        next: (updated) => {
          this.saving.set(false);
          this.fit.set(updated);
          this.success.set('Ligne FIT enregistrée avec signatures.');
          this.showForm.set(false);
          this.form.reset({
            dateOperation: this.today(),
            numeroSocle: '',
            numeroEmplacement: '',
            motifNatureOperations: '',
            interventionId: null,
            signatureAdmin: null,
            signatureTechnicien: null,
            signataireAdminId: null,
            signataireTechnicienId: null
          });
          this.preselectCurrentUser();
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(apiErrorMessage(err, 'Enregistrement de la ligne FIT impossible.'));
        }
      });
  }

  private loadSignataires(): void {
    this.fitService.listSignataires().subscribe({
      next: (signataires) => {
        this.admins.set(signataires.admins || []);
        this.techniciens.set(signataires.techniciens || []);
        this.preselectCurrentUser();
      },
      error: () => {
        this.admins.set([]);
        this.techniciens.set([]);
      }
    });
  }

  private preselectCurrentUser(): void {
    const username = this.auth.username();
    if (!username) {
      return;
    }
    if (this.auth.isAdmin()) {
      const me = this.admins().find((u) => u.username === username);
      if (me) {
        this.form.patchValue({ signataireAdminId: me.id });
      }
    }
    if (this.auth.isTechnicien()) {
      const me = this.techniciens().find((u) => u.username === username);
      if (me) {
        this.form.patchValue({ signataireTechnicienId: me.id });
      }
    }
  }

  private displayNameById(list: FitSignataire[], id: number | null | undefined): string | null {
    if (id == null) {
      return null;
    }
    const found = list.find((u) => u.id === id);
    return found?.displayName?.trim() || found?.username || null;
  }

  private today(): string {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }
}
