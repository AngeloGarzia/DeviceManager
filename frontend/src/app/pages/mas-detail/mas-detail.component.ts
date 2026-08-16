import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Mas } from '../../models/models';
import { MasService } from '../../services/mas.service';
import { AuthService } from '../../services/auth.service';
import { apiErrorMessage } from '../../shared/api-error';
import { isPdfOrImageFile, PDF_OR_IMAGE_ACCEPT } from '../../shared/document-upload';
import { masStatutBadgeClass, masStatutLabel } from '../../shared/mas-statut';

/**
 * Fiche détaillée d'une MAS.
 * Affiche numéro, marque, statut — pas de suppression (changement de statut uniquement).
 */
@Component({
  selector: 'app-mas-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule, MatCardModule, MatProgressSpinnerModule],
  templateUrl: './mas-detail.component.html',
  styleUrl: './mas-detail.component.scss'
})
export class MasDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly masService = inject(MasService);
  readonly auth = inject(AuthService);
  readonly item = signal<Mas | null>(null);
  readonly statutLabel = masStatutLabel;
  readonly statutBadgeClass = masStatutBadgeClass;
  readonly loading = signal(true);
  readonly uploading = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);
  readonly pdfOrImageAccept = PDF_OR_IMAGE_ACCEPT;

  ngOnInit(): void {
    this.masService.get(Number(this.route.snapshot.paramMap.get('id'))).subscribe({
      next: (data) => { this.item.set(data); this.loading.set(false); },
      error: () => { this.error.set('MAS introuvable.'); this.loading.set(false); }
    });
  }

  isDetruite(mas: Mas): boolean {
    return mas.statut === 'DETRUITE';
  }

  isImageDoc(mas: Mas): boolean {
    const ct = (mas.destructionContentType || '').toLowerCase();
    const name = (mas.destructionOriginalName || '').toLowerCase();
    return ct.startsWith('image/') || /\.(png|jpe?g|webp|gif)$/.test(name);
  }

  destructionUrl(mas: Mas): string {
    return this.masService.resolveFileUrl(mas.destructionFileUrl);
  }

  pickDestructionFile(): void {
    const input = document.getElementById('bon-destruction-input') as HTMLInputElement | null;
    input?.click();
  }

  onDestructionSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const mas = this.item();
    if (!file || !mas || !this.isDetruite(mas)) {
      return;
    }
    if (!isPdfOrImageFile(file)) {
      this.error.set('Le bon de destruction doit être un PDF ou une image.');
      return;
    }
    this.uploading.set(true);
    this.error.set(null);
    this.success.set(null);
    this.masService.attachBonDestruction(mas.id, file).subscribe({
      next: (updated) => {
        this.item.set(updated);
        this.uploading.set(false);
        this.success.set('Bon de destruction associé.');
      },
      error: (err) => {
        this.uploading.set(false);
        this.error.set(apiErrorMessage(err, 'Envoi du bon de destruction impossible.'));
      }
    });
  }
}
