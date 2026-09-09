import { Component, EventEmitter, Input, Output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MemoireSynaptiqueResponse } from '../services/ai.service';

/**
 * Modale de briefing atelier après changement d'atelier :
 * snapshot mémoire synaptique + rapport (IA ou texte local).
 */
@Component({
  selector: 'app-atelier-situation-dialog',
  standalone: true,
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './atelier-situation-dialog.component.html',
  styleUrl: './atelier-situation-dialog.component.scss'
})
export class AtelierSituationDialogComponent {
  @Input() open = false;
  @Input() loading = false;
  @Input() error: string | null = null;
  @Input() memory: MemoireSynaptiqueResponse | null = null;
  @Input() report: string | null = null;
  @Output() dismissed = new EventEmitter<void>();
}
