import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MasService } from '../../services/mas.service';
import { DenoOption, MarqueMasOption, MasForm, MasStatut } from '../../models/models';
import { apiErrorMessage } from '../../shared/api-error';

const MAS_STATUT_OPTIONS: { value: MasStatut; label: string }[] = [
  { value: 'UTILISEE', label: 'Machine utilisée' },
  { value: 'EN_RESERVE', label: 'En réserve' },
  { value: 'VENDUE', label: 'Vendue' },
  { value: 'DETRUITE', label: 'Détruite' }
];

const MAS_TYPE_OPTIONS = [
  'Machine à sous',
  'Poker',
  'Roulette',
  'Blackjack',
  'Bingo',
  'Autre'
];

/**
 * Formulaire de création ou modification d'une MAS.
 * Permet aussi la création inline d'une marque / dénomination.
 */
@Component({
  selector: 'app-mas-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatCardModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './mas-form.component.html',
  styleUrl: './mas-form.component.scss'
})
export class MasFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly masService = inject(MasService);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly savingMarque = signal(false);
  readonly savingDeno = signal(false);
  readonly showNewMarque = signal(false);
  readonly showNewDeno = signal(false);
  readonly error = signal<string | null>(null);
  readonly marqueError = signal<string | null>(null);
  readonly denoError = signal<string | null>(null);
  readonly marques = signal<MarqueMasOption[]>([]);
  readonly denos = signal<DenoOption[]>([]);
  readonly statutOptions = MAS_STATUT_OPTIONS;
  readonly typeOptions = MAS_TYPE_OPTIONS;
  id: number | null = null;
  returnDevice: string | null = null;
  private returnForOrderRequest = false;

  readonly form = this.fb.group({
    numero: ['', [Validators.required, Validators.maxLength(80)]],
    numeroSocle: ['', Validators.maxLength(80)],
    tauxRedistribution: [null as number | null, [Validators.min(0), Validators.max(100)]],
    dateMiseEnService: [''],
    typeMachine: ['', Validators.maxLength(120)],
    numeroSerie: ['', Validators.maxLength(120)],
    dateCessation: [''],
    destinationMachineUsagee: ['', Validators.maxLength(255)],
    marqueId: [null as number | null, Validators.required],
    denoId: [null as number | null],
    multiDeno: [false],
    statut: ['UTILISEE' as string, Validators.required]
  });

  readonly newMarqueForm = this.fb.nonNullable.group({
    label: ['', [Validators.required, Validators.maxLength(120)]]
  });

  readonly newDenoForm = this.fb.nonNullable.group({
    valeur: [null as number | null, [Validators.required, Validators.min(0.0001)]]
  });

  get isEdit(): boolean {
    return this.id !== null;
  }

  /** Date de cessation : accessible si Vendue, En réserve ou Détruite. */
  canEditDateCessation(): boolean {
    const s = this.form.controls.statut.value;
    return s === 'VENDUE' || s === 'EN_RESERVE' || s === 'DETRUITE';
  }

  /** Destination machine usagée : accessible uniquement si Vendue. */
  canEditDestination(): boolean {
    return this.form.controls.statut.value === 'VENDUE';
  }

  isStatutChecked(value: string): boolean {
    return this.form.controls.statut.value === value;
  }

  /** Cases à cocher exclusives : un seul statut à la fois. */
  onStatutToggle(value: MasStatut, checked: boolean): void {
    if (checked) {
      this.form.patchValue({ statut: value });
      this.syncStatutDependentFields();
      return;
    }
    if (this.form.controls.statut.value === value) {
      this.form.patchValue({ statut: 'UTILISEE' });
      this.syncStatutDependentFields();
    }
  }

  private syncStatutDependentFields(): void {
    const dateCtrl = this.form.controls.dateCessation;
    const destCtrl = this.form.controls.destinationMachineUsagee;

    if (this.canEditDateCessation()) {
      dateCtrl.enable({ emitEvent: false });
    } else {
      dateCtrl.setValue('', { emitEvent: false });
      dateCtrl.disable({ emitEvent: false });
    }

    if (this.canEditDestination()) {
      destCtrl.enable({ emitEvent: false });
    } else {
      destCtrl.setValue('', { emitEvent: false });
      destCtrl.disable({ emitEvent: false });
    }
  }

  /** Multi-déno : désactive la sélection d'une dénomination unique. */
  isMultiDeno(): boolean {
    return !!this.form.controls.multiDeno.value;
  }

  private syncMultiDenoFields(): void {
    const denoCtrl = this.form.controls.denoId;
    if (this.isMultiDeno()) {
      denoCtrl.setValue(null, { emitEvent: false });
      denoCtrl.disable({ emitEvent: false });
      this.showNewDeno.set(false);
      return;
    }
    denoCtrl.enable({ emitEvent: false });
  }

  ngOnInit(): void {
    this.returnDevice = this.route.snapshot.queryParamMap.get('returnDevice');
    this.returnForOrderRequest = this.route.snapshot.queryParamMap.get('forOrderRequest') === '1';
    this.loadMarques();
    this.loadDenos();
    this.syncStatutDependentFields();
    this.syncMultiDenoFields();
    this.form.controls.statut.valueChanges.subscribe(() => this.syncStatutDependentFields());
    this.form.controls.multiDeno.valueChanges.subscribe(() => this.syncMultiDenoFields());

    const rawId = this.route.snapshot.paramMap.get('id');
    if (rawId) {
      this.id = Number(rawId);
      this.loading.set(true);
      this.masService.get(this.id).subscribe({
        next: (mas) => {
          this.form.patchValue({
            numero: mas.numero,
            numeroSocle: mas.numeroSocle || '',
            tauxRedistribution: mas.tauxRedistribution ?? null,
            dateMiseEnService: mas.dateMiseEnService || '',
            typeMachine: mas.typeMachine || '',
            numeroSerie: mas.numeroSerie || '',
            dateCessation: mas.dateCessation || '',
            destinationMachineUsagee: mas.destinationMachineUsagee || '',
            marqueId: mas.marqueId,
            denoId: mas.multiDeno ? null : (mas.denoId ?? null),
            multiDeno: !!mas.multiDeno,
            statut: mas.statut || (mas.utilise ? 'UTILISEE' : 'EN_RESERVE')
          });
          this.syncStatutDependentFields();
          this.syncMultiDenoFields();
          this.loading.set(false);
        },
        error: () => {
          this.error.set('MAS introuvable.');
          this.loading.set(false);
        }
      });
    }
  }

  loadMarques(selectId?: number): void {
    this.masService.listMarques().subscribe({
      next: (data) => {
        this.marques.set(this.sortMarques(data));
        if (selectId != null) {
          this.form.patchValue({ marqueId: selectId });
        }
      }
    });
  }

  loadDenos(selectId?: number): void {
    this.masService.listDenos().subscribe({
      next: (data) => {
        this.denos.set([...data].sort((a, b) => Number(a.valeur) - Number(b.valeur)));
        if (selectId != null) {
          this.form.patchValue({ denoId: selectId });
        }
      }
    });
  }

  private sortMarques(data: MarqueMasOption[]): MarqueMasOption[] {
    return [...data].sort((a, b) =>
      (a.label || '').localeCompare(b.label || '', 'fr', { sensitivity: 'base' })
    );
  }

  openNewMarque(): void {
    this.showNewMarque.set(true);
    this.marqueError.set(null);
    this.newMarqueForm.reset({ label: '' });
  }

  cancelNewMarque(): void {
    this.showNewMarque.set(false);
    this.marqueError.set(null);
  }

  createMarque(): void {
    if (this.newMarqueForm.invalid) {
      this.newMarqueForm.markAllAsTouched();
      return;
    }
    this.savingMarque.set(true);
    this.marqueError.set(null);
    const label = this.newMarqueForm.controls.label.value.trim();
    this.masService.createMarque(label).subscribe({
      next: (created) => {
        this.savingMarque.set(false);
        this.showNewMarque.set(false);
        this.loadMarques(created.id ?? created.value ?? undefined);
      },
      error: (err) => {
        this.savingMarque.set(false);
        this.marqueError.set(apiErrorMessage(err, 'Création de la marque impossible.'));
      }
    });
  }

  openNewDeno(): void {
    if (this.isMultiDeno()) {
      return;
    }
    this.showNewDeno.set(true);
    this.denoError.set(null);
    this.newDenoForm.reset({ valeur: null });
  }

  cancelNewDeno(): void {
    this.showNewDeno.set(false);
    this.denoError.set(null);
  }

  createDeno(): void {
    if (this.newDenoForm.invalid) {
      this.newDenoForm.markAllAsTouched();
      return;
    }
    this.savingDeno.set(true);
    this.denoError.set(null);
    const valeur = Number(this.newDenoForm.controls.valeur.value);
    if (!Number.isFinite(valeur) || valeur <= 0) {
      this.denoError.set('Saisissez une valeur strictement positive (ex. 0.50).');
      this.savingDeno.set(false);
      return;
    }
    this.masService.createDeno(valeur).subscribe({
      next: (created) => {
        const id = created.id ?? created.value ?? null;
        this.savingDeno.set(false);
        this.showNewDeno.set(false);
        this.denoError.set(null);
        if (id != null) {
          this.denos.update((list) => {
            const without = list.filter((d) => d.id !== id);
            return [...without, created].sort((a, b) => Number(a.valeur) - Number(b.valeur));
          });
          this.form.patchValue({ denoId: id });
        } else {
          this.loadDenos();
        }
      },
      error: (err) => {
        this.savingDeno.set(false);
        this.denoError.set(apiErrorMessage(err, 'Création de la dénomination impossible.'));
      }
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const raw = this.form.getRawValue();
    const tauxRaw = raw.tauxRedistribution;
    const taux =
      tauxRaw === null || tauxRaw === undefined || String(tauxRaw).trim() === ''
        ? null
        : Number(tauxRaw);
    const payload: MasForm = {
      numero: raw.numero!.trim(),
      numeroSocle: raw.numeroSocle?.trim() || null,
      tauxRedistribution: Number.isFinite(taux as number) ? (taux as number) : null,
      dateMiseEnService: raw.dateMiseEnService?.trim() || null,
      typeMachine: raw.typeMachine?.trim() || null,
      numeroSerie: raw.numeroSerie?.trim() || null,
      dateCessation: this.canEditDateCessation()
        ? raw.dateCessation?.trim() || null
        : null,
      destinationMachineUsagee: this.canEditDestination()
        ? raw.destinationMachineUsagee?.trim() || null
        : null,
      marqueId: raw.marqueId,
      denoId: raw.multiDeno ? null : raw.denoId,
      multiDeno: !!raw.multiDeno,
      statut: raw.statut || 'UTILISEE',
      utilise: (raw.statut || 'UTILISEE') === 'UTILISEE'
    };
    const req$ = this.id ? this.masService.update(this.id, payload) : this.masService.create(payload);
    req$.subscribe({
      next: (saved) => this.navigateAfterSave(saved.id),
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Enregistrement impossible.'));
      }
    });
  }

  cancel(): void {
    if (this.returnToDeviceForm()) {
      return;
    }
    if (this.id) {
      this.router.navigate(['/mas', this.id]);
    } else {
      this.router.navigate(['/mas']);
    }
  }

  private navigateAfterSave(masId: number): void {
    if (this.returnToDeviceForm({ masId: String(masId) })) {
      return;
    }
    this.router.navigate(['/mas', masId]);
  }

  private returnToDeviceForm(extra: Record<string, string | number> = {}): boolean {
    if (!this.returnDevice) {
      return false;
    }
    const queryParams: Record<string, string> = {};
    for (const [key, value] of Object.entries(extra)) {
      queryParams[key] = String(value);
    }
    if (this.returnForOrderRequest) {
      queryParams['forOrderRequest'] = '1';
    }
    if (this.returnDevice === 'new') {
      void this.router.navigate(['/devices/new'], { queryParams });
    } else {
      void this.router.navigate(['/devices', this.returnDevice, 'edit'], { queryParams });
    }
    return true;
  }
}
