import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { Sfm } from '../models/models';
import { AiFactureScanResponse } from '../services/ai.service';

export interface FactureAiDialogConfirm {
  nom: string | null;
  reference: string | null;
  numeroSerie: string | null;
  marque: string | null;
  usage: string | null;
  unitPriceHt: number | null;
  dateAcquisition: string | null;
  sfmId: number | null;
  sfmNom: string | null;
}

/**
 * Modale de validation des informations extraites par l'IA d'une facture.
 */
@Component({
  selector: 'app-facture-ai-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './facture-ai-dialog.component.html'
})
export class FactureAiDialogComponent {
  private readonly fb = inject(FormBuilder);

  @Input() open = false;
  @Input() scanning = false;
  @Input() sfms: Sfm[] = [];
  @Input() scanNotes: string | null = null;
  @Output() dismissed = new EventEmitter<void>();
  @Output() confirmed = new EventEmitter<FactureAiDialogConfirm>();

  readonly form = this.fb.group({
    nom: ['', Validators.maxLength(120)],
    reference: ['', Validators.maxLength(80)],
    numeroSerie: ['', Validators.maxLength(80)],
    marque: ['', Validators.maxLength(80)],
    usage: ['', Validators.maxLength(500)],
    unitPriceHt: [null as number | null, Validators.min(0)],
    dateAcquisition: [''],
    sfmId: [null as number | null],
    sfmNom: ['']
  });

  private lastSuggestedKey = '';

  @Input()
  set suggested(value: AiFactureScanResponse | null) {
    if (!value) {
      this.lastSuggestedKey = '';
      return;
    }
    const key = JSON.stringify(value);
    if (key === this.lastSuggestedKey) {
      return;
    }
    this.lastSuggestedKey = key;
    const matchedSfmId = this.matchSfmId(value.sfmNom || value.fournisseur);
    this.form.patchValue({
      nom: value.nom?.trim() || '',
      reference: value.reference?.trim() || '',
      numeroSerie: value.numeroSerie?.trim() || '',
      marque: value.marque?.trim() || '',
      usage: value.usage?.trim() || '',
      unitPriceHt: value.unitPriceHt ?? null,
      dateAcquisition: value.dateAcquisition?.trim() || '',
      sfmId: matchedSfmId,
      sfmNom: value.sfmNom?.trim() || value.fournisseur?.trim() || ''
    });
  }

  submit(): void {
    if (this.scanning || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    this.confirmed.emit({
      nom: raw.nom?.trim() || null,
      reference: raw.reference?.trim() || null,
      numeroSerie: raw.numeroSerie?.trim() || null,
      marque: raw.marque?.trim() || null,
      usage: raw.usage?.trim() || null,
      unitPriceHt: raw.unitPriceHt != null && raw.unitPriceHt >= 0 ? Number(raw.unitPriceHt) : null,
      dateAcquisition: raw.dateAcquisition?.trim() || null,
      sfmId: raw.sfmId ?? null,
      sfmNom: raw.sfmNom?.trim() || null
    });
  }

  private matchSfmId(name: string | null | undefined): number | null {
    if (!name?.trim() || !this.sfms.length) {
      return null;
    }
    const needle = normalizeName(name);
    const exact = this.sfms.find((s) => normalizeName(s.nom) === needle);
    if (exact) {
      return exact.id;
    }
    const partial = this.sfms.find((s) => {
      const n = normalizeName(s.nom);
      return n.includes(needle) || needle.includes(n);
    });
    return partial?.id ?? null;
  }
}

function normalizeName(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}
