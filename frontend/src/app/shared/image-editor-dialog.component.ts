import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
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

/**
 * Dialogue d'édition d'image : zoom, recadrage, rotation et miroir.
 * Retourne un fichier JPEG recadré, ou {@code null} si annulé.
 */
@Component({
  selector: 'app-image-editor-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatButtonToggleModule,
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

  transform: ImageTransform = { scale: 1, rotate: 0, flipH: false, flipV: false };
  aspectRatio = 0;
  maintainAspectRatio = false;
  roundCropper = false;

  private croppedBlob: Blob | null = null;

  /** Change le ratio de recadrage (0 = libre). */
  setAspect(ratio: number): void {
    this.aspectRatio = ratio;
    this.maintainAspectRatio = ratio > 0;
  }

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

  /** Réinitialise zoom, rotation et miroirs. */
  resetTransforms(): void {
    this.transform = { scale: 1, rotate: 0, flipH: false, flipV: false };
    this.setAspect(0);
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

  /** Valide le recadrage et renvoie un fichier JPEG. */
  async apply(): Promise<void> {
    if (!this.croppedBlob) {
      this.error.set('Recadrez l’image avant de valider.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      const name = this.imageFile.name.replace(/\.[^.]+$/, '') || `image-${Date.now()}`;
      const file = new File([this.croppedBlob], `${name}-edit.jpg`, { type: 'image/jpeg' });
      this.dialogRef.close(file);
    } catch {
      this.error.set('Enregistrement de l’image impossible.');
      this.saving.set(false);
    }
  }
}
