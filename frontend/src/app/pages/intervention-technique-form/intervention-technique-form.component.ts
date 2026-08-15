import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin } from 'rxjs';
import {
  FitSignataire,
  Intervention,
  InterventionTechnique,
  Mas,
  OrderRequest
} from '../../models/models';
import { AuthService } from '../../services/auth.service';
import { FitService } from '../../services/fit.service';
import { InterventionService } from '../../services/intervention.service';
import { InterventionTechniqueService } from '../../services/intervention-technique.service';
import { MasService } from '../../services/mas.service';
import { OrderRequestService } from '../../services/order-request.service';
import { SignaturePadComponent } from '../../shared/signature-pad.component';
import { apiErrorMessage } from '../../shared/api-error';

type FollowUpAction = 'bon' | 'fit' | 'done';

/**
 * Création d'intervention(s) technique(s) : une ligne DB par MAS sélectionnée.
 */
@Component({
  selector: 'app-intervention-technique-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule,
    SignaturePadComponent
  ],
  templateUrl: './intervention-technique-form.component.html',
  styleUrl: './intervention-technique-form.component.scss'
})
export class InterventionTechniqueFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly masService = inject(MasService);
  private readonly fitService = inject(FitService);
  private readonly interventionService = inject(InterventionService);
  private readonly orderService = inject(OrderRequestService);
  private readonly techniqueService = inject(InterventionTechniqueService);
  private readonly auth = inject(AuthService);

  readonly masses = signal<Mas[]>([]);
  readonly bons = signal<Intervention[]>([]);
  readonly commandes = signal<OrderRequest[]>([]);
  readonly admins = signal<FitSignataire[]>([]);
  readonly techniciens = signal<FitSignataire[]>([]);
  readonly loading = signal(false);
  readonly loadingLinks = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  /** Modale post-enregistrement. */
  readonly followUpOpen = signal(false);
  readonly createdItems = signal<InterventionTechnique[]>([]);
  readonly followUpMasId = signal<number | null>(null);

  readonly form = this.fb.group({
    dateIntervention: [this.defaultDateTimeLocal(), Validators.required],
    masIds: [[] as number[], Validators.required],
    emplacement: [''],
    motif: ['', [Validators.required, Validators.maxLength(500)]],
    diagnostic: [''],
    travaux: ['', [Validators.required, Validators.maxLength(2000)]],
    observations: [''],
    associerFit: [false],
    signatureAdmin: [null as string | null],
    signatureTechnicien: [null as string | null],
    signataireAdminId: [null as number | null],
    signataireTechnicienId: [null as number | null],
    commandeId: [null as number | null],
    bonInterventionId: [null as number | null]
  });

  requiresFitSignatures(): boolean {
    return !!this.form.controls.associerFit.value;
  }

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
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Impossible de charger le formulaire.'));
      }
    });

    this.form.controls.masIds.valueChanges.subscribe((ids) => {
      this.refreshLinkedRecords((ids || []).filter((id): id is number => id != null));
    });
  }

  masLabel(mas: Mas): string {
    const marque = (mas.marqueLabel || mas.marque || '').trim();
    return marque ? `${mas.numero} — ${marque}` : mas.numero;
  }

  createdMasOptions(): { id: number; label: string }[] {
    const seen = new Set<number>();
    const out: { id: number; label: string }[] = [];
    for (const item of this.createdItems()) {
      if (item.masId == null || seen.has(item.masId)) {
        continue;
      }
      seen.add(item.masId);
      const marque = (item.masMarque || '').trim();
      out.push({
        id: item.masId,
        label: marque ? `${item.masNumero} — ${marque}` : item.masNumero || String(item.masId)
      });
    }
    return out;
  }

  submit(): void {
    this.error.set(null);
    const v = this.form.getRawValue();
    const masIds = (v.masIds || []).filter((id): id is number => id != null);
    if (masIds.length === 0) {
      this.form.markAllAsTouched();
      this.error.set('Sélectionnez au moins une MAS.');
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Complétez les champs obligatoires.');
      return;
    }
    const associerFit = !!v.associerFit;
    if (associerFit && (!v.signatureAdmin || !v.signatureTechnicien)) {
      this.error.set('Pour lier à la FIT, les deux signatures dessinées sont obligatoires.');
      return;
    }
    const adminNom = associerFit
      ? this.displayNameById(this.admins(), v.signataireAdminId)
      : null;
    const techNom = associerFit
      ? this.displayNameById(this.techniciens(), v.signataireTechnicienId)
      : null;
    if (associerFit && (!adminNom || !techNom)) {
      this.error.set('Sélectionnez les signataires admin et technicien.');
      return;
    }

    this.saving.set(true);
    this.techniqueService
      .create({
        dateIntervention: this.toIsoLocal(v.dateIntervention || ''),
        masIds,
        emplacement: v.emplacement?.trim() || null,
        motif: (v.motif || '').trim(),
        diagnostic: v.diagnostic?.trim() || null,
        travaux: (v.travaux || '').trim(),
        observations: v.observations?.trim() || null,
        associerFit,
        signatureAdmin: associerFit ? v.signatureAdmin : null,
        signatureTechnicien: associerFit ? v.signatureTechnicien : null,
        signataireAdminNom: adminNom,
        signataireTechnicienNom: techNom,
        commandeId: v.commandeId,
        bonInterventionId: v.bonInterventionId
      })
      .subscribe({
        next: (created) => {
          this.saving.set(false);
          this.createdItems.set(created);
          this.followUpMasId.set(created[0]?.masId ?? masIds[0] ?? null);
          this.followUpOpen.set(true);
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(apiErrorMessage(err, 'Enregistrement impossible.'));
        }
      });
  }

  chooseFollowUp(action: FollowUpAction): void {
    const masId = this.followUpMasId();
    const sample = this.createdItems()[0];
    const motif = sample?.motif || this.form.controls.motif.value || '';
    const travaux = sample?.travaux || this.form.controls.travaux.value || '';
    const diagnostic = sample?.diagnostic || this.form.controls.diagnostic.value || '';
    const emplacement = sample?.emplacement || this.form.controls.emplacement.value || '';
    const observations = sample?.observations || this.form.controls.observations.value || '';

    this.followUpOpen.set(false);

    if (action === 'bon') {
      void this.router.navigate(['/devices/utiliser'], {
        queryParams: {
          masId: masId ?? undefined,
          motif,
          travaux,
          diagnostic: diagnostic || undefined,
          emplacement: emplacement || undefined,
          observations: observations || undefined,
          fromInterventionTechnique: sample?.id ?? undefined
        }
      });
      return;
    }

    if (action === 'fit') {
      const motifFit = [
        motif ? `Motif : ${motif}` : '',
        travaux ? `Travaux : ${travaux}` : ''
      ]
        .filter(Boolean)
        .join('\n')
        .slice(0, 2000);
      void this.router.navigate(['/mas/fit/new'], {
        queryParams: {
          masId: masId ?? undefined,
          motif: motifFit || undefined,
          fromInterventionTechnique: sample?.id ?? undefined
        }
      });
      return;
    }

    void this.router.navigate(['/mas/interventions'], {
      queryParams: { created: this.createdItems().length }
    });
  }

  private refreshLinkedRecords(masIds: number[]): void {
    this.form.patchValue(
      { commandeId: null, bonInterventionId: null },
      { emitEvent: false }
    );
    if (masIds.length === 0) {
      this.bons.set([]);
      this.commandes.set([]);
      return;
    }
    this.loadingLinks.set(true);
    forkJoin({
      bons: forkJoin(masIds.map((id) => this.interventionService.listByMas(id))),
      commandes: this.orderService.list({ masIds })
    }).subscribe({
      next: ({ bons, commandes }) => {
        const byId = new Map<number, Intervention>();
        for (const list of bons) {
          for (const b of list) {
            byId.set(b.id, b);
          }
        }
        this.bons.set(
          [...byId.values()].sort((a, b) =>
            (b.dateIntervention || '').localeCompare(a.dateIntervention || '')
          )
        );
        this.commandes.set(commandes);
        this.loadingLinks.set(false);
      },
      error: () => {
        this.bons.set([]);
        this.commandes.set([]);
        this.loadingLinks.set(false);
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

  private defaultDateTimeLocal(): string {
    const d = new Date();
    d.setSeconds(0, 0);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  private toIsoLocal(value: string): string {
    if (!value) {
      return new Date().toISOString().slice(0, 19);
    }
    return value.length === 16 ? `${value}:00` : value;
  }
}
