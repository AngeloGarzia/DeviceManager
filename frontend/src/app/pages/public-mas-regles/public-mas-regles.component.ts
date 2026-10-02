import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  PublicMasRegles,
  PublicMasReglesService,
  PublicRegleJeux
} from '../../services/public-mas-regles.service';
import { apiErrorMessage } from '../../shared/api-error';

/**
 * Page publique : liste des règles de jeux d'une MAS (accès par jeton QR, sans connexion).
 * Si une seule règle : redirection automatique vers la visionneuse.
 */
@Component({
  selector: 'app-public-mas-regles',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './public-mas-regles.component.html',
  styleUrl: './public-mas-regles.component.scss'
})
export class PublicMasReglesComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly publicApi = inject(PublicMasReglesService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly data = signal<PublicMasRegles | null>(null);
  readonly token = signal('');

  ngOnInit(): void {
    const token = (this.route.snapshot.paramMap.get('token') || '').trim();
    this.token.set(token);
    if (!token) {
      this.loading.set(false);
      this.error.set('Lien invalide.');
      return;
    }
    this.publicApi.byToken(token).subscribe({
      next: (data) => {
        // Une seule règle → ouverture directe (parcours QR téléphone).
        if (data.regles?.length === 1) {
          void this.router.navigate(['/public/r', token, 'regles', data.regles[0].id], {
            replaceUrl: true
          });
          return;
        }
        this.data.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Lien invalide ou règles indisponibles.'));
      }
    });
  }

  isImage(regle: PublicRegleJeux): boolean {
    const ct = (regle.contentType || '').toLowerCase();
    const name = (regle.originalName || '').toLowerCase();
    return ct.startsWith('image/') || /\.(png|jpe?g|webp|gif)$/.test(name);
  }
}
