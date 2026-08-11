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
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { DeviceService } from '../../services/device.service';
import { DeviceFormDraftService } from '../../services/device-form-draft.service';
import { SfmService } from '../../services/sfm.service';
import { MasService } from '../../services/mas.service';
import { AiService } from '../../services/ai.service';
import { DeviceForm, DevicePhoto, Mas, Sfm } from '../../models/models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { ImageEditorDialogComponent } from '../../shared/image-editor-dialog.component';
import { apiErrorMessage } from '../../shared/api-error';

interface NewPhotoItem {
  file: File;
  previewUrl: string;
}

/**
 * Formulaire de création ou modification d'une pièce détachée.
 * Gère la capture photo (caméra ou galerie), le rattachement SFM/MAS,
 * le scan IA d'étiquette et la sauvegarde avec brouillon de retour.
 */
@Component({
  selector: 'app-device-form',
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
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatDialogModule,
    ConfirmDialogComponent
  ],
  templateUrl: './device-form.component.html',
  styleUrl: './device-form.component.scss'
})
export class DeviceFormComponent implements OnInit, AfterViewInit, OnDestroy {
  static readonly MAX_PHOTOS = 5;

  @ViewChild('videoEl') videoEl?: ElementRef<HTMLVideoElement>;
  @ViewChild('canvasEl') canvasEl?: ElementRef<HTMLCanvasElement>;
  @ViewChild('fileInput') fileInput?: ElementRef<HTMLInputElement>;

  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly deviceService = inject(DeviceService);
  private readonly draftService = inject(DeviceFormDraftService);
  private readonly sfmService = inject(SfmService);
  private readonly masService = inject(MasService);
  private readonly dialog = inject(MatDialog);
  readonly aiService = inject(AiService);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly scanningLabel = signal(false);
  readonly error = signal<string | null>(null);
  readonly aiHint = signal<string | null>(null);
  readonly associatedMasHint = signal<string | null>(null);
  readonly cameraError = signal<string | null>(null);
  readonly cameraReady = signal(false);
  readonly cameras = signal<MediaDeviceInfo[]>([]);
  readonly selectedCameraId = signal<string | null>(null);
  readonly sfms = signal<Sfm[]>([]);
  readonly masses = signal<Mas[]>([]);
  readonly selectedMasId = signal<number | null>(null);
  readonly selectedSfmId = signal<number | null>(null);
  readonly existingPhotos = signal<DevicePhoto[]>([]);
  readonly newPhotos = signal<NewPhotoItem[]>([]);
  readonly offerAnotherOpen = signal(false);
  readonly marqueMismatchOpen = signal(false);
  private savedDeviceId: number | null = null;

  private mediaStream: MediaStream | null = null;
  private keepDraftOnDestroy = false;
  forOrderRequest = false;
  private readonly onDeviceChange = (): void => {
    void this.refreshCameraList();
  };
  id: number | null = null;

  readonly form = this.fb.group({
    nom: ['', [Validators.required, Validators.maxLength(120)]],
    reference: ['', [Validators.maxLength(80)]],
    usage: ['', [Validators.required, Validators.maxLength(500)]],
    dateAcquisition: [this.todayIso(), Validators.required],
    obsolete: [false],
    stock: [0, [Validators.required, Validators.min(0)]],
    sfmId: [null as number | null],
    masId: [null as number | null]
  });

  readonly photoCount = computed(
    () => this.existingPhotos().length + this.newPhotos().length
  );

  readonly canAddPhoto = computed(() => this.photoCount() < DeviceFormComponent.MAX_PHOTOS);

  readonly selectedMasMarque = computed(() => {
    const masId = this.selectedMasId();
    if (masId == null) {
      return '—';
    }
    const mas = this.masses().find((m) => m.id === masId);
    return mas?.marqueLabel || mas?.marque || '—';
  });

