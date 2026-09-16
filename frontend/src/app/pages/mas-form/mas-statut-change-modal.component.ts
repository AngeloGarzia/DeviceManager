import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { forkJoin } from 'rxjs';
import {
  CasinoSummary,
  FitSignataire,
  MasStatut,
  MasStatutChangeRequest,
  Sfm
} from '../../models/models';
import { AtelierService } from '../../services/atelier.service';
import { AuthService } from '../../services/auth.service';
import { FitService } from '../../services/fit.service';
import { SfmService } from '../../services/sfm.service';
import { SignaturePadComponent } from '../../shared/signature-pad.component';
import { apiErrorMessage } from '../../shared/api-error';

export interface MasStatutChangeDialogData {
  targetStatut: MasStatut;
}

export interface MasStatutChangeDialogResult {
  statutChange: MasStatutChangeRequest;
  dateCessation: string | null;
  destinationMachineUsagee: string | null;
}

const STATUT_LABELS: Record<MasStatut, string> = {
  UTILISEE: 'Machine utilisée',
  EN_RESERVE: 'En réserve',
  VENDUE: 'Vendue',
  DETRUITE: 'Détruite'
};

/**
 * Modale obligatoire lors d'un changement de statut MAS : champs métier + 2 signatures FIT.
 */
@Component({
  selector: 'app-mas-statut-change-modal',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatIconModule,
    SignaturePadComponent
  ],
  templateUrl: './mas-statut-change-modal.component.html',
  styleUrl: './mas-statut-change-modal.component.scss'
})
export class MasStatutChangeModalComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly dialogRef = inject(MatDialogRef<MasStatutChangeModalComponent, MasStatutChangeDialogResult>);
  readonly data = inject<MasStatutChangeDialogData>(MAT_DIALOG_DATA);
  private readonly fitService = inject(FitService);
  private readonly atelierService = inject(AtelierService);
  private readonly sfmService = inject(SfmService);
  private readonly auth = inject(AuthService);

  readonly targetStatut = this.data.targetStatut;
  readonly statutLabel = STATUT_LABELS[this.targetStatut];
  readonly casinos: CasinoSummary[] = [];
  readonly sfms: Sfm[] = [];
  readonly admins: FitSignataire[] = [];
  readonly techniciens: FitSignataire[] = [];
  loading = true;
  error: string | null = null;

  readonly form = this.fb.group({
    dateOperation: [this.today(), Validators.required],
    dateCessation: [''],
    acheteurType: ['CASINO' as 'CASINO' | 'SFM'],
    casinoAcheteurId: [null as number | null],
    sfmAcheteurId: [null as number | null],
    motifNatureOperations: ['', Validators.maxLength(2000)],
    signatureAdmin: [null as string | null, Validators.required],
    signatureTechnicien: [null as string | null, Validators.required],
    signataireAdminId: [null as number | null, Validators.required],
    signataireTechnicienId: [null as number | null, Validators.required]
  });

  ngOnInit(): void {
    this.applyStatutValidators();
    forkJoin({
      signataires: this.fitService.listSignataires(),
      casinos: this.atelierService.listCasinos(),
      sfms: this.sfmService.list()
    }).subscribe({
      next: ({ signataires, casinos, sfms }) => {
        this.admins.splice(0, this.admins.length, ...(signataires.admins || []));
        this.techniciens.splice(0, this.techniciens.length, ...(signataires.techniciens || []));
        this.casinos.splice(
          0,
          this.casinos.length,
          ...[...casinos].sort((a, b) => a.nom.localeCompare(b.nom, 'fr'))
        );
        this.sfms.splice(
          0,
          this.sfms.length,
          ...[...sfms].sort((a, b) => a.nom.localeCompare(b.nom, 'fr'))
        );
        this.preselectCurrentUser();
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = apiErrorMessage(err, 'Impossible de charger la modale.');
      }
    });
  }

  isVendue(): boolean {
    return this.targetStatut === 'VENDUE';
  }

  isDetruite(): boolean {
    return this.targetStatut === 'DETRUITE';
  }

  isEnReserve(): boolean {
    return this.targetStatut === 'EN_RESERVE';
  }

  isUtilisee(): boolean {
    return this.targetStatut === 'UTILISEE';
  }

  cancel(): void {
    this.dialogRef.close();
  }

  confirm(): void {
    this.error = null;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error = 'Complétez les champs obligatoires et les deux signatures.';
      return;
    }
    const v = this.form.getRawValue();
    if (!v.signatureAdmin || !v.signatureTechnicien) {
      this.error = 'Les deux signatures sont obligatoires.';
      return;
    }
    const adminNom = this.displayNameById(this.admins, v.signataireAdminId);
    const techNom = this.displayNameById(this.techniciens, v.signataireTechnicienId);
    if (!adminNom || !techNom) {
      this.error = 'Sélectionnez un signataire admin et un technicien.';
      return;
    }

    const dateCessation: string | null = v.dateCessation?.trim() || null;
    let destination: string | null = null;
    const statutChange: MasStatutChangeRequest = {
      dateOperation: v.dateOperation || this.today(),
      signatureAdmin: v.signatureAdmin,
      signatureTechnicien: v.signatureTechnicien,
      signataireAdminNom: adminNom,
      signataireTechnicienNom: techNom,
      motifNatureOperations: v.motifNatureOperations?.trim() || null
    };

    if (this.isVendue()) {
      if (!dateCessation) {
        this.error = 'La date de vente est obligatoire.';
        return;
      }
      statutChange.dateCessation = dateCessation;
      statutChange.acheteurType = v.acheteurType || 'CASINO';
      if (statutChange.acheteurType === 'CASINO') {
        if (v.casinoAcheteurId == null) {
          this.error = 'Sélectionnez le casino acheteur.';
          return;
        }
        statutChange.casinoAcheteurId = v.casinoAcheteurId;
        const casino = this.casinos.find((c) => c.id === v.casinoAcheteurId);
        destination = casino ? `Casino ${casino.nom}` : null;
      } else {
        if (v.sfmAcheteurId == null) {
          this.error = 'Sélectionnez le SFM acheteur.';
          return;
        }
        statutChange.sfmAcheteurId = v.sfmAcheteurId;
        const sfm = this.sfms.find((s) => s.id === v.sfmAcheteurId);
        destination = sfm ? `SFM ${sfm.nom}` : null;
      }
      statutChange.destinationMachineUsagee = destination;
    } else if (this.isDetruite()) {
      if (dateCessation) {
        statutChange.dateCessation = dateCessation;
      }
      destination = 'Destruction';
      statutChange.destinationMachineUsagee = destination;
    } else if (this.isEnReserve()) {
      if (dateCessation) {
        statutChange.dateCessation = dateCessation;
      }
    }

    this.dialogRef.close({
      statutChange,
      dateCessation: this.isUtilisee() ? null : dateCessation,
      destinationMachineUsagee: this.isUtilisee() ? null : destination
    });
  }

  private applyStatutValidators(): void {
    const cessationCtrl = this.form.controls.dateCessation;
    if (this.isVendue()) {
      cessationCtrl.setValidators([Validators.required]);
    } else {
      cessationCtrl.clearValidators();
    }
    cessationCtrl.updateValueAndValidity();
  }

  private preselectCurrentUser(): void {
    const username = this.auth.username();
    if (!username) {
      return;
    }
    if (this.auth.isAdmin()) {
      const me = this.admins.find((u) => u.username === username);
      if (me) {
        this.form.patchValue({ signataireAdminId: me.id });
      }
    }
    if (this.auth.isTechnicien()) {
      const me = this.techniciens.find((u) => u.username === username);
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
