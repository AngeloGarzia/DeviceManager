import { Component, OnInit, effect, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { AuthService } from '../services/auth.service';
import { OrderRequestService } from '../services/order-request.service';
import { AiService } from '../services/ai.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatFormFieldModule,
    MatSelectModule
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss'
})
export class ShellComponent implements OnInit {
  readonly auth = inject(AuthService);
  readonly orders = inject(OrderRequestService);
  readonly ai = inject(AiService);
  readonly router = inject(Router);

  constructor() {
    effect(() => {
      // Recharge le badge à chaque changement d'atelier
      this.auth.atelierRevision();
      this.auth.atelierId();
      if (this.auth.getToken()) {
        this.orders.refreshPendingCount();
      }
    });
  }

  ngOnInit(): void {
    if (this.auth.getToken()) {
      this.orders.refreshPendingCount();
      this.ai.refreshStatus();
    }
  }

  openAiAssistant(): void {
    if (!this.ai.enabled()) {
      return;
    }
    void this.router.navigate(['/ai']);
  }

  logout(): void {
    this.ai.reset();
    this.auth.logout();
  }
}
