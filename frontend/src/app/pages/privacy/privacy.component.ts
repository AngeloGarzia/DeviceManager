import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { PrivacyService } from '../../services/privacy.service';

/**
 * Page publique Mentions légales + Politique de confidentialité (RGPD).
 * Les champs variables sont chargés depuis l'API (éditables dans Setup).
 */
@Component({
  selector: 'app-privacy',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './privacy.component.html',
  styleUrl: './privacy.component.scss'
})
export class PrivacyComponent implements OnInit {
  private readonly privacy = inject(PrivacyService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  private fields: Record<string, string> = {};

  ngOnInit(): void {
    this.privacy.getPolicyFields().subscribe({
      next: (data) => {
        this.fields = data ?? {};
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les mentions. Les valeurs par défaut s’affichent.');
        this.loading.set(false);
      }
    });
  }

  /** Date affichée (Setup) ou valeur de repli. */
  get updatedAt(): string {
    return this.display('PRIVACY_LAST_UPDATED', '—');
  }

  /**
   * Affiche la valeur Setup, ou un marqueur « À compléter » si vide.
   */
  display(key: string, emptyLabel = '[À COMPLÉTER]'): string {
    const v = (this.fields[key] ?? '').trim();
    return v || emptyLabel;
  }

  /** True si au moins un champ obligatoire légal est encore vide. */
  get hasIncomplete(): boolean {
    const keys = [
      'PRIVACY_EDITOR_LEGAL',
      'PRIVACY_PUBLICATION_DIRECTOR',
      'PRIVACY_EDITOR_CONTACT',
      'PRIVACY_HOSTING',
      'PRIVACY_CONTROLLER',
      'PRIVACY_PROCESSORS',
      'PRIVACY_TRANSFER',
      'PRIVACY_RIGHTS_EMAIL',
      'PRIVACY_POSTAL_ADDRESS'
    ];
    return keys.some((k) => !(this.fields[k] ?? '').trim());
  }
}