  readonly filteredMasses = computed(() => {
    const all = [...this.masses()];
    const sfmId = this.selectedSfmId();
    if (sfmId == null) {
      return all.sort((a, b) => a.numero.localeCompare(b.numero, 'fr'));
    }
    const sfm = this.sfms().find((s) => s.id === sfmId);
    const marqueIds = new Set(sfm?.marqueIds ?? sfm?.marques?.map((m) => m.id) ?? []);
    // Toutes les MAS restent sélectionnables ; les marques compatibles avec le SFM en premier.
    return all.sort((a, b) => {
      const aOk = marqueIds.size === 0 || marqueIds.has(a.marqueId) ? 0 : 1;
      const bOk = marqueIds.size === 0 || marqueIds.has(b.marqueId) ? 0 : 1;
      if (aOk !== bOk) {
        return aOk - bOk;
      }
      return a.numero.localeCompare(b.numero, 'fr');
    });
  });

  /** Indique si le formulaire est en mode édition. */
  get isEdit(): boolean {
    return this.id !== null;
  }

  /** Longueur actuelle du champ usage (compteur d'affichage). */
  get usageLength(): number {
    return this.form.controls.usage.value?.length ?? 0;
  }

  /** Nombre maximal de photos autorisées par pièce. */
  get maxPhotos(): number {
    return DeviceFormComponent.MAX_PHOTOS;
  }

  ngOnInit(): void {
    this.aiService.refreshStatus();
    this.forOrderRequest = this.route.snapshot.queryParamMap.get('forOrderRequest') === '1';
    this.form.controls.masId.valueChanges.subscribe((masId) => this.selectedMasId.set(masId));
    this.form.controls.sfmId.valueChanges.subscribe((sfmId) => {
      this.selectedSfmId.set(sfmId);
    });

    const draft = this.draftService.take();
    const querySfmId = this.parseOptionalId(this.route.snapshot.queryParamMap.get('sfmId'));
    const queryMasId = this.parseOptionalId(this.route.snapshot.queryParamMap.get('masId'));

    const rawId = this.route.snapshot.paramMap.get('id');
    if (rawId) {
      this.id = Number(rawId);
    }

    if (draft) {
      this.id = draft.editId;
      this.forOrderRequest = draft.forOrderRequest || this.forOrderRequest;
      this.form.patchValue(draft.form);
      this.selectedSfmId.set(draft.form.sfmId);
      this.selectedMasId.set(draft.form.masId);
      this.existingPhotos.set(draft.existingPhotos);
      this.newPhotos.set(draft.newPhotos);
    } else if (this.id != null) {
      this.loading.set(true);
      this.deviceService.get(this.id).subscribe({
        next: (device) => {
          this.form.patchValue({
            nom: device.nom,
            reference: device.reference ?? '',
            usage: device.usage,
            dateAcquisition: device.dateAcquisition,
            obsolete: device.obsolete,
            stock: device.stock ?? 0,
            sfmId: device.sfmId ?? null,
            masId: device.masId ?? null
          });
          this.selectedSfmId.set(device.sfmId ?? null);
          this.selectedMasId.set(device.masId ?? null);
          const photos =
            device.photos && device.photos.length > 0
              ? [...device.photos].sort((a, b) => a.position - b.position)
              : device.photoUrl
                ? [
                    {
                      id: -1,
                      photoUrl: device.photoUrl,
                      position: 0
                    } as DevicePhoto
                  ]
                : [];
          this.existingPhotos.set(photos);
          this.applyQuerySelections(querySfmId, queryMasId);
          this.loading.set(false);
          setTimeout(() => void this.startCamera(), 0);
        },
        error: () => {
          this.error.set('Pièce introuvable.');
          this.loading.set(false);
        }
      });
    }

    this.sfmService.list().subscribe({
      next: (data) => {
        this.sfms.set(data);
        this.applyQuerySelections(querySfmId, queryMasId);
      }
    });
    this.masService.list().subscribe({
      next: (data) => {
        this.masses.set(data);
        this.applyQuerySelections(querySfmId, queryMasId);
      }
    });

    if (!draft && this.id == null) {
      this.applyQuerySelections(querySfmId, queryMasId);
    } else if (draft) {
      this.applyQuerySelections(querySfmId, queryMasId);
    }
  }

