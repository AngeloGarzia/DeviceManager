import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { InterventionTechnique } from '../../models/models';
import { InterventionTechniqueService } from '../../services/intervention-technique.service';
import { apiErrorMessage } from '../../shared/api-error';

@Component({
  selector: 'app-intervention-technique-list',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './intervention-technique-list.component.html',
  styleUrl: './intervention-technique-list.component.scss'
})
export class InterventionTechniqueListComponent implements OnInit {
  private readonly service = inject(InterventionTechniqueService);

  readonly items = signal<InterventionTechnique[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.list().subscribe({
      next: (list) => {
        this.items.set(list);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Impossible de charger les interventions.'));
      }
    });
  }

  masLabel(item: InterventionTechnique): string {
    const marque = (item.masMarque || '').trim();
    return marque ? `${item.masNumero} — ${marque}` : item.masNumero || '—';
  }
}
