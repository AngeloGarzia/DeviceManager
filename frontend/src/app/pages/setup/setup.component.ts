import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { AppSetting } from '../../models/models';
import { SetupService } from '../../services/setup.service';

@Component({
  selector: 'app-setup',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule
  ],
  templateUrl: './setup.component.html',
  styleUrl: './setup.component.scss'
})
export class SetupComponent implements OnInit {
  private readonly setupService = inject(SetupService);
  private readonly fb = inject(FormBuilder);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly testingMail = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal(false);
  readonly mailTestMessage = signal<string | null>(null);
  readonly settings = signal<AppSetting[]>([]);
  form: FormGroup = this.fb.group({});

  readonly categories = computed(() => {
    const map = new Map<string, AppSetting[]>();
    for (const item of this.settings()) {
      const list = map.get(item.category) ?? [];
      list.push(item);
      map.set(item.category, list);
    }
    return [...map.entries()];
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.setupService.list().subscribe({
      next: (data) => {
        this.settings.set(data);
        const group: Record<string, FormControl<string>> = {};
        for (const item of data) {
          group[item.key] = this.fb.nonNullable.control(item.value ?? '');
        }
        this.form = this.fb.group(group);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message || 'Chargement du setup impossible.');
      }
    });
  }

  control(key: string): FormControl<string> {
    return this.form.get(key) as FormControl<string>;
  }

  isBooleanSetting(key: string): boolean {
    return key === 'MAIL_ENABLED' || key === 'S3_ENABLED';
  }

  booleanValue(key: string): boolean {
    const value = this.control(key)?.value ?? '';
    return value === 'true' || value === '1' || value.toLowerCase() === 'yes';
  }

  toggleBoolean(key: string, checked: boolean): void {
    this.control(key)?.setValue(checked ? 'true' : 'false');
  }

  submit(): void {
    this.saving.set(true);
    this.error.set(null);
    this.success.set(false);
    this.mailTestMessage.set(null);
    const values = this.form.getRawValue() as Record<string, string>;
    this.setupService.update(values).subscribe({
      next: (data) => {
        this.settings.set(data);
        for (const item of data) {
          this.control(item.key)?.setValue(item.value ?? '', { emitEvent: false });
        }
        this.saving.set(false);
        this.success.set(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(err?.error?.message || 'Enregistrement impossible.');
      }
    });
  }

  testMail(): void {
    this.testingMail.set(true);
    this.error.set(null);
    this.mailTestMessage.set(null);
    // Enregistre d'abord les valeurs du formulaire pour que le test utilise la config saisie
    const values = this.form.getRawValue() as Record<string, string>;
    this.setupService.update(values).subscribe({
      next: (data) => {
        this.settings.set(data);
        for (const item of data) {
          this.control(item.key)?.setValue(item.value ?? '', { emitEvent: false });
        }
        this.setupService.testMail().subscribe({
          next: (res) => {
            this.testingMail.set(false);
            this.mailTestMessage.set(res.message || 'E-mail de test envoyé.');
          },
          error: (err) => {
            this.testingMail.set(false);
            this.error.set(err?.error?.message || 'Échec de l\'envoi de test.');
          }
        });
      },
      error: (err) => {
        this.testingMail.set(false);
        this.error.set(err?.error?.message || 'Enregistrement impossible avant le test.');
      }
    });
  }
}
