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
import { MarqueMasOption, MasForm } from '../../models/models';

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
  readonly showNewMarque = signal(false);
  readonly error = signal<string | null>(null);
  readonly marqueError = signal<string | null>(null);
  readonly marques = signal<MarqueMasOption[]>([]);
  id: number | null = null;
  returnDevice: string | null = null;
  private returnForOrderRequest = false;

  readonly form = this.fb.group({
    numero: ['', [Validators.required, Validators.maxLength(80)]],
    marqueId: [null as number | null, Validators.required],
    utilise: [true]
  });

  readonly newMarqueForm = this.fb.nonNullable.group({
    label: ['', [Validators.required, Validators.maxLength(120)]]
  });

  get isEdit(): boolean {
    return this.id !== null;
  }

  ngOnInit(): void {
    this.returnDevice = this.route.snapshot.queryParamMap.get('returnDevice');
    this.returnForOrderRequest = this.route.snapshot.queryParamMap.get('forOrderRequest') === '1';
    this.loadMarques();

    const rawId = this.route.snapshot.paramMap.get('id');
    if (rawId) {
      this.id = Number(rawId);
      this.loading.set(true);
      this.masService.get(this.id).subscribe({
        next: (mas) => {
          this.form.patchValue({
            numero: mas.numero,
            marqueId: mas.marqueId,
            utilise: mas.utilise
          });
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
        this.marques.set(data);
        if (selectId != null) {
          this.form.patchValue({ marqueId: selectId });
        }
      }
    });
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

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const raw = this.form.getRawValue();
    const payload: MasForm = {
      numero: raw.numero!.trim(),
      marqueId: raw.marqueId,
      utilise: !!raw.utilise
    };
    const req$ = this.id ? this.masService.update(this.id, payload) : this.masService.create(payload);
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
    this.id ? this.router.navigate(['/mas', this.id]) : this.router.navigate(['/mas']);
  }

  private navigateAfterSave(masId: number): void {
    if (this.returnToDeviceForm({ masId })) {
      return;
    }
    this.router.navigate(['/mas', masId]);
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
