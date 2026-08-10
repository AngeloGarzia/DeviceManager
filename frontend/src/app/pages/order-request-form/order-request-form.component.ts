import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatAutocompleteModule, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
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
import { Device, OrderRequestLineForm } from '../../models/models';
import { DeviceService } from '../../services/device.service';
import { MailPreviewItem, OrderRequestService } from '../../services/order-request.service';

interface DraftLine {
  deviceId: number;
  quantite: number;
  nom: string;
  reference?: string | null;
  photoUrl: string;
  sfmNom?: string | null;
}

/**
 * Formulaire de création d'une demande de commande de pièces détachées.
 * Permet la recherche de pièces, la composition des lignes, l'aperçu des e-mails
 * et l'envoi de la demande aux SFM concernés.
 */
@Component({
  selector: 'app-order-request-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatAutocompleteModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './order-request-form.component.html',
  styleUrl: './order-request-form.component.scss'
})
export class OrderRequestFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly deviceService = inject(DeviceService);
  private readonly orderService = inject(OrderRequestService);
  private readonly destroyRef = inject(DestroyRef);

  readonly suggestions = signal<Device[]>([]);
  readonly searching = signal(false);
  readonly selectedDevice = signal<Device | null>(null);
  readonly lines = signal<DraftLine[]>([]);
  readonly saving = signal(false);
  readonly previewing = signal(false);
  readonly mailPreviews = signal<MailPreviewItem[]>([]);
  readonly success = signal(false);
  readonly error = signal<string | null>(null);
  lastSentCount = 0;

  readonly searchCtrl = this.fb.control<string | Device>('', { nonNullable: true });

  readonly picker = this.fb.group({
    deviceId: [null as number | null, Validators.required],
    quantite: [1, [Validators.required, Validators.min(1)]]
  });

  readonly form = this.fb.group({
    message: ['', [Validators.required, Validators.maxLength(1000)]]
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
    if (this.route.snapshot.queryParamMap.get('zeroStock') === '1') {
      this.prefillZeroStockLines();
      return;
    }
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

  /** Préremplit la demande avec toutes les pièces en stock à zéro. */
  private prefillZeroStockLines(): void {
    this.deviceService.list('').subscribe({
      next: (devices) => {
        const zeroStock = devices.filter((d) => (d.stock ?? 0) <= 0);
        this.lines.set(
          zeroStock.map((device) => ({
            deviceId: device.id,
            quantite: 1,
            nom: device.nom,
            reference: device.reference,
            photoUrl: device.photoUrl || '',
            sfmNom: device.sfmNom
          }))
        );
        if (zeroStock.length === 0) {
          this.error.set('Aucune pièce en stock à zéro pour cet atelier.');
        }
      },
      error: () => {
        this.error.set('Impossible de charger les pièces en stock à zéro.');
      }
    });
  }

  /** Formate une pièce pour l'affichage dans l'autocomplétion. */
  displayDevice = (value: Device | string | null): string => {
    if (!value) {
      return '';
    }
    if (typeof value === 'string') {
      return value;
    }
    const ref = value.reference?.trim();
    return ref ? `${value.nom} (${ref})` : value.nom;
  };

  /** Applique la pièce choisie dans la liste de suggestions. */
  onDeviceSelected(event: MatAutocompleteSelectedEvent): void {
    const device = event.option.value as Device;
    this.applyDevice(device);
  }

  /** Réinitialise la recherche et la sélection de pièce. */
  clearSearch(): void {
    this.searchCtrl.setValue('');
    this.selectedDevice.set(null);
    this.picker.patchValue({ deviceId: null });
    this.suggestions.set([]);
  }

  /** Texte saisi dans le champ de recherche (hors objet Device). */
  searchQueryText(): string {
    const value = this.searchCtrl.value;
    return typeof value === 'string' ? value.trim() : '';
  }

  /** URL absolue de la photo d'une pièce ou d'une ligne brouillon. */
  photoUrl(device: Device | null | DraftLine): string {
    if (!device) return '';
    const url = 'photoUrl' in device ? device.photoUrl : '';
    if (!url) return '';
    return this.deviceService.resolvePhotoUrl(url);
  }

  /** Sous-titre contextuel (marque, MAS, SFM) pour une pièce suggérée. */
  deviceHint(device: Device): string {
    const parts = [
      device.marqueLabel || device.masMarque,
      device.masNumero ? `MAS ${device.masNumero}` : null,
      device.sfmNom
    ].filter(Boolean);
    return parts.join(' · ');
  }

  /** Ajoute la pièce sélectionnée à la liste des lignes de commande. */
  addLine(): void {
    if (this.picker.invalid) {
      this.picker.markAllAsTouched();
      this.error.set('Sélectionnez une pièce dans les suggestions, puis ajoutez-la.');
      return;
    }
    const deviceId = this.picker.controls.deviceId.value!;
    const quantite = Number(this.picker.controls.quantite.value || 1);
    const device = this.selectedDevice();
    if (!device || device.id !== deviceId) {
      this.error.set('Pièce introuvable. Relancez la recherche et choisissez une suggestion.');
      return;
    }

    const existing = this.lines().find((l) => l.deviceId === deviceId);
    if (existing) {
      this.lines.update((list) =>
        list.map((l) =>
          l.deviceId === deviceId ? { ...l, quantite: l.quantite + quantite } : l
        )
      );
    } else {
      this.lines.update((list) => [
        ...list,
        {
          deviceId,
          quantite,
          nom: device.nom,
          reference: device.reference,
          photoUrl: device.photoUrl || '',
          sfmNom: device.sfmNom
        }
      ]);
    }
    this.clearSearch();
    this.picker.patchValue({ quantite: 1 });
    this.error.set(null);
    this.mailPreviews.set([]);
  }

  /** Met à jour la quantité d'une ligne existante. */
  updateQty(deviceId: number, value: string | number): void {
    const qty = Math.max(1, Number(value) || 1);
    this.lines.update((list) =>
      list.map((l) => (l.deviceId === deviceId ? { ...l, quantite: qty } : l))
    );
    this.mailPreviews.set([]);
  }

  /** Retire une ligne de la demande en cours. */
  removeLine(deviceId: number): void {
    this.lines.update((list) => list.filter((l) => l.deviceId !== deviceId));
    this.mailPreviews.set([]);
  }

  /** Charge l'aperçu des e-mails qui seraient envoyés aux SFM. */
  loadMailPreview(): void {
    if (this.lines().length === 0) {
      this.error.set('Ajoutez au moins une pièce pour prévisualiser l’e-mail.');
      return;
    }
    const lignes: OrderRequestLineForm[] = this.lines().map((l) => ({
      deviceId: l.deviceId,
      quantite: l.quantite
    }));
    this.previewing.set(true);
    this.error.set(null);
    this.orderService
      .previewCreate({
        message: (this.form.controls.message.value || '').trim() || '(message à compléter)',
        lignes
      })
      .subscribe({
        next: (previews) => {
          this.mailPreviews.set(previews);
          this.previewing.set(false);
        },
        error: (err) => {
          this.previewing.set(false);
          this.error.set(err?.error?.message || 'Aperçu impossible.');
        }
      });
  }

  /** Envoie la demande de commande avec message et lignes saisies. */
  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Complétez le message de la demande.');
      return;
    }
    if (this.lines().length === 0) {
      this.error.set('Ajoutez au moins une pièce détachée à la liste.');
      return;
    }

    const lignes: OrderRequestLineForm[] = this.lines().map((l) => ({
      deviceId: l.deviceId,
      quantite: l.quantite
    }));

    this.saving.set(true);
    this.error.set(null);
    this.success.set(false);
    this.orderService
      .create({
        message: this.form.controls.message.value!.trim(),
        lignes
      })
      .subscribe({
        next: () => {
          const count = this.lines().length;
          this.saving.set(false);
          this.success.set(true);
          this.form.reset({ message: '' });
          this.lines.set([]);
          this.clearSearch();
          this.picker.reset({ deviceId: null, quantite: 1 });
          this.mailPreviews.set([]);
          this.error.set(null);
          this.lastSentCount = count;
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(err?.error?.message || 'Envoi impossible.');
        }
      });
  }

  private applyDevice(device: Device): void {
    this.selectedDevice.set(device);
    this.picker.patchValue({ deviceId: device.id });
    this.searchCtrl.setValue(this.displayDevice(device), { emitEvent: false });
    this.suggestions.set([]);
  }
}
