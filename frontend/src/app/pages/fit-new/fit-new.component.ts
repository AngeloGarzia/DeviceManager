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
import { forkJoin } from 'rxjs';
import { FitSignataire, Intervention, Mas } from '../../models/models';
import { AuthService } from '../../services/auth.service';
import { FitService } from '../../services/fit.service';
import { InterventionService } from '../../services/intervention.service';
import { MasService } from '../../services/mas.service';
import { SignaturePadComponent } from '../../shared/signature-pad.component';
import { apiErrorMessage } from '../../shared/api-error';

/**
 * Crée (si besoin) la FIT d'une MAS puis ajoute une ligne d'intervention signée.
 */
@Component({
  selector: 'app-fit-new',
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
  templateUrl: './fit-new.component.html',
  styleUrl: './fit-new.component.scss'
})
export class FitNewComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly masService = inject(MasService);
  private readonly fitService = inject(FitService);
  private readonly interventionService = inject(InterventionService);
  private readonly auth = inject(AuthService);

  readonly masses = signal<Mas[]>([]);
  readonly interventions = signal<Intervention[]>([]);
  readonly admins = signal<FitSignataire[]>([]);
  readonly techniciens = signal<FitSignataire[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.group({
    masId: [null as number | null, Validators.required],
    interventionId: [null as number | null],
    dateOperation: [this.today(), Validators.required],
    numeroSocle: [''],
    numeroEmplacement: [''],
    motifNatureOperations: ['', [Validators.required, Validators.maxLength(2000)]],
    signatureAdmin: [null as string | null, Validators.required],
    signatureTechnicien: [null as string | null, Validators.required],
    signataireAdminId: [null as number | null, Validators.required],
    signataireTechnicienId: [null as number | null, Validators.required]
  });

  ngOnInit(): void {
    this.loading.set(true);
    forkJoin({
      masses: this.masService.list(),
      signataires: this.fitService.listSignataires()
    }).subscribe({
      next: ({ masses, signataires }) => {
        this.masses.set(
          [...masses].sort((a, b) => a.numero.localeCompare(b.numero, 'fr', { numeric: true }))
        );
        this.admins.set(signataires.admins || []);
        this.techniciens.set(signataires.techniciens || []);
        this.loading.set(false);
        this.preselectCurrentUser();
        const raw = this.route.snapshot.queryParamMap.get('masId');
        const fromQuery = raw ? Number(raw) : NaN;
        if (Number.isFinite(fromQuery) && masses.some((m) => m.id === fromQuery)) {
          this.form.patchValue({ masId: fromQuery });
          this.loadInterventions(fromQuery);
        }
        const motif = this.route.snapshot.queryParamMap.get('motif');
        if (motif) {
          this.form.patchValue({ motifNatureOperations: motif.slice(0, 2000) });
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Impossible de charger le formulaire FIT.'));
      }
    });

    this.form.controls.masId.valueChanges.subscribe((masId) => {
      this.form.patchValue({ interventionId: null }, { emitEvent: false });
      this.loadInterventions(masId);
    });
  }

  private loadInterventions(masId: number | null): void {
    if (masId == null) {
      this.interventions.set([]);
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

  masLabel(mas: Mas): string {
    const marque = (mas.marqueLabel || mas.marque || '').trim();
    return marque ? `${mas.numero} — ${marque}` : mas.numero;
  }

  submit(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set(
        'Sélectionnez une MAS, un motif, les signataires admin / technicien et les deux signatures.'
      );
      return;
    }
    const v = this.form.getRawValue();
    if (v.masId == null || !v.signatureAdmin || !v.signatureTechnicien) {
      this.error.set('MAS et signatures admin / technicien obligatoires.');
      return;
    }
    const adminNom = this.displayNameById(this.admins(), v.signataireAdminId);
    const techNom = this.displayNameById(this.techniciens(), v.signataireTechnicienId);
    if (!adminNom || !techNom) {
      this.error.set('Sélectionnez un signataire admin et un technicien.');
      return;
    }
    this.saving.set(true);
    this.fitService.ensureFromMas({ masId: v.masId }).subscribe({
      next: (fit) => {
        this.fitService
          .addLigne(fit.id, {
            dateOperation: v.dateOperation || this.today(),
            numeroSocle: v.numeroSocle?.trim() || null,
            numeroEmplacement: v.numeroEmplacement?.trim() || null,
            motifNatureOperations: (v.motifNatureOperations || '').trim(),
            signatureAdmin: v.signatureAdmin!,
            signatureTechnicien: v.signatureTechnicien!,
            signataireAdminNom: adminNom,
            signataireTechnicienNom: techNom,
            interventionId: v.interventionId
          })
          .subscribe({
            next: (updated) => {
              this.saving.set(false);
              void this.router.navigate(['/mas/fit', updated.id]);
            },
            error: (err) => {
              this.saving.set(false);
              this.error.set(apiErrorMessage(err, 'Enregistrement de la ligne FIT impossible.'));
            }
          });
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Création de la FIT impossible.'));
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
