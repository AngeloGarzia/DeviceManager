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
  readonly error = signal<string | null>(null);
  readonly marques = signal<MarqueMasOption[]>([]);
  id: number | null = null;

  readonly form = this.fb.group({
    nom: ['', [Validators.required, Validators.maxLength(120)]],
    marqueIds: [[] as number[], Validators.required],
    contacts: this.fb.array([this.createContactGroup()])
  });

  get isEdit(): boolean {
    return this.id !== null;
  }

  get contacts(): FormArray {
    return this.form.get('contacts') as FormArray;
  }

  ngOnInit(): void {
    this.masService.listMarques().subscribe({
      next: (data) => this.marques.set(data),
      error: () => this.error.set('Impossible de charger les marques.')
    });

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

  createContactGroup(contact?: Partial<SfmContact>) {
    return this.fb.group({
      nom: [contact?.nom || '', [Validators.required, Validators.maxLength(120)]],
      telephone: [contact?.telephone || '', [Validators.required, Validators.maxLength(40)]],
      email: [contact?.email || '', [Validators.required, Validators.email, Validators.maxLength(160)]]
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
        email: String(c?.email || '').trim()
      }))
    };
    const req$ = this.id ? this.sfmService.update(this.id, payload) : this.sfmService.create(payload);
    req$.subscribe({
      next: (saved) => this.router.navigate(['/sfm', saved.id]),
      error: (err) => {
        this.saving.set(false);
        this.error.set(err?.error?.message || 'Enregistrement impossible.');
      }
    });
  }

  cancel(): void {
    this.id ? this.router.navigate(['/sfm', this.id]) : this.router.navigate(['/sfm']);
  }
}
