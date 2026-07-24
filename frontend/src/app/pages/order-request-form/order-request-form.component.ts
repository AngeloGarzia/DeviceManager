import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { Device, OrderRequestLineForm } from '../../models/models';
import { DeviceService } from '../../services/device.service';
import { OrderRequestService } from '../../services/order-request.service';

interface DraftLine {
  deviceId: number;
  quantite: number;
  nom: string;
  reference: string;
  photoUrl: string;
}

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
    MatSelectModule,
    MatCardModule,
    MatIconModule
  ],
  templateUrl: './order-request-form.component.html',
  styleUrl: './order-request-form.component.scss'
})
export class OrderRequestFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly deviceService = inject(DeviceService);
  private readonly orderService = inject(OrderRequestService);

  readonly devices = signal<Device[]>([]);
  readonly lines = signal<DraftLine[]>([]);
  readonly saving = signal(false);
  readonly success = signal(false);
  readonly error = signal<string | null>(null);
  lastSentCount = 0;

  readonly selectedDevice = computed(() => {
    const id = this.picker.controls.deviceId.value;
    if (id == null) return null;
    return this.devices().find((d) => d.id === id) ?? null;
  });

  readonly picker = this.fb.group({
    deviceId: [null as number | null, Validators.required],
    quantite: [1, [Validators.required, Validators.min(1)]]
  });

  readonly form = this.fb.group({
    message: ['', [Validators.required, Validators.maxLength(1000)]]
  });

  ngOnInit(): void {
    this.deviceService.list().subscribe({
      next: (data) => {
        this.devices.set(data);
        const raw = this.route.snapshot.queryParamMap.get('deviceId');
        if (raw) {
          const id = Number(raw);
          this.picker.patchValue({ deviceId: id });
          this.addLine();
        }
      }
    });
  }

  photoUrl(device: Device | null | DraftLine): string {
    if (!device) return '';
    const url = 'photoUrl' in device ? device.photoUrl : '';
    if (!url) return '';
    return this.deviceService.resolvePhotoUrl(url);
  }

  addLine(): void {
    if (this.picker.invalid) {
      this.picker.markAllAsTouched();
      return;
    }
    const deviceId = this.picker.controls.deviceId.value!;
    const quantite = Number(this.picker.controls.quantite.value || 1);
    const device = this.devices().find((d) => d.id === deviceId);
    if (!device) {
      this.error.set('Pièce introuvable.');
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
          photoUrl: device.photoUrl || ''
        }
      ]);
    }
    this.picker.patchValue({ deviceId: null, quantite: 1 });
    this.error.set(null);
  }

  updateQty(deviceId: number, value: string | number): void {
    const qty = Math.max(1, Number(value) || 1);
    this.lines.update((list) =>
      list.map((l) => (l.deviceId === deviceId ? { ...l, quantite: qty } : l))
    );
  }

  removeLine(deviceId: number): void {
    this.lines.update((list) => list.filter((l) => l.deviceId !== deviceId));
  }

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
          this.picker.reset({ deviceId: null, quantite: 1 });
          this.error.set(null);
          this.lastSentCount = count;
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(err?.error?.message || 'Envoi impossible.');
        }
      });
  }
}