  ngAfterViewInit(): void {
    navigator.mediaDevices?.addEventListener?.('devicechange', this.onDeviceChange);
    if (!this.loading()) {
      void this.startCamera();
    }
  }

  ngOnDestroy(): void {
    navigator.mediaDevices?.removeEventListener?.('devicechange', this.onDeviceChange);
    this.stopCamera();
    if (!this.keepDraftOnDestroy) {
      this.revokeNewPreviews();
      this.draftService.clear();
    }
  }

  /** Sauvegarde le brouillon et navigue vers la création d'un SFM. */
  goCreateSfm(): void {
    this.persistDraft();
    this.keepDraftOnDestroy = true;
    this.router.navigate(['/sfm/new'], { queryParams: this.returnDeviceQuery() });
  }

  /** Sauvegarde le brouillon et navigue vers la création d'une MAS. */
  goCreateMas(): void {
    this.persistDraft();
    this.keepDraftOnDestroy = true;
    this.router.navigate(['/mas/new'], { queryParams: this.returnDeviceQuery() });
  }

  /** Libellé affiché pour une source caméra dans la liste déroulante. */
  cameraLabel(device: MediaDeviceInfo, index: number): string {
    if (device.label?.trim()) {
      return device.label;
    }
    return `Caméra ${index + 1}`;
  }

  /** URL absolue d'une photo déjà enregistrée. */
  resolveExistingUrl(photo: DevicePhoto): string {
    return this.deviceService.resolvePhotoUrl(photo.photoUrl);
  }

  /** Change la caméra active et redémarre le flux vidéo. */
  async onCameraSourceChange(deviceId: string): Promise<void> {
    if (!deviceId || deviceId === this.selectedCameraId()) {
      return;
    }
    this.selectedCameraId.set(deviceId);
    await this.startCamera(deviceId);
  }

  /** Démarre ou redémarre le flux caméra pour la capture photo. */
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
    const aspect = { ideal: 4 / 3 } as const;
    if (deviceId) {
      return navigator.mediaDevices.getUserMedia({
        video: { deviceId: { exact: deviceId }, aspectRatio: aspect },
        audio: false
      });
    }

