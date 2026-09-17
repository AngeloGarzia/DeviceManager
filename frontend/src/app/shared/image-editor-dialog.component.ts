import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  ImageCroppedEvent,
  ImageCropperComponent,
  ImageTransform
} from 'ngx-image-cropper';

export interface ImageEditorDialogData {
  file: File;
  title?: string;
}

/** Sortie fixe des photos pièces : 4/3 à 800×600. */
export const DEVICE_PHOTO_WIDTH = 800;
export const DEVICE_PHOTO_HEIGHT = 600;
export const DEVICE_PHOTO_ASPECT = DEVICE_PHOTO_WIDTH / DEVICE_PHOTO_HEIGHT;

/**
 * Dialogue d'édition d'image : zoom, recadrage 4:3 manuel, rotation et miroir.
 * Retourne un fichier JPEG 800×600, ou {@code null} si annulé.
 */
@Component({
  selector: 'app-image-editor-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    ImageCropperComponent
  ],
  templateUrl: './image-editor-dialog.component.html',
  styleUrl: './image-editor-dialog.component.scss'
})
export class ImageEditorDialogComponent {
  readonly data = inject<ImageEditorDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ImageEditorDialogComponent, File | null>);

  readonly imageFile = this.data.file;
  readonly title = this.data.title || 'Éditer l’image';
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly aspectRatio = DEVICE_PHOTO_ASPECT;
  readonly outputWidth = DEVICE_PHOTO_WIDTH;
  readonly outputHeight = DEVICE_PHOTO_HEIGHT;

  transform: ImageTransform = { scale: 1, rotate: 0, flipH: false, flipV: false };
  roundCropper = false;

  private croppedBlob: Blob | null = null;

  /** Zoom avant. */
  zoomIn(): void {
    const scale = Math.min(3, (this.transform.scale || 1) + 0.1);
    this.transform = { ...this.transform, scale };
  }

  /** Zoom arrière. */
  zoomOut(): void {
    const scale = Math.max(0.4, (this.transform.scale || 1) - 0.1);
    this.transform = { ...this.transform, scale };
  }

  /** Rotation de 90° vers la gauche. */
  rotateLeft(): void {
    const rotate = ((this.transform.rotate || 0) - 90 + 360) % 360;
    this.transform = { ...this.transform, rotate };
  }

  /** Rotation de 90° vers la droite. */
  rotateRight(): void {
    const rotate = ((this.transform.rotate || 0) + 90) % 360;
    this.transform = { ...this.transform, rotate };
  }

  /** Miroir horizontal. */
  flipHorizontal(): void {
    this.transform = { ...this.transform, flipH: !this.transform.flipH };
  }

  /** Miroir vertical. */
  flipVertical(): void {
    this.transform = { ...this.transform, flipV: !this.transform.flipV };
  }

  /** Réinitialise zoom, rotation et miroirs (le ratio 4:3 reste imposé). */
  resetTransforms(): void {
    this.transform = { scale: 1, rotate: 0, flipH: false, flipV: false };
  }

  onImageCropped(event: ImageCroppedEvent): void {
    this.croppedBlob = event.blob ?? null;
  }

  onLoadFailed(): void {
    this.error.set('Impossible de charger l’image.');
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  /** Valide le recadrage et renvoie un fichier JPEG 800×600. */
  async apply(): Promise<void> {
    if (!this.croppedBlob) {
      this.error.set('Recadrez l’image avant de valider.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      const name = this.imageFile.name.replace(/\.[^.]+$/, '') || `image-${Date.now()}`;
      const file = await this.toFixedJpeg(this.croppedBlob, `${name}-edit.jpg`);
      this.dialogRef.close(file);
    } catch {
      this.error.set('Enregistrement de l’image impossible.');
      this.saving.set(false);
    }
  }

  /** Garantit exactement 800×600 même si le cropper dérive légèrement. */
  private async toFixedJpeg(blob: Blob, filename: string): Promise<File> {
    const bitmap = await createImageBitmap(blob);
    try {
      const canvas = document.createElement('canvas');
      canvas.width = DEVICE_PHOTO_WIDTH;
      canvas.height = DEVICE_PHOTO_HEIGHT;
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        throw new Error('Canvas 2D indisponible');
      }
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(0, 0, DEVICE_PHOTO_WIDTH, DEVICE_PHOTO_HEIGHT);
      ctx.drawImage(bitmap, 0, 0, DEVICE_PHOTO_WIDTH, DEVICE_PHOTO_HEIGHT);
      const out = await new Promise<Blob>((resolve, reject) => {
        canvas.toBlob(
          (b) => (b ? resolve(b) : reject(new Error('toBlob failed'))),
          'image/jpeg',
          0.82
        );
      });
      return new File([out], filename, { type: 'image/jpeg' });
    } finally {
      bitmap.close();
    }
  }
}
