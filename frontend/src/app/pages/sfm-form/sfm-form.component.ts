import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SfmService } from '../../services/sfm.service';
import { MasService } from '../../services/mas.service';
import { MarqueMasOption, SfmContact, SfmForm, SfmTechnicien } from '../../models/models';

/**
 * Formulaire de création ou modification d'un SFM (fournisseur de pièces).
 * Gère les contacts, les marques associées et le retour vers le formulaire pièce.
 */
@Component({
  selector: 'app-sfm-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCardModule,
    MatCheckboxModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './sfm-form.component.html',
  styleUrl: './sfm-form.component.scss'
})
export class SfmFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly sfmService = inject(SfmService);
  private readonly masService = inject(MasService);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly savingMarque = signal(false);
  readonly showNewMarque = signal(false);
  readonly error = signal<string | null>(null);
  readonly marqueError = signal<string | null>(null);
  readonly marques = signal<MarqueMasOption[]>([]);
  readonly techniciens = signal<SfmTechnicien[]>([]);
  /** Remise à null après chaque affectation (sélecteur global). */
  readonly assignSelectValue = signal<number | null>(null);
  id: number | null = null;
  returnDevice: string | null = null;
  private returnForOrderRequest = false;

  readonly form = this.fb.group({
    nom: ['', [Validators.required, Validators.maxLength(120)]],
    marqueIds: [[] as number[], Validators.required],
    contacts: this.fb.array([this.createContactGroup()])
  });

  readonly newMarqueForm = this.fb.nonNullable.group({
    label: ['', [Validators.required, Validators.maxLength(80)]]
  });

  /** Indique si le formulaire est en mode édition. */
  get isEdit(): boolean {
    return this.id !== null;
  }

  get contacts(): FormArray {
    return this.form.get('contacts') as FormArray;
  }

  ngOnInit(): void {
    this.returnDevice = this.route.snapshot.queryParamMap.get('returnDevice');
    this.returnForOrderRequest = this.route.snapshot.queryParamMap.get('forOrderRequest') === '1';
    this.loadMarques();
    this.loadTechniciens();

    const rawId = this.route.snapshot.paramMap.get('id');
    if (!rawId) {
      return;
    }
    this.id = Number(rawId);
    this.loading.set(true);
    this.sfmService.get(this.id).subscribe({
      next: (sfm) => {
        this.form.patchValue({
          nom: sfm.nom,
          marqueIds: sfm.marqueIds ?? sfm.marques?.map((m) => m.id) ?? []
        });
        this.contacts.clear();
        const list = sfm.contacts?.length
          ? sfm.contacts
          : [{ nom: sfm.responsable, telephone: sfm.telephone, email: sfm.email }];
        for (const c of list) {
          this.contacts.push(this.createContactGroup(c));
        }
        this.loading.set(false);
      },
      error: () => {
        this.error.set('SFM introuvable.');
        this.loading.set(false);
      }
    });
  }

  /** Charge les techniciens SFM déjà connus dans l'atelier (réutilisation). */
  loadTechniciens(): void {
    this.sfmService.listTechniciens().subscribe({
      next: (data) => this.techniciens.set(data ?? []),
      error: () => this.techniciens.set([])
    });
  }

  /** Techniciens encore disponibles pour rattachement (non déjà choisis dans ce formulaire). */
  get unusedTechniciens(): SfmTechnicien[] {
    const usedIds = new Set(
      this.contacts.controls
        .map((c) => c.get('id')?.value as number | null)
        .filter((id): id is number => id != null)
    );
    return this.techniciens().filter((t) => !usedIds.has(t.id));
  }

  /** Charge la liste des marques disponibles pour le SFM. */
  loadMarques(selectId?: number): void {
    this.masService.listMarques().subscribe({
      next: (data) => {
        this.marques.set(this.sortMarques(data));
        if (selectId != null) {
          const current = this.form.controls.marqueIds.value ?? [];
          if (!current.includes(selectId)) {
            this.form.patchValue({ marqueIds: [...current, selectId] });
          }
        }
      },
      error: () => this.error.set('Impossible de charger les marques.')
    });
  }

  private sortMarques(data: MarqueMasOption[]): MarqueMasOption[] {
    return [...data].sort((a, b) =>
      (a.label || '').localeCompare(b.label || '', 'fr', { sensitivity: 'base' })
    );
  }

  /** Affiche le sous-formulaire de création d'une nouvelle marque. */
  openNewMarque(): void {
    this.showNewMarque.set(true);
    this.marqueError.set(null);
    this.newMarqueForm.reset({ label: '' });
  }

  /** Ferme le sous-formulaire de création de marque. */
  cancelNewMarque(): void {
    this.showNewMarque.set(false);
    this.marqueError.set(null);
  }

  /** Crée une nouvelle marque et l'ajoute à la sélection du SFM. */
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
        this.marqueError.set(err?.error?.message || 'Création de la marque impossible.');
      }
    });
  }

  /** Crée un groupe de formulaire pour un contact SFM. */
  createContactGroup(contact?: Partial<SfmContact>) {
    return this.fb.group({
      id: [contact?.id ?? null as number | null],
      nom: [contact?.nom || '', [Validators.required, Validators.maxLength(120)]],
      telephone: [contact?.telephone || '', [Validators.required, Validators.maxLength(40)]],
      email: [contact?.email || '', [Validators.required, Validators.email, Validators.maxLength(160)]],
      receiveOrderMails: [contact?.receiveOrderMails !== false],
      technicienSfm: [!!contact?.technicienSfm]
    });
  }

  /** Ajoute une ligne contact au formulaire SFM. */
  addContact(): void {
    this.contacts.push(this.createContactGroup());
  }

  /** Sélection depuis le sélecteur global « Affecter un technicien… ». */
  onAssignSelect(techId: number | null): void {
    this.assignExistingTechnicien(techId);
    this.assignSelectValue.set(null);
  }

  /**
   * Ajoute (ou remplit la 1ʳᵉ ligne vide) un technicien déjà en base.
   * Utilisé par le sélecteur global au-dessus de la liste des contacts.
   */
  assignExistingTechnicien(techId: number | null): void {
    if (techId == null) {
      return;
    }
    const emptyIndex = this.contacts.controls.findIndex((c) => {
      const id = c.get('id')?.value;
      const nom = String(c.get('nom')?.value || '').trim();
      const email = String(c.get('email')?.value || '').trim();
      return id == null && !nom && !email;
    });
    if (emptyIndex >= 0) {
      this.reuseTechnicien(emptyIndex, techId);
      return;
    }
    this.addContact();
    this.reuseTechnicien(this.contacts.length - 1, techId);
  }

  /** Préremplit une ligne avec un technicien SFM existant (multi-SFM). */
  reuseTechnicien(index: number, techId: number | null): void {
    const group = this.contacts.at(index);
    if (techId == null) {
      group.patchValue({
        id: null,
        nom: '',
        telephone: '',
        email: '',
        receiveOrderMails: true,
        technicienSfm: true
      });
      return;
    }
    const tech = this.techniciens().find((t) => t.id === techId);
    if (!tech) {
      return;
    }
    group.patchValue({
      id: tech.id,
      nom: tech.nom,
      telephone: tech.telephone,
      email: tech.email,
      receiveOrderMails: tech.receiveOrderMails !== false,
      technicienSfm: true
    });
  }

  /** Indique si la ligne pointe vers une fiche technicien déjà en base. */
  isLinkedTechnicien(index: number): boolean {
    const group = this.contacts.at(index);
    return !!group.get('technicienSfm')?.value && group.get('id')?.value != null;
  }

  /** Techniciens disponibles pour une ligne (hors déjà sélectionnés ailleurs). */
  availableTechniciens(index: number): SfmTechnicien[] {
    const usedIds = new Set(
      this.contacts.controls
        .map((c, i) => (i === index ? null : c.get('id')?.value as number | null))
        .filter((id): id is number => id != null)
    );
    return this.techniciens().filter((t) => !usedIds.has(t.id));
  }

  /** Supprime une ligne contact (minimum un contact requis). */
  removeContact(index: number): void {
    if (this.contacts.length <= 1) {
      this.error.set('Un SFM doit avoir au moins un contact.');
      return;
    }
    this.contacts.removeAt(index);
    this.error.set(null);
  }

  /** Valide et enregistre le SFM (création ou mise à jour). */
  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const marqueIds = this.form.controls.marqueIds.value || [];
    if (marqueIds.length === 0) {
      this.error.set('Sélectionnez au moins une marque.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const raw = this.form.getRawValue();
    const payload: SfmForm = {
      nom: raw.nom!.trim(),
      marqueIds: [...marqueIds],
      contacts: (raw.contacts || []).map((c) => ({
        id: c?.id != null ? Number(c.id) : undefined,
        nom: String(c?.nom || '').trim(),
        telephone: String(c?.telephone || '').trim(),
        email: String(c?.email || '').trim(),
        receiveOrderMails: c?.receiveOrderMails !== false,
        technicienSfm: !!c?.technicienSfm
      }))
    };
    const req$ = this.id ? this.sfmService.update(this.id, payload) : this.sfmService.create(payload);
    req$.subscribe({
      next: (saved) => {
        this.loadTechniciens();
        this.navigateAfterSave(saved.id);
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(err?.error?.message || 'Enregistrement impossible.');
      }
    });
  }

  /** Annule la saisie et retourne à la fiche ou à la liste. */
  cancel(): void {
    if (this.returnToDeviceForm()) {
      return;
    }
    if (this.id) {
      this.router.navigate(['/sfm', this.id]);
    } else {
      this.router.navigate(['/sfm']);
    }
  }

  private navigateAfterSave(sfmId: number): void {
    if (this.returnToDeviceForm({ sfmId: String(sfmId) })) {
      return;
    }
    this.router.navigate(['/sfm', sfmId]);
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
