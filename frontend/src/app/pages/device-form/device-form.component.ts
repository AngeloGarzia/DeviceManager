import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { DeviceService } from '../../services/device.service';
import { SfmService } from '../../services/sfm.service';
import { MasService } from '../../services/mas.service';
import { DeviceForm, Mas, Sfm } from '../../models/models';

@Component({
  selector: 'app-device-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatCardModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './device-form.component.html',
  styleUrl: './device-form.component.scss'
})
export class DeviceFormComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild('videoEl') videoEl?: ElementRef<HTMLVideoElement>;
  @ViewChild('canvasEl') canvasEl?: ElementRef<HTMLCanvasElement>;
  @ViewChild('fileInput') fileInput?: ElementRef<HTMLInputElement>;

  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly deviceService = inject(DeviceService);
  private readonly sfmService = inject(SfmService);
  private readonly masService = inject(MasService);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly cameraError = signal<string | null>(null);
  readonly cameraReady = signal(false);
  readonly cameras = signal<MediaDeviceInfo[]>([]);
  readonly selectedCameraId = signal<string | null>(null);
  readonly sfms = signal<Sfm[]>([]);
  readonly masses = signal<Mas[]>([]);
  readonly selectedMasId = signal<number | null>(null);
  readonly selectedSfmId = signal<number | null>(null);
  readonly previewUrl = signal<string | null>(null);
  readonly existingPhotoUrl = signal<string | null>(null);

  private mediaStream: MediaStream | null = null;
  private photoFile: File | null = null;
  private viewReady = false;
  forOrderRequest = false;
  private readonly onDeviceChange = (): void => {
    void this.refreshCameraList();
  };
  id: number | null = null;

  readonly form = this.fb.group({
    nom: ['', [Validators.required, Validators.maxLength(120)]],
    reference: ['', [Validators.required, Validators.maxLength(80)]],
    usage: ['', [Validators.required, Validators.maxLength(500)]],
    dateAcquisition: ['', Validators.required],
    obsolete: [false],
    sfmId: [null as number | null, Validators.required],
    masId: [null as number | null, Validators.required]
  });

  readonly selectedMasMarque = computed(() => {
    const masId = this.selectedMasId();
    if (masId == null) {
      return 'Sélectionnez une MAS';
    }
    const mas = this.masses().find((m) => m.id === masId);
    return mas?.marqueLabel || mas?.marque || '—';
  });

  readonly filteredMasses = computed(() => {
    const sfmId = this.selectedSfmId();
    if (sfmId == null) {
      return [] as Mas[];
    }
    const sfm = this.sfms().find((s) => s.id === sfmId);
    const marqueIds = new Set(sfm?.marqueIds ?? sfm?.marques?.map((m) => m.id) ?? []);
    if (marqueIds.size === 0) {
      return [] as Mas[];
    }
    return this.masses().filter((m) => marqueIds.has(m.marqueId));
  });

  get isEdit(): boolean {
    return this.id !== null;
  }

  get usageLength(): number {
    return this.form.controls.usage.value?.length ?? 0;
  }

  ngOnInit(): void {
    this.forOrderRequest = this.route.snapshot.queryParamMap.get('forOrderRequest') === '1';
    this.sfmService.list().subscribe({ next: (data) => this.sfms.set(data) });
    this.masService.list().subscribe({ next: (data) => this.masses.set(data) });
    this.form.controls.masId.valueChanges.subscribe((masId) => this.selectedMasId.set(masId));
    this.form.controls.sfmId.valueChanges.subscribe((sfmId) => {
      this.selectedSfmId.set(sfmId);
      const currentMasId = this.form.controls.masId.value;
      if (currentMasId == null || this.sfms().length === 0 || this.masses().length === 0) {
        return;
      }
      const allowed = this.filteredMasses().some((m) => m.id === currentMasId);
      if (!allowed) {
        this.form.patchValue({ masId: null }, { emitEvent: false });
        this.selectedMasId.set(null);
      }
    });

    const rawId = this.route.snapshot.paramMap.get('id');
    if (rawId) {
      this.id = Number(rawId);
      this.loading.set(true);
      this.deviceService.get(this.id).subscribe({
        next: (device) => {
          this.form.patchValue({
            nom: device.nom,
            reference: device.reference,
            usage: device.usage,
            dateAcquisition: device.dateAcquisition,
            obsolete: device.obsolete,
            sfmId: device.sfmId,
            masId: device.masId
          });
          this.selectedSfmId.set(device.sfmId);
          this.selectedMasId.set(device.masId);
          if (device.photoUrl) {
            this.existingPhotoUrl.set(this.deviceService.resolvePhotoUrl(device.photoUrl));
          }
          this.loading.set(false);
          setTimeout(() => void this.startCamera(), 0);
        },
        error: () => {
          this.error.set('Pièce introuvable.');
          this.loading.set(false);
        }
      });
    }
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    navigator.mediaDevices?.addEventListener?.('devicechange', this.onDeviceChange);
    if (!this.isEdit) {
      void this.startCamera();
    }
  }

  ngOnDestroy(): void {
    navigator.mediaDevices?.removeEventListener?.('devicechange', this.onDeviceChange);
    this.stopCamera();
    const preview = this.previewUrl();
    if (preview) {
      URL.revokeObjectURL(preview);
    }
  }

  cameraLabel(device: MediaDeviceInfo, index: number): string {
    if (device.label?.trim()) {
      return device.label;
    }
    return `Caméra ${index + 1}`;
  }

  async onCameraSourceChange(deviceId: string): Promise<void> {
    if (!deviceId || deviceId === this.selectedCameraId()) {
      return;
    }
    this.selectedCameraId.set(deviceId);
    await this.startCamera(deviceId);
  }

  async startCamera(deviceId?: string | null): Promise<void> {
    if (!this.videoEl) {
      setTimeout(() => void this.startCamera(deviceId), 50);
      return;
    }
    this.cameraError.set(null);
    this.stopCamera(false);
    const preferredId = deviceId ?? this.selectedCameraId();
    try {
      this.mediaStream = await this.openCameraStream(preferredId);
      const video = this.videoEl.nativeElement;
      video.srcObject = this.mediaStream;
      await video.play();
      this.cameraReady.set(true);
      await this.refreshCameraList();
    } catch {
      this.cameraReady.set(false);
      this.cameraError.set(
        "Caméra inaccessible. Autorisez l'accès ou utilisez le bouton galerie."
      );
    }
  }

  private async openCameraStream(deviceId: string | null): Promise<MediaStream> {
    if (deviceId) {
      return navigator.mediaDevices.getUserMedia({
        video: { deviceId: { exact: deviceId } },
        audio: false
      });
    }

    // Priorité: caméra arrière (environment), sinon n'importe quelle caméra.
    try {
      return await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { exact: 'environment' } },
        audio: false
      });
    } catch {
      try {
        return await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { ideal: 'environment' } },
          audio: false
        });
      } catch {
        return navigator.mediaDevices.getUserMedia({ video: true, audio: false });
      }
    }
  }

  private async refreshCameraList(): Promise<void> {
    if (!navigator.mediaDevices?.enumerateDevices) {
      return;
    }
    const devices = await navigator.mediaDevices.enumerateDevices();
    const videoInputs = devices.filter((d) => d.kind === 'videoinput');
    this.cameras.set(videoInputs);

    const activeId = this.mediaStream?.getVideoTracks()[0]?.getSettings()?.deviceId ?? null;
    if (activeId) {
      this.selectedCameraId.set(activeId);
      return;
    }

    if (!this.selectedCameraId() && videoInputs.length > 0) {
      const rear = videoInputs.find((d) =>
        /back|rear|environment|arri[eè]re|world/i.test(d.label)
      );
      this.selectedCameraId.set(rear?.deviceId ?? videoInputs[0].deviceId);
    }
  }

  async capturePhoto(): Promise<void> {
    const video = this.videoEl?.nativeElement;
    const canvas = this.canvasEl?.nativeElement;
    if (!video || !canvas || !this.cameraReady()) {
      this.error.set("La caméra n'est pas prête.");
      return;
    }

    canvas.width = video.videoWidth || 1280;
    canvas.height = video.videoHeight || 720;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return;
    }
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

    const blob = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob((b) => resolve(b), 'image/jpeg', 0.9)
    );
    if (!blob) {
      this.error.set("Impossible d'acquérir l'image.");
      return;
    }

    this.setPhotoFile(new File([blob], `piece-${Date.now()}.jpg`, { type: 'image/jpeg' }));
  }

  openGallery(): void {
    this.fileInput?.nativeElement.click();
  }

  onGallerySelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) {
      this.setPhotoFile(file);
    }
  }

  clearPhoto(): void {
    const preview = this.previewUrl();
    if (preview) {
      URL.revokeObjectURL(preview);
    }
    this.previewUrl.set(null);
    this.photoFile = null;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.isEdit && !this.photoFile) {
      this.error.set("Acquérez une image avant d'enregistrer.");
      return;
    }

    const payload = this.form.getRawValue() as DeviceForm;
    this.saving.set(true);
    this.error.set(null);
    const req$ = this.id
      ? this.deviceService.update(this.id, payload, this.photoFile)
      : this.deviceService.create(payload, this.photoFile!);
    req$.subscribe({
      next: (saved) => {
        if (this.forOrderRequest) {
          this.router.navigate(['/order-request'], { queryParams: { deviceId: saved.id } });
          return;
        }
        this.router.navigate(['/devices', saved.id]);
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(err?.error?.message || 'Enregistrement impossible.');
      }
    });
  }

  cancel(): void {
    if (this.forOrderRequest) {
      this.router.navigate(['/order-request']);
      return;
    }
    if (this.id) {
      this.router.navigate(['/devices', this.id]);
    } else {
      this.router.navigate(['/devices']);
    }
  }

  private setPhotoFile(file: File): void {
    const previous = this.previewUrl();
    if (previous) {
      URL.revokeObjectURL(previous);
    }
    this.photoFile = file;
    this.previewUrl.set(URL.createObjectURL(file));
    this.error.set(null);
  }

  private stopCamera(clearSelection = true): void {
    this.mediaStream?.getTracks().forEach((track) => track.stop());
    this.mediaStream = null;
    this.cameraReady.set(false);
    if (clearSelection) {
      this.selectedCameraId.set(null);
    }
    const video = this.videoEl?.nativeElement;
    if (video) {
      video.srcObject = null;
    }
  }
}
