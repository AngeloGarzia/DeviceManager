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
import { MarqueMasOption, SfmContact, SfmForm } from '../../models/models';

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
        this.marqueError.set(err?.error?.message || 'Création de la marque impossible.');
      }
    });
  }

  createContactGroup(contact?: Partial<SfmContact>) {
    return this.fb.group({
      nom: [contact?.nom || '', [Validators.required, Validators.maxLength(120)]],
      telephone: [contact?.telephone || '', [Validators.required, Validators.maxLength(40)]],
      email: [contact?.email || '', [Validators.required, Validators.email, Validators.maxLength(160)]],
      receiveOrderMails: [contact?.receiveOrderMails !== false]
    });
  }

  addContact(): void {
    this.contacts.push(this.createContactGroup());
  }

  removeContact(index: number): void {
    if (this.contacts.length <= 1) {
      this.error.set('Un SFM doit avoir au moins un contact.');
      return;
    }
    this.contacts.removeAt(index);
    this.error.set(null);
  }

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
        nom: String(c?.nom || '').trim(),
        telephone: String(c?.telephone || '').trim(),
        email: String(c?.email || '').trim(),
        receiveOrderMails: c?.receiveOrderMails !== false
      }))
    };
    const req$ = this.id ? this.sfmService.update(this.id, payload) : this.sfmService.create(payload);
    req$.subscribe({
      next: (saved) => this.navigateAfterSave(saved.id),
      error: (err) => {
        this.saving.set(false);
        this.error.set(err?.error?.message || 'Enregistrement impossible.');
      }
    });
  }

  cancel(): void {
    if (this.returnToDeviceForm()) {
      return;
    }
    this.id ? this.router.navigate(['/sfm', this.id]) : this.router.navigate(['/sfm']);
  }

  private navigateAfterSave(sfmId: number): void {
    if (this.returnToDeviceForm({ sfmId })) {
      return;
    }
    this.router.navigate(['/sfm', sfmId]);
  }

  private returnToDeviceForm(extra: Record<string, string | number> = {}): boolean {
    if (!this.returnDevice) {
      return false;
    }
    const queryParams: Record<string, string | number> = { ...extra };
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
