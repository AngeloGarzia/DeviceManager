import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatSelectModule } from '@angular/material/select';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  finalize,
  of,
  switchMap
} from 'rxjs';
import { Device, Mas } from '../../models/models';
import { DeviceService } from '../../services/device.service';
import { InterventionService } from '../../services/intervention.service';
import { MasService } from '../../services/mas.service';
import { apiErrorMessage } from '../../shared/api-error';

interface DraftLine {
  deviceId: number;
  quantite: number;
  nom: string;
  reference?: string | null;
  photoUrl: string;
  stock: number;
}

/**
 * Consommation de pièces détachées : crée et archive un bon d'intervention.
 */
@Component({
  selector: 'app-device-use',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    MatSelectModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './device-use.component.html',
  styleUrl: './device-use.component.scss'
})
export class DeviceUseComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly deviceService = inject(DeviceService);
  private readonly interventionService = inject(InterventionService);
  private readonly masService = inject(MasService);
  private readonly destroyRef = inject(DestroyRef);

  readonly suggestions = signal<Device[]>([]);
  readonly searching = signal(false);
  readonly selectedDevice = signal<Device | null>(null);
  readonly lines = signal<DraftLine[]>([]);
  readonly masses = signal<Mas[]>([]);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly successNumero = signal<string | null>(null);

  readonly searchCtrl = this.fb.control<string | Device>('', { nonNullable: true });

  readonly picker = this.fb.group({
    deviceId: [null as number | null, Validators.required],
    quantite: [1, [Validators.required, Validators.min(1)]]
  });

  readonly form = this.fb.group({
    dateIntervention: [this.defaultDateTimeLocal(), [Validators.required]],
    emplacement: ['', Validators.maxLength(200)],
    /** Libellé MAS archivé sur le bon (numero — marque). */
    machineMas: [null as string | null],
    motif: ['', [Validators.required, Validators.maxLength(500)]],
    diagnostic: ['', Validators.maxLength(2000)],
    travaux: ['', [Validators.required, Validators.maxLength(2000)]],
    observations: ['', Validators.maxLength(2000)]
  });

  constructor() {
    this.searchCtrl.valueChanges
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((value) => {
          if (value && typeof value === 'object' && 'id' in value) {
            return of([] as Device[]);
          }
          const q = String(value ?? '').trim();
          if (this.selectedDevice() && this.displayDevice(this.selectedDevice()) === q) {
            return of([] as Device[]);
          }
          if (this.picker.controls.deviceId.value != null) {
            this.picker.patchValue({ deviceId: null }, { emitEvent: false });
            this.selectedDevice.set(null);
          }
          if (q.length < 1) {
            this.suggestions.set([]);
            return of([] as Device[]);
          }
          this.searching.set(true);
          return this.deviceService.list(q).pipe(
            catchError(() => of([] as Device[])),
            finalize(() => this.searching.set(false))
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((list) => this.suggestions.set(list));
  }

  ngOnInit(): void {
    this.loadMasses();
    const raw = this.route.snapshot.queryParamMap.get('deviceId');
    if (!raw) {
      return;
    }
    const id = Number(raw);
    if (!Number.isFinite(id)) {
      return;
    }
    this.deviceService.get(id).subscribe({
      next: (device) => {
        this.applyDevice(device);
        this.addLine();
      }
    });
  }

  /** Charge les MAS de l'atelier pour le sélecteur Machine. */
  private loadMasses(): void {
    this.masService.list().subscribe({
      next: (list) => {
        this.masses.set(
          [...list].sort((a, b) => {
            if (a.utilise !== b.utilise) {
              return a.utilise ? -1 : 1;
            }
            return a.numero.localeCompare(b.numero, 'fr', { numeric: true });
          })
        );
      },
      error: () => this.masses.set([])
    });
  }

  /** Libellé affiché / archivé pour une MAS. */
  masLabel(mas: Mas): string {
    const marque = (mas.marqueLabel || mas.marque || '').trim();
    return marque ? `${mas.numero} — ${marque}` : mas.numero;
  }

  displayDevice = (value: Device | string | null): string => {
    if (!value) {
      return '';
    }
    if (typeof value === 'string') {
      return value;
    }
    const ref = value.reference?.trim();
    const stock = `stock ${value.stock ?? 0}`;
    return ref ? `${value.nom} (${ref}) — ${stock}` : `${value.nom} — ${stock}`;
  };

  onDeviceSelected(event: MatAutocompleteSelectedEvent): void {
    this.applyDevice(event.option.value as Device);
  }

  clearSearch(): void {
    this.searchCtrl.setValue('');
    this.selectedDevice.set(null);
    this.picker.patchValue({ deviceId: null });
    this.suggestions.set([]);
  }

  searchQueryText(): string {
    const value = this.searchCtrl.value;
    return typeof value === 'string' ? value.trim() : '';
  }

  photoUrl(device: Device | null | DraftLine): string {
    if (!device) return '';
    return this.deviceService.resolvePhotoUrl(device.photoUrl);
  }

  addLine(): void {
    this.error.set(null);
    const device = this.selectedDevice();
    const qty = Number(this.picker.controls.quantite.value ?? 0);
    if (!device || !Number.isFinite(qty) || qty < 1) {
      this.error.set('Sélectionnez une pièce et une quantité valide.');
      return;
    }
    if (qty > (device.stock ?? 0)) {
      this.error.set(
        `Stock insuffisant pour « ${device.nom} » (disponible : ${device.stock ?? 0}).`
      );
      return;
    }
    const existing = this.lines().find((l) => l.deviceId === device.id);
    if (existing) {
      const nextQty = existing.quantite + qty;
      if (nextQty > (device.stock ?? 0)) {
        this.error.set(
          `Stock insuffisant pour « ${device.nom} » (disponible : ${device.stock ?? 0}).`
        );
        return;
      }
      this.lines.update((list) =>
        list.map((l) => (l.deviceId === device.id ? { ...l, quantite: nextQty } : l))
      );
    } else {
      this.lines.update((list) => [
        ...list,
        {
          deviceId: device.id,
          quantite: qty,
          nom: device.nom,
          reference: device.reference,
          photoUrl: device.photoUrl || '',
          stock: device.stock ?? 0
        }
      ]);
    }
    this.clearSearch();
    this.picker.patchValue({ quantite: 1 });
  }

  updateQty(deviceId: number, raw: string): void {
    const qty = Number(raw);
    if (!Number.isFinite(qty) || qty < 1) {
      return;
    }
    this.lines.update((list) =>
      list.map((l) => {
        if (l.deviceId !== deviceId) {
          return l;
        }
        return { ...l, quantite: Math.min(qty, l.stock) };
      })
    );
  }

  removeLine(deviceId: number): void {
    this.lines.update((list) => list.filter((l) => l.deviceId !== deviceId));
  }

  submit(): void {
    this.error.set(null);
    this.successNumero.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Complétez les champs obligatoires du bon d’intervention.');
      return;
    }
    if (this.lines().length === 0) {
      this.error.set('Ajoutez au moins une pièce détachée consommée.');
      return;
    }
    for (const line of this.lines()) {
      if (line.quantite > line.stock) {
        this.error.set(`Stock insuffisant pour « ${line.nom} ».`);
        return;
      }
    }

    const v = this.form.getRawValue();
    this.saving.set(true);
    this.interventionService
      .create({
        dateIntervention: this.toIsoLocal(v.dateIntervention || ''),
        emplacement: v.emplacement?.trim() || null,
        machineMas: (v.machineMas || '').trim() || null,
        motif: (v.motif || '').trim(),
        diagnostic: v.diagnostic?.trim() || null,
        travaux: (v.travaux || '').trim(),
        observations: v.observations?.trim() || null,
        lignes: this.lines().map((l) => ({ deviceId: l.deviceId, quantite: l.quantite }))
      })
      .subscribe({
        next: (res) => {
          this.saving.set(false);
          this.successNumero.set(res.numero);
          this.lines.set([]);
          this.form.patchValue({
            motif: '',
            diagnostic: '',
            travaux: '',
            observations: '',
            emplacement: '',
            machineMas: null,
            dateIntervention: this.defaultDateTimeLocal()
          });
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(apiErrorMessage(err, 'Enregistrement du bon d’intervention impossible.'));
        }
      });
  }

  goToArchive(): void {
    void this.router.navigate(['/devices/interventions']);
  }

  private applyDevice(device: Device): void {
    this.selectedDevice.set(device);
    this.picker.patchValue({ deviceId: device.id });
    this.searchCtrl.setValue(this.displayDevice(device), { emitEvent: false });
  }

  private defaultDateTimeLocal(): string {
    const d = new Date();
    d.setSeconds(0, 0);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  /** Convertit datetime-local → ISO sans timezone (LocalDateTime backend). */
  private toIsoLocal(value: string): string {
    if (!value) {
      return new Date().toISOString().slice(0, 19);
    }
    return value.length === 16 ? `${value}:00` : value;
  }
}