    try {
      return await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { exact: 'environment' }, aspectRatio: aspect },
        audio: false
      });
    } catch {
      try {
        return await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { ideal: 'environment' }, aspectRatio: aspect },
          audio: false
        });
      } catch {
        return navigator.mediaDevices.getUserMedia({
          video: { aspectRatio: aspect },
          audio: false
        });
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
      this.selectedCameraId.set(rear?.deviceId ?? videoInputs.at(0)?.deviceId ?? null);
    }
  }

  /** Capture une photo depuis le flux caméra, l'édite, puis l'ajoute à la galerie. */
  async capturePhoto(): Promise<void> {
    if (!this.canAddPhoto()) {
      this.error.set(`Maximum ${this.maxPhotos} images par pièce.`);
      return;
    }
    const file = await this.captureBlobAsFile();
    if (!file) {
      this.error.set(this.cameraReady() ? "Impossible d'acquérir l'image." : "La caméra n'est pas prête.");
      return;
    }
    const edited = await this.openImageEditor(file, 'Recadrer la capture');
    if (edited) {
      this.addNewPhoto(edited);
    }
  }

  /** Capture (ou dernière photo) puis analyse IA de l'étiquette → préremplit le formulaire. */
  async scanLabelWithAi(): Promise<void> {
    if (!this.aiService.enabled() || this.scanningLabel()) {
      return;
    }
    this.error.set(null);
    this.aiHint.set(null);

    let file: File | null = null;
    try {
      file = await this.captureBlobAsFile();
    } catch {
      // fallback: dernière image ajoutée
    }
    if (!file) {
      const latest = this.newPhotos().at(-1)?.file ?? null;
      file = latest;
    }
    if (!file) {
      this.error.set('Prenez une photo d’étiquette ou ajoutez une image avant le scan IA.');
      return;
    }

    // Propose un recadrage avant le scan (étiquette plus nette pour l'IA)
    const edited = await this.openImageEditor(file, 'Recadrer l’étiquette pour le scan IA');
    if (!edited) {
      return;
    }
    file = edited;

    // Conserve aussi l'image dans la galerie si on peut encore ajouter
    if (this.canAddPhoto()) {
      this.addNewPhoto(file);
    }

    this.scanningLabel.set(true);
    this.aiService.scanLabel(file).subscribe({
      next: (res) => {
        this.scanningLabel.set(false);
        this.applyLabelScan(res);
      },
      error: (err) => {
        this.scanningLabel.set(false);
        this.error.set(
          apiErrorMessage(err, 'Scan IA impossible. Vérifiez que l’assistant est activé dans Paramètres.')
        );
      }
    });
  }

  private applyLabelScan(res: {
    nom?: string | null;
    reference?: string | null;
    marque?: string | null;
    usage?: string | null;
    notes?: string | null;
  }): void {
    const patch: Record<string, string> = {};
    if (res.nom?.trim()) {
      patch['nom'] = res.nom.trim().slice(0, 120);
    }
    if (res.reference?.trim()) {
      patch['reference'] = res.reference.trim().slice(0, 80);
    }
    if (res.usage?.trim()) {
      patch['usage'] = res.usage.trim().slice(0, 500);
    }
    if (Object.keys(patch).length > 0) {
      this.form.patchValue(patch);
    }
    const hints: string[] = [];
    if (res.marque?.trim()) {
      hints.push(`Marque détectée : ${res.marque.trim()} (à rattacher via MAS si besoin)`);
    }
    if (res.notes?.trim()) {
      hints.push(res.notes.trim());
    }
    if (hints.length === 0 && Object.keys(patch).length === 0) {
      this.aiHint.set('Aucune information lisible sur l’étiquette. Réessayez avec une photo plus nette.');
    } else if (hints.length > 0) {
      this.aiHint.set(hints.join(' · '));
    } else {
      this.aiHint.set('Champs préremplis à partir de l’étiquette.');
    }
  }

  private async captureBlobAsFile(): Promise<File | null> {
    const video = this.videoEl?.nativeElement;
    const canvas = this.canvasEl?.nativeElement;
    if (!video || !canvas || !this.cameraReady()) {
      return null;
    }
    canvas.width = video.videoWidth || 1280;
    canvas.height = video.videoHeight || 720;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      return null;
    }
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob((b) => resolve(b), 'image/jpeg', 0.85)
    );
    if (!blob) {
      return null;
    }
    return new File([blob], `etiquette-${Date.now()}.jpg`, { type: 'image/jpeg' });
  }

  /** Ouvre le sélecteur de fichiers pour ajouter des images depuis la galerie. */
  openGallery(): void {
    if (!this.canAddPhoto()) {
      this.error.set(`Maximum ${this.maxPhotos} images par pièce.`);
      return;
    }
    this.fileInput?.nativeElement.click();
  }

  /** Traite les fichiers image sélectionnés depuis la galerie (édition avant ajout). */
  async onGallerySelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    for (const file of files) {
      if (!this.canAddPhoto()) {
        this.error.set(`Maximum ${this.maxPhotos} images par pièce.`);
        break;
      }
      if (!file.type.startsWith('image/')) {
        this.error.set('Seules les images sont acceptées.');
        continue;
      }
      const edited = await this.openImageEditor(file, 'Recadrer l’image');
      if (edited) {
        this.addNewPhoto(edited);
      }
    }
  }

  /** Ouvre l'éditeur (zoom / recadrage) sur une nouvelle photo déjà ajoutée. */
  async editNewPhoto(index: number): Promise<void> {
    const current = this.newPhotos()[index];
    if (!current) {
      return;
    }
    const edited = await this.openImageEditor(current.file, 'Modifier l’image');
    if (!edited) {
      return;
    }
    URL.revokeObjectURL(current.previewUrl);
    this.newPhotos.update((list) => {
      const next = [...list];
      next[index] = { file: edited, previewUrl: URL.createObjectURL(edited) };
      return next;
    });
  }

  /** Retire une photo déjà enregistrée de la liste à conserver. */
  removeExistingPhoto(photoId: number): void {
    this.existingPhotos.update((list) => list.filter((p) => p.id !== photoId));
  }

  /** Retire une nouvelle photo non encore enregistrée. */
  removeNewPhoto(index: number): void {
    const list = [...this.newPhotos()];
    const [removed] = list.splice(index, 1);
    if (removed) {
      URL.revokeObjectURL(removed.previewUrl);
    }
    this.newPhotos.set(list);
  }

  /** Valide et enregistre la pièce (création ou mise à jour). */
  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.photoCount() < 1) {
      this.error.set("Ajoutez au moins une image avant d'enregistrer.");
      return;
    }
    if (this.photoCount() > this.maxPhotos) {
      this.error.set(`Maximum ${this.maxPhotos} images par pièce.`);
      return;
    }

    if (this.hasMarqueMismatch()) {
      this.marqueMismatchOpen.set(true);
      return;
    }

    this.saveDevice();
  }

  /** L'utilisateur a pris connaissance de l'incompatibilité — corriger le choix SFM/MAS. */
  confirmMarqueMismatch(): void {
    this.marqueMismatchOpen.set(false);
  }

  /** Ferme l'avertissement sans enregistrer. */
  cancelMarqueMismatch(): void {
    this.marqueMismatchOpen.set(false);
  }

  /** Message détaillé pour la popup d'incompatibilité SFM / MAS. */
  marqueMismatchMessage(): string {
    const sfmId = this.form.controls.sfmId.value;
    const masId = this.form.controls.masId.value;
    const sfm = this.sfms().find((s) => s.id === sfmId);
    const mas = this.masses().find((m) => m.id === masId);
    const sfmMarques =
      sfm?.marques?.map((m) => m.label).filter(Boolean).join(', ') ||
      'aucune marque définie';
    const masMarque = mas?.marqueLabel || mas?.marque || '—';
    return (
      `La marque de la MAS « ${mas?.numero || '—'} » (${masMarque}) ` +
      `ne fait pas partie des marques du SFM « ${sfm?.nom || '—'} » (${sfmMarques}). ` +
      `Corrigez le SFM ou la MAS avant d'enregistrer.`
    );
  }

  private hasMarqueMismatch(): boolean {
    const sfmId = this.form.controls.sfmId.value;
    const masId = this.form.controls.masId.value;
    if (sfmId == null || masId == null) {
      return false;
    }
    const sfm = this.sfms().find((s) => s.id === sfmId);
    const mas = this.masses().find((m) => m.id === masId);
    if (!sfm || !mas) {
      return false;
    }
    const marqueIds = new Set(sfm.marqueIds ?? sfm.marques?.map((m) => m.id) ?? []);
    if (marqueIds.size === 0) {
      return false;
    }
    return !marqueIds.has(mas.marqueId);
  }

  private saveDevice(): void {
    const keepPhotoIds = this.existingPhotos()
      .map((p) => p.id)
      .filter((id) => id > 0);
    const payload = {
      ...(this.form.getRawValue() as DeviceForm),
      keepPhotoIds
    };
    const files = this.newPhotos().map((p) => p.file);

    this.saving.set(true);
    this.error.set(null);
    this.draftService.clear();
    const req$ = this.id
      ? this.deviceService.update(this.id, payload, files)
      : this.deviceService.create(payload, files);
    req$.subscribe({
      next: (saved) => {
        this.saving.set(false);
        if (this.forOrderRequest) {
          this.router.navigate(['/order-request'], { queryParams: { deviceId: saved.id } });
          return;
        }
        if (this.isEdit) {
          this.router.navigate(['/devices', saved.id]);
          return;
        }
        this.savedDeviceId = saved.id;
        this.offerAnotherOpen.set(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Enregistrement impossible.'));
      }
    });
  }

  /** Réinitialise le formulaire pour saisir une autre pièce après création. */
  addAnotherDevice(): void {
    this.offerAnotherOpen.set(false);
    this.resetFormForAnother();
  }

  /** Termine la création et navigue vers la fiche de la pièce enregistrée. */
  skipAnotherDevice(): void {
    this.offerAnotherOpen.set(false);
    const id = this.savedDeviceId;
    this.savedDeviceId = null;
    if (id != null) {
      this.router.navigate(['/devices', id]);
    } else {
      this.router.navigate(['/devices']);
    }
  }

  private resetFormForAnother(): void {
    this.savedDeviceId = null;
    this.error.set(null);
    this.stopCamera();
    for (const item of this.newPhotos()) {
      URL.revokeObjectURL(item.previewUrl);
    }
    this.newPhotos.set([]);
    this.existingPhotos.set([]);
    const keepSfmId = this.form.controls.sfmId.value;
    const keepMasId = this.form.controls.masId.value;
    this.form.reset({
      nom: '',
      reference: '',
      usage: '',
      dateAcquisition: this.todayIso(),
      obsolete: false,
      stock: 0,
      sfmId: keepSfmId,
      masId: keepMasId
    });
    this.selectedSfmId.set(keepSfmId);
    this.selectedMasId.set(keepMasId);
    this.draftService.clear();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  /** Annule la saisie et retourne à la liste ou à la fiche. */
  cancel(): void {
    this.draftService.clear();
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

  private persistDraft(): void {
    const raw = this.form.getRawValue();
    this.draftService.save({
      editId: this.id,
      forOrderRequest: this.forOrderRequest,
      form: {
        nom: raw.nom ?? '',
        reference: raw.reference ?? '',
        usage: raw.usage ?? '',
        dateAcquisition: raw.dateAcquisition || this.todayIso(),
        obsolete: !!raw.obsolete,
        stock: Number(raw.stock) || 0,
        sfmId: raw.sfmId ?? null,
        masId: raw.masId ?? null
      },
      existingPhotos: [...this.existingPhotos()],
      newPhotos: [...this.newPhotos()]
    });
  }

  private returnDeviceQuery(): Record<string, string> {
    const query: Record<string, string> = {
      returnDevice: this.id != null ? String(this.id) : 'new'
    };
    if (this.forOrderRequest) {
      query['forOrderRequest'] = '1';
    }
    return query;
  }

  private applyQuerySelections(sfmId: number | null, masId: number | null): void {
    const patch: { sfmId?: number | null; masId?: number | null } = {};
    if (sfmId != null) {
      patch.sfmId = sfmId;
      this.selectedSfmId.set(sfmId);
    }
    if (masId != null) {
      patch.masId = masId;
      this.selectedMasId.set(masId);
      this.associatedMasHint.set('MAS associée automatiquement après création.');
    }
    if (Object.keys(patch).length > 0) {
      // emitEvent:false évite que le changement de SFM efface la MAS nouvellement associée
      this.form.patchValue(patch, { emitEvent: false });
    }
  }

  private parseOptionalId(raw: string | null): number | null {
    if (raw == null || raw === '') {
      return null;
    }
    const value = Number(raw);
    return Number.isFinite(value) ? value : null;
  }

  private todayIso(): string {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  /** Ouvre l'éditeur d'image (zoom, recadrage, rotation, miroir). */
  private async openImageEditor(file: File, title: string): Promise<File | null> {
    const ref = this.dialog.open(ImageEditorDialogComponent, {
      data: { file, title },
      width: 'min(960px, 96vw)',
      maxHeight: '94vh',
      autoFocus: false,
      disableClose: true
    });
    const result = await firstValueFrom(ref.afterClosed());
    return result ?? null;
  }

  private addNewPhoto(file: File): void {
    this.newPhotos.update((list) => [
      ...list,
      { file, previewUrl: URL.createObjectURL(file) }
    ]);
    this.error.set(null);
  }

  private revokeNewPreviews(): void {
    for (const item of this.newPhotos()) {
      URL.revokeObjectURL(item.previewUrl);
    }
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
