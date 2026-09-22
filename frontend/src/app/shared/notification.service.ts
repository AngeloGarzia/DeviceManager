import { Injectable, inject } from '@angular/core';
import { MatSnackBar, MatSnackBarConfig } from '@angular/material/snack-bar';

/**
 * Notifications globales (snackbar Material).
 * Point unique pour afficher succès / info / erreurs à l'utilisateur,
 * notamment depuis l'intercepteur d'erreurs HTTP.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snackBar = inject(MatSnackBar);

  private readonly baseConfig: MatSnackBarConfig = {
    horizontalPosition: 'right',
    verticalPosition: 'bottom'
  };

  /** Toast succès (fermeture auto ~3.5s). */
  success(message: string): void {
    this.open(message, 'Fermer', {
      ...this.baseConfig,
      duration: 3500,
      panelClass: ['dm-snack', 'dm-snack--success']
    });
  }

  /** Toast information (fermeture auto ~4s). */
  info(message: string): void {
    this.open(message, 'Fermer', {
      ...this.baseConfig,
      duration: 4000,
      panelClass: ['dm-snack', 'dm-snack--info']
    });
  }

  /** Avertissement (fermeture auto ~5s). */
  warning(message: string): void {
    this.open(message, 'Fermer', {
      ...this.baseConfig,
      duration: 5000,
      panelClass: ['dm-snack', 'dm-snack--warning']
    });
  }

  /** Erreur (fermeture auto ~6s, plus long pour lecture). */
  error(message: string): void {
    this.open(message, 'Fermer', {
      ...this.baseConfig,
      duration: 6000,
      panelClass: ['dm-snack', 'dm-snack--error']
    });
  }

  private open(message: string, action: string, config: MatSnackBarConfig): void {
    if (!message || !message.trim()) {
      return;
    }
    this.snackBar.open(message, action, config);
  }
}
