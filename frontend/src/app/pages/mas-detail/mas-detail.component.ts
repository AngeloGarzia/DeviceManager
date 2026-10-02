import { Component, ElementRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import QRCode from 'qrcode';
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
  @ViewChild('qrCanvas') qrCanvas?: ElementRef<HTMLCanvasElement>;

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
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

  readonly qrLoading = signal(false);
  readonly qrUrl = signal<string | null>(null);
  readonly qrVisible = signal(false);

  ngOnInit(): void {
    this.masService.get(Number(this.route.snapshot.paramMap.get('id'))).subscribe({
      next: (data) => {
        this.item.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('MAS introuvable.');
        this.loading.set(false);
      }
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

  openRegleJeuxPdf(regle: { id: number; originalName?: string | null }): void {
    void this.router.navigate(['/mas/regles-jeux', regle.id]);
  }

  isRegleImage(regle: { contentType?: string | null; originalName?: string | null }): boolean {
    const ct = (regle.contentType || '').toLowerCase();
    const name = (regle.originalName || '').toLowerCase();
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

  showPublicQr(): void {
    const mas = this.item();
    if (!mas) {
      return;
    }
    this.qrLoading.set(true);
    this.error.set(null);
    this.masService.ensurePublicAccessToken(mas.id).subscribe({
      next: async (res) => {
        this.item.update((cur) => (cur ? { ...cur, publicAccessToken: res.token } : cur));
        const url = this.buildPublicUrl(res.token, mas);
        this.qrUrl.set(url);
        this.qrVisible.set(true);
        this.qrLoading.set(false);
        await this.renderQr(url);
      },
      error: (err) => {
        this.qrLoading.set(false);
        this.error.set(apiErrorMessage(err, 'Impossible de générer le QR code.'));
      }
    });
  }

  rotatePublicQr(): void {
    const mas = this.item();
    if (!mas) {
      return;
    }
    if (!confirm('Régénérer le QR ? Les codes déjà imprimés ne fonctionneront plus.')) {
      return;
    }
    this.qrLoading.set(true);
    this.error.set(null);
    this.masService.rotatePublicAccessToken(mas.id).subscribe({
      next: async (res) => {
        this.item.update((cur) => (cur ? { ...cur, publicAccessToken: res.token } : cur));
        const url = this.buildPublicUrl(res.token, mas);
        this.qrUrl.set(url);
        this.qrVisible.set(true);
        this.qrLoading.set(false);
        this.success.set('QR régénéré — réimprimez les supports.');
        await this.renderQr(url);
      },
      error: (err) => {
        this.qrLoading.set(false);
        this.error.set(apiErrorMessage(err, 'Régénération du QR impossible.'));
      }
    });
  }

  copyPublicUrl(): void {
    const url = this.qrUrl();
    if (!url || !navigator.clipboard) {
      return;
    }
    void navigator.clipboard.writeText(url).then(() => {
      this.success.set('Lien public copié.');
    });
  }

  /**
   * URL absolue scannable depuis n’importe quel téléphone (navigateur, sans app).
   * Une seule règle → lien direct visionneuse ; sinon liste publique.
   */
  private buildPublicUrl(token: string, mas: Mas): string {
    const origin = window.location.origin;
    const rules = mas.reglesJeux ?? [];
    if (rules.length === 1) {
      return `${origin}/public/r/${token}/regles/${rules[0].id}`;
    }
    return `${origin}/public/r/${token}`;
  }

  private async renderQr(url: string): Promise<void> {
    await new Promise<void>((resolve) => setTimeout(resolve, 0));
    const canvas = this.qrCanvas?.nativeElement;
    if (!canvas) {
      return;
    }
    await QRCode.toCanvas(canvas, url, {
      width: 220,
      margin: 2,
      color: { dark: '#0f172a', light: '#ffffff' }
    });
  }
}
