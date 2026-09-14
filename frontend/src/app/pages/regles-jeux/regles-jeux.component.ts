import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Mas, RegleJeuxOption, RegleJeuxPdfCheck } from '../../models/models';
import { MasService } from '../../services/mas.service';
import { apiErrorMessage } from '../../shared/api-error';
import { isPdfFile, PDF_ACCEPT } from '../../shared/document-upload';
import {
  RegleJeuxAiDialogComponent,
  RegleJeuxAiDialogConfirm
} from '../../shared/regle-jeux-ai-dialog.component';

type PdfCheckState = RegleJeuxPdfCheck | { status: 'loading' } | { status: 'error'; message: string };

/**
 * Catalogue global des règles de jeux (PDF) — gestion depuis le menu MAS.
 */
@Component({
  selector: 'app-regles-jeux',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    RegleJeuxAiDialogComponent
  ],
  templateUrl: './regles-jeux.component.html',
  styleUrl: './regles-jeux.component.scss'
})
export class ReglesJeuxComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly masService = inject(MasService);

  readonly items = signal<RegleJeuxOption[]>([]);
  readonly searchQuery = signal('');
  readonly filteredItems = computed(() => {
    const q = this.searchQuery().trim().toLowerCase();
    const list = this.items();
    if (!q) {
      return list;
    }
    return list.filter((item) => this.matchesSearch(item, q));
  });
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);
  readonly linkingRegleId = signal<number | null>(null);
  readonly masses = signal<Mas[]>([]);
  readonly loadingMasLinks = signal(false);
  readonly pdfAccept = PDF_ACCEPT;
  /** Contrôles PDF par id de règle. */
  readonly pdfChecks = signal<Record<number, PdfCheckState>>({});

  readonly aiDialogOpen = signal(false);
  readonly aiScanning = signal(false);
  readonly aiScanNotes = signal<string | null>(null);
  readonly aiSuggested = signal<{ label?: string | null; description?: string | null } | null>(null);
  pendingCreateFile: File | null = null;
  replaceFileTargetId: number | null = null;

  readonly editForm = this.fb.nonNullable.group({
    label: ['', [Validators.required, Validators.maxLength(200)]],
    description: ['', Validators.maxLength(500)]
  });

  readonly masLinkForm = this.fb.nonNullable.group({
    masIds: [[] as number[]]
  });

  ngOnInit(): void {
    this.reload();
    this.loadMasses();
  }

  loadMasses(): void {
    this.masService.list().subscribe({
      next: (list) => {
        this.masses.set([...list].sort((a, b) =>
          a.numero.localeCompare(b.numero, 'fr', { sensitivity: 'base' })
        ));
      }
    });
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.masService.listReglesJeux().subscribe({
      next: (list) => {
        this.items.set([...list].sort((a, b) =>
          (a.label || '').localeCompare(b.label || '', 'fr', { sensitivity: 'base' })
        ));
        this.loading.set(false);
        this.checkAllPdfs(list);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'Impossible de charger les règles de jeux.'));
      }
    });
  }

  checkAllPdfs(list: RegleJeuxOption[]): void {
    if (list.length === 0) {
      this.pdfChecks.set({});
      return;
    }
    const loadingMap: Record<number, PdfCheckState> = {};
    for (const item of list) {
      loadingMap[item.id] = { status: 'loading' };
    }
    this.pdfChecks.set(loadingMap);

    forkJoin(
      list.map((item) =>
        this.masService.checkRegleJeuxPdf(item.id).pipe(
          catchError((err) =>
            of({
              kind: 'error' as const,
              id: item.id,
              message: apiErrorMessage(err, 'Contrôle PDF impossible.')
            })
          )
        )
      )
    ).subscribe((results) => {
      const next: Record<number, PdfCheckState> = {};
      for (let i = 0; i < list.length; i++) {
        const item = list[i];
        const result = results[i] as RegleJeuxPdfCheck | { kind: 'error'; id: number; message: string };
        if (result && 'kind' in result && result.kind === 'error') {
          next[item.id] = { status: 'error', message: result.message };
        } else {
          next[item.id] = result as RegleJeuxPdfCheck;
        }
      }
      this.pdfChecks.set(next);
    });
  }

  recheckPdf(id: number): void {
    this.pdfChecks.update((map) => ({ ...map, [id]: { status: 'loading' } }));
    this.masService.checkRegleJeuxPdf(id).subscribe({
      next: (check) => {
        this.pdfChecks.update((map) => ({ ...map, [id]: check }));
      },
      error: (err) => {
        this.pdfChecks.update((map) => ({
          ...map,
          [id]: { status: 'error', message: apiErrorMessage(err, 'Contrôle PDF impossible.') }
        }));
      }
    });
  }

  pdfCheck(item: RegleJeuxOption): PdfCheckState | undefined {
    return this.pdfChecks()[item.id];
  }

  isPdfCheckLoading(state: PdfCheckState | undefined): boolean {
    return !!state && 'status' in state && state.status === 'loading';
  }

  pdfCheckErrorMessage(state: PdfCheckState | undefined): string | null {
    if (state && 'status' in state && state.status === 'error') {
      return state.message;
    }
    return null;
  }

  asPdfCheckResult(state: PdfCheckState | undefined): RegleJeuxPdfCheck | null {
    if (state && !('status' in state)) {
      return state;
    }
    return null;
  }

  pdfCheckBadgeClass(check: RegleJeuxPdfCheck): string {
    if (check.valid && check.readable) {
      return 'bg-emerald-50 text-emerald-800';
    }
    if (check.valid) {
      return 'bg-amber-50 text-amber-900';
    }
    return 'bg-rose-50 text-rose-800';
  }

  openCreate(): void {
    this.error.set(null);
    this.success.set(null);
    document.getElementById('regle-jeux-create-file')?.click();
  }

  onCreateFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (!file) {
      return;
    }
    if (!isPdfFile(file)) {
      this.error.set('La règle de jeux doit être un fichier PDF.');
      return;
    }
    this.pendingCreateFile = file;
    this.aiSuggested.set(null);
    this.aiScanNotes.set(null);
    this.aiDialogOpen.set(true);
    this.aiScanning.set(true);
    this.error.set(null);

    this.masService.analyzeRegleJeuxPdf(file).subscribe({
      next: (scan) => {
        this.aiScanning.set(false);
        this.aiSuggested.set({
          label: scan.label,
          description: scan.description
        });
        if (!scan.enabled) {
          this.aiScanNotes.set(
            scan.notes || 'IA indisponible — saisissez le libellé et la description manuellement.'
          );
        } else if (scan.notes?.trim()) {
          this.aiScanNotes.set(scan.notes.trim());
        }
      },
      error: (err) => {
        this.aiScanning.set(false);
        const message = apiErrorMessage(err, 'Analyse IA impossible — complétez les champs manuellement.');
        if (err?.status === 400 && /pdf|illisible|script|valide|vide/i.test(message)) {
          this.cancelAiDialog();
          this.error.set(message);
          return;
        }
        this.aiSuggested.set({ label: '', description: '' });
        this.aiScanNotes.set(message);
      }
    });
  }

  cancelAiDialog(): void {
    if (this.saving()) {
      return;
    }
    this.aiDialogOpen.set(false);
    this.aiScanning.set(false);
    this.pendingCreateFile = null;
    this.aiSuggested.set(null);
    this.aiScanNotes.set(null);
  }

  confirmAiDialog(payload: RegleJeuxAiDialogConfirm): void {
    if (!this.pendingCreateFile) {
      this.error.set('Fichier PDF manquant.');
      this.cancelAiDialog();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.success.set(null);
    this.masService
      .createRegleJeux(payload.label, this.pendingCreateFile, payload.description)
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.cancelAiDialog();
          this.success.set('Règle de jeux créée.');
          this.reload();
        },
        error: (err) => {
          this.saving.set(false);
          this.error.set(apiErrorMessage(err, 'Création impossible.'));
        }
      });
  }

  startEdit(item: RegleJeuxOption): void {
    this.linkingRegleId.set(null);
    this.editingId.set(item.id);
    this.editForm.patchValue({
      label: item.label || '',
      description: item.description || ''
    });
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  startLinkMas(item: RegleJeuxOption): void {
    this.editingId.set(null);
    this.linkingRegleId.set(item.id);
    this.loadingMasLinks.set(true);
    this.error.set(null);
    this.masService.getRegleJeux(item.id).subscribe({
      next: (detail) => {
        this.masLinkForm.patchValue({ masIds: detail.masIds ?? [] });
        this.loadingMasLinks.set(false);
      },
      error: (err) => {
        this.loadingMasLinks.set(false);
        this.masLinkForm.patchValue({ masIds: item.masIds ?? [] });
        this.error.set(apiErrorMessage(err, 'Impossible de charger les MAS rattachées.'));
      }
    });
  }

  cancelLinkMas(): void {
    this.linkingRegleId.set(null);
  }

  submitLinkMas(regleId: number): void {
    this.saving.set(true);
    this.error.set(null);
    this.success.set(null);
    const raw = this.masLinkForm.controls.masIds.value ?? [];
    const masIds = raw.map((id) => Number(id)).filter((id) => Number.isFinite(id));
    this.masService.linkRegleJeuxMas(regleId, masIds).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.linkingRegleId.set(null);
        this.success.set('MAS rattachées à la règle.');
        this.items.update((list) =>
          list.map((r) => (r.id === regleId ? { ...r, ...updated } : r))
        );
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Rattachement impossible.'));
      }
    });
  }

  masOptionLabel(mas: Mas): string {
    const marque = mas.marqueLabel || mas.marque;
    return marque ? `${mas.numero} — ${marque}` : mas.numero;
  }

  submitEdit(id: number): void {
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.success.set(null);
    const raw = this.editForm.getRawValue();
    this.masService.updateRegleJeux(id, raw.label, raw.description || null).subscribe({
      next: () => {
        this.saving.set(false);
        this.editingId.set(null);
        this.success.set('Règle de jeux mise à jour.');
        this.reload();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Mise à jour impossible.'));
      }
    });
  }

  pickReplaceFile(id: number): void {
    this.replaceFileTargetId = id;
    document.getElementById('regle-jeux-replace-file')?.click();
  }

  onReplaceFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    const id = this.replaceFileTargetId;
    this.replaceFileTargetId = null;
    if (!file || id == null) {
      return;
    }
    if (!isPdfFile(file)) {
      this.error.set('Le document doit être un fichier PDF.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.success.set(null);
    this.masService.replaceRegleJeuxDocument(id, file).subscribe({
      next: () => {
        this.saving.set(false);
        this.success.set('PDF remplacé.');
        this.reload();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Remplacement du PDF impossible.'));
      }
    });
  }

  deleteItem(item: RegleJeuxOption): void {
    if ((item.masCount ?? 0) > 0) {
      return;
    }
    if (!confirm(`Supprimer la règle « ${item.label} » ?`)) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.success.set(null);
    this.masService.deleteRegleJeux(item.id).subscribe({
      next: () => {
        this.saving.set(false);
        this.success.set('Règle de jeux supprimée.');
        this.reload();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Suppression impossible.'));
      }
    });
  }

  openPdf(item: RegleJeuxOption): void {
    this.error.set(null);
    this.masService.downloadRegleJeuxPdf(item.id).subscribe({
      next: (blob) => {
        if (!blob || blob.size === 0) {
          this.error.set('PDF vide ou introuvable — remplacez le document.');
          return;
        }
        if (blob.type && blob.type.includes('json')) {
          this.error.set('PDF introuvable dans le stockage — remplacez le document.');
          return;
        }
        const url = URL.createObjectURL(blob);
        const opened = window.open(url, '_blank', 'noopener');
        if (!opened) {
          const a = document.createElement('a');
          a.href = url;
          a.download = item.originalName || 'regle-jeux.pdf';
          a.click();
        }
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (err) => {
        this.error.set(apiErrorMessage(err, 'PDF introuvable dans le stockage — remplacez le document.'));
      }
    });
  }

  canDelete(item: RegleJeuxOption): boolean {
    return (item.masCount ?? 0) === 0;
  }

  private matchesSearch(item: RegleJeuxOption, q: string): boolean {
    const parts: string[] = [
      item.label,
      item.description ?? '',
      item.code ?? '',
      item.originalName ?? ''
    ];
    for (const m of item.masses ?? []) {
      parts.push(m.numero, m.marqueLabel ?? '');
    }
    return parts.join(' ').toLowerCase().includes(q);
  }
}
