import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/**
 * Page publique Mentions légales + Politique de confidentialité (RGPD).
 * Modèle à compléter / valider par le responsable du traitement.
 */
@Component({
  selector: 'app-privacy',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule],
  templateUrl: './privacy.component.html',
  styleUrl: './privacy.component.scss'
})
export class PrivacyComponent {
  readonly updatedAt = '10 août 2026';
}
