import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AiService } from '../../services/ai.service';

interface ChatTurn {
  role: 'user' | 'assistant';
  text: string;
}

/**
 * Assistant conversationnel IA intégré à DeviceManager.
 * Permet d'interroger le modèle configuré sur le parc de pièces et les processus métier.
 */
@Component({
  selector: 'app-ai-assistant',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatCardModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './ai-assistant.component.html',
  styleUrl: './ai-assistant.component.scss'
})
export class AiAssistantComponent implements OnInit {
  readonly ai = inject(AiService);
  private readonly fb = inject(FormBuilder);

  readonly loadingStatus = signal(true);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  readonly turns = signal<ChatTurn[]>([]);

  readonly form = this.fb.group({
    message: ['', [Validators.required, Validators.maxLength(4000)]]
  });

  ngOnInit(): void {
    this.ai.status().subscribe({
      next: (res) => {
        this.loadingStatus.set(false);
        if (!res.enabled) {
          this.error.set(res.reply);
        }
      },
      error: () => {
        this.loadingStatus.set(false);
        this.error.set('Impossible de contacter l’assistant IA.');
      }
    });
  }

  /** Envoie le message saisi et affiche la réponse de l'assistant. */
  send(): void {
    if (this.form.invalid || this.sending() || !this.ai.enabled()) {
      this.form.markAllAsTouched();
      return;
    }
    const message = this.form.controls.message.value!.trim();
    this.turns.update((list) => [...list, { role: 'user', text: message }]);
    this.form.reset();
    this.sending.set(true);
    this.error.set(null);
    this.ai.chat(message).subscribe({
      next: (res) => {
        this.turns.update((list) => [...list, { role: 'assistant', text: res.reply }]);
        this.sending.set(false);
      },
      error: (err) => {
        this.sending.set(false);
        this.error.set(err?.error?.message || 'Échec de la réponse IA.');
      }
    });
  }
}
