import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { forkJoin } from 'rxjs';
import {
  AppSetting,
  AtelierRequest,
  AtelierResponsable,
  AtelierSummary,
  CasinoRequest,
  CasinoSummary,
  TypeReseauSocial
} from '../../models/models';
import { SetupService } from '../../services/setup.service';
import { AtelierService } from '../../services/atelier.service';
import { AuthService } from '../../services/auth.service';
import { AiService } from '../../services/ai.service';
import { AdminLogEntry, AdminLogService } from '../../services/admin-log.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';

/**
 * Page d'administration initiale et des paramètres applicatifs.
 * Permet la configuration mail, S3, IA, la gestion des ateliers,
 * la consultation des logs SLF4J en base, et l'accès réservé aux administrateurs.
 * Les tuiles sont repliées par défaut.
 */
@Component({
  selector: 'app-setup',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
    MatSelectModule,
    MatIconModule,
    MatCheckboxModule,
    ConfirmDialogComponent
  ],
  templateUrl: './setup.component.html',
  styleUrl: './setup.component.scss'
})
export class SetupComponent implements OnInit {
  private readonly setupService = inject(SetupService);
  private readonly atelierService = inject(AtelierService);
  private readonly adminLogService = inject(AdminLogService);
  readonly aiService = inject(AiService);
  readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  /** Tuiles ouvertes (vide = toutes fermées par défaut). */
  private readonly openTiles = signal<Set<string>>(new Set());

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly testingMail = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal(false);
  readonly mailTestMessage = signal<string | null>(null);
  readonly settings = signal<AppSetting[]>([]);
  form: FormGroup = this.fb.group({});

  readonly ateliers = signal<AtelierSummary[]>([]);
  readonly casinos = signal<CasinoSummary[]>([]);
  readonly groupeUsers = signal<AtelierResponsable[]>([]);
  readonly ateliersLoading = signal(false);
  readonly atelierSaving = signal(false);
  readonly atelierError = signal<string | null>(null);
  readonly atelierSuccess = signal<string | null>(null);
  readonly editingAtelierId = signal<number | null>(null);
  /** Modale création / édition d'atelier (paramètres inclus). */
  readonly atelierDialogOpen = signal(false);
  readonly confirmDeleteOpen = signal(false);
  readonly pendingDeleteNom = signal('');
  private pendingDelete: AtelierSummary | null = null;

  readonly casinoSaving = signal(false);
  readonly casinoError = signal<string | null>(null);
  readonly casinoSuccess = signal<string | null>(null);
  readonly editingCasinoId = signal<number | null>(null);
  /** Modale de gestion des casinos (CRUD). */
  readonly casinoDialogOpen = signal(false);
  readonly confirmDeleteCasinoOpen = signal(false);
  readonly pendingDeleteCasinoNom = signal('');
  private pendingDeleteCasino: CasinoSummary | null = null;

  readonly casinoForm = this.fb.group({
    nom: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(120)])
  });

  /** Ateliers groupés par casino pour l'affichage hiérarchique. */
  readonly ateliersByCasino = computed(() => {
    const casinos = this.casinos();
    const ateliers = this.ateliers();
    const byId = new Map<number, AtelierSummary[]>();
    for (const a of ateliers) {
      const list = byId.get(a.casinoId) ?? [];
      list.push(a);
      byId.set(a.casinoId, list);
    }
    const groups = casinos.map((c) => ({
      casino: c,
      ateliers: (byId.get(c.id) ?? []).sort((a, b) => a.nom.localeCompare(b.nom, 'fr'))
    }));
    // Ateliers orphelins (casino absent de la liste) — improbable mais sûr
    for (const [casinoId, list] of byId) {
      if (!casinos.some((c) => c.id === casinoId)) {
        groups.push({
          casino: {
            id: casinoId,
            nom: list[0]?.casinoNom || `Casino #${casinoId}`,
            groupeId: list[0]?.groupeId || 0,
            groupeNom: list[0]?.groupeNom || '',
            atelierCount: list.length
          },
          ateliers: list.sort((a, b) => a.nom.localeCompare(b.nom, 'fr'))
        });
      }
    }
    return groups;
  });

  readonly logsLoading = signal(false);
  readonly logsError = signal<string | null>(null);
  readonly logItems = signal<AdminLogEntry[]>([]);
  readonly logTotal = signal(0);
  readonly logRetention = signal(0);
  readonly confirmClearLogs = signal(false);
  logLevel = 'INFO';
  logLogger = '';
  logQuery = '';
  private logsLoadedOnce = false;

  readonly reseauTypes: { value: TypeReseauSocial; label: string }[] = [
    { value: 'SITE_WEB', label: 'Site web' },
    { value: 'LINKEDIN', label: 'LinkedIn' },
    { value: 'FACEBOOK', label: 'Facebook' },
    { value: 'INSTAGRAM', label: 'Instagram' },
    { value: 'X', label: 'X' },
    { value: 'YOUTUBE', label: 'YouTube' },
    { value: 'AUTRE', label: 'Autre' }
  ];

  readonly atelierForm = this.fb.group({
    nom: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(160)]),
    casinoId: this.fb.control<number | null>(null, Validators.required),
    ligne1: this.fb.nonNullable.control(''),
    ligne2: this.fb.nonNullable.control(''),
    codePostal: this.fb.nonNullable.control(''),
    ville: this.fb.nonNullable.control(''),
    pays: this.fb.nonNullable.control('France'),
    emails: this.fb.array([this.newEmailGroup()]),
    telephones: this.fb.array([this.newTelephoneGroup()]),
    reseauxSociaux: this.fb.array([] as FormGroup[]),
    responsableIds: this.fb.nonNullable.control<number[]>([]),
    utilisateurPrefereIds: this.fb.nonNullable.control<number[]>([])
  });

  readonly aiProviders = [
    {
      id: 'gemini',
      label: 'Google Gemini',
      models: [
        { value: 'gemini-2.0-flash', label: 'Gemini 2.0 Flash (vision)' },
        { value: 'gemini-2.0-flash-lite', label: 'Gemini 2.0 Flash Lite (vision)' },
        { value: 'gemini-2.5-flash', label: 'Gemini 2.5 Flash (vision)' },
        { value: 'gemini-2.5-pro', label: 'Gemini 2.5 Pro (vision)' },
        { value: 'gemini-1.5-flash', label: 'Gemini 1.5 Flash (vision)' },
        { value: 'gemini-1.5-pro', label: 'Gemini 1.5 Pro (vision)' }
      ]
    },
    {
      id: 'openai',
      label: 'OpenAI',
      models: [
        { value: 'gpt-4o-mini', label: 'gpt-4o-mini — économique (vision)' },
        { value: 'gpt-4o', label: 'gpt-4o — polyvalent (vision)' },
        { value: 'gpt-4.1-mini', label: 'gpt-4.1-mini' },
        { value: 'gpt-4.1', label: 'gpt-4.1' },
        { value: 'gpt-4.1-nano', label: 'gpt-4.1-nano' },
        { value: 'o4-mini', label: 'o4-mini — raisonnement' },
        { value: 'o3-mini', label: 'o3-mini — raisonnement' },
        { value: 'gpt-4-turbo', label: 'gpt-4-turbo' },
        { value: 'gpt-3.5-turbo', label: 'gpt-3.5-turbo' },
        { value: 'chatgpt-4o-latest', label: 'chatgpt-4o-latest' }
      ]
    },
    {
      id: 'groq',
      label: 'Groq',
      models: [
        { value: 'llama-3.3-70b-versatile', label: 'Llama 3.3 70B Versatile' },
        { value: 'llama-3.1-8b-instant', label: 'Llama 3.1 8B Instant' },
        { value: 'openai/gpt-oss-120b', label: 'GPT-OSS 120B' },
        { value: 'openai/gpt-oss-20b', label: 'GPT-OSS 20B' },
        { value: 'qwen/qwen3.6-27b', label: 'Qwen3.6 27B' },
        { value: 'groq/compound', label: 'Groq Compound' }
      ]
    },
    {
      id: 'mistral',
      label: 'Mistral AI',
      models: [
        { value: 'mistral-small-latest', label: 'Mistral Small' },
        { value: 'mistral-medium-latest', label: 'Mistral Medium' },
        { value: 'mistral-large-latest', label: 'Mistral Large' },
        { value: 'open-mistral-nemo', label: 'Mistral Nemo' },
        { value: 'codestral-latest', label: 'Codestral' },
        { value: 'pixtral-12b-2409', label: 'Pixtral 12B (vision)' },
        { value: 'pixtral-large-latest', label: 'Pixtral Large (vision)' }
      ]
    },
    {
      id: 'openrouter',
      label: 'OpenRouter (Claude, Gemini, Llama…)',
      models: [
        { value: 'openai/gpt-4o-mini', label: 'OpenAI GPT-4o mini (vision)' },
        { value: 'openai/gpt-4o', label: 'OpenAI GPT-4o (vision)' },
        { value: 'anthropic/claude-3.5-sonnet', label: 'Anthropic Claude 3.5 Sonnet' },
        { value: 'anthropic/claude-sonnet-4', label: 'Anthropic Claude Sonnet 4' },
        { value: 'google/gemini-2.0-flash-001', label: 'Google Gemini 2.0 Flash (vision)' },
        { value: 'google/gemini-2.5-pro-preview', label: 'Google Gemini 2.5 Pro' },
        { value: 'meta-llama/llama-3.3-70b-instruct', label: 'Meta Llama 3.3 70B' },
        { value: 'mistralai/mistral-large', label: 'Mistral Large' },
        { value: 'deepseek/deepseek-chat', label: 'DeepSeek Chat' },
        { value: 'qwen/qwen-2.5-72b-instruct', label: 'Qwen 2.5 72B' }
      ]
    },
    {
      id: 'deepseek',
      label: 'DeepSeek',
      models: [
        { value: 'deepseek-chat', label: 'DeepSeek Chat' },
        { value: 'deepseek-reasoner', label: 'DeepSeek Reasoner' }
      ]
    },
    {
      id: 'together',
      label: 'Together AI',
      models: [
        { value: 'meta-llama/Meta-Llama-3.1-70B-Instruct-Turbo', label: 'Llama 3.1 70B Turbo' },
        { value: 'meta-llama/Meta-Llama-3.1-8B-Instruct-Turbo', label: 'Llama 3.1 8B Turbo' },
        { value: 'mistralai/Mixtral-8x7B-Instruct-v0.1', label: 'Mixtral 8x7B' },
        { value: 'Qwen/Qwen2.5-72B-Instruct-Turbo', label: 'Qwen2.5 72B Turbo' },
        { value: 'meta-llama/Llama-Vision-Free', label: 'Llama Vision Free' }
      ]
    },
    {
      id: 'fireworks',
      label: 'Fireworks AI',
      models: [
        { value: 'accounts/fireworks/models/llama-v3p3-70b-instruct', label: 'Llama 3.3 70B' },
        { value: 'accounts/fireworks/models/llama-v3p1-8b-instruct', label: 'Llama 3.1 8B' },
        { value: 'accounts/fireworks/models/mixtral-8x22b-instruct', label: 'Mixtral 8x22B' },
        { value: 'accounts/fireworks/models/qwen2p5-72b-instruct', label: 'Qwen2.5 72B' }
      ]
    }
  ] as const;

  readonly selectedAiProvider = signal<string>('openai');

  readonly aiModelsForProvider = computed(() => {
    const selected = this.selectedAiProvider();
    const provider =
      this.aiProviders.find((p) => p.id === selected) ??
      this.aiProviders.find((p) => p.id === 'openai') ??
      this.aiProviders.find(() => true);
    return provider ? [...provider.models] : [];
  });

  readonly categories = computed(() => {
    const map = new Map<string, AppSetting[]>();
    for (const item of this.settings()) {
      if (item.key === 'AI_API_KEY') {
        continue;
      }
      const list = map.get(item.category) ?? [];
      list.push(item);
      map.set(item.category, list);
    }
    return [...map.entries()];
  });

  providersKeyStatusReady = computed(() => Object.keys(this.aiService.providerKeyStatus()).length > 0);

  readonly atelierFormTitle = computed(() =>
    this.editingAtelierId() == null ? 'Nouvel atelier' : 'Modifier l’atelier'
  );

  get emails(): FormArray {
    return this.atelierForm.get('emails') as FormArray;
  }

  get telephones(): FormArray {
    return this.atelierForm.get('telephones') as FormArray;
  }

  get reseauxSociaux(): FormArray {
    return this.atelierForm.get('reseauxSociaux') as FormArray;
  }

  /** Charge les paramètres applicatifs et initialise les ateliers. */
  ngOnInit(): void {
    this.aiService.refreshStatus();
    this.load();
    this.loadAteliers();
  }

  isTileOpen(id: string): boolean {
    return this.openTiles().has(id);
  }

  toggleTile(id: string): void {
    this.openTiles.update((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
        if (id === 'logs' && !this.logsLoadedOnce) {
          this.reloadLogs();
        }
      }
      return next;
    });
  }

  reloadLogs(): void {
    this.logsLoading.set(true);
    this.logsError.set(null);
    this.logsLoadedOnce = true;
    this.adminLogService
      .list({
        level: this.logLevel || undefined,
        logger: this.logLogger.trim() || undefined,
        q: this.logQuery.trim() || undefined,
        limit: 200
      })
      .subscribe({
        next: (res) => {
          this.logItems.set(res.items);
          this.logTotal.set(res.totalCount);
          this.logRetention.set(res.retentionMax);
          this.logsLoading.set(false);
        },
        error: (err) => {
          this.logsError.set(err?.error?.message || 'Impossible de charger les logs.');
          this.logsLoading.set(false);
        }
      });
  }

  askClearLogs(): void {
    this.confirmClearLogs.set(true);
  }

  doClearLogs(): void {
    this.confirmClearLogs.set(false);
    this.adminLogService.clear().subscribe({
      next: () => this.reloadLogs(),
      error: (err) => this.logsError.set(err?.error?.message || 'Échec du vidage des logs.')
    });
  }

  logLevelClass(level: string): string {
    switch ((level || '').toUpperCase()) {
      case 'ERROR':
        return 'lvl-error';
      case 'WARN':
      case 'WARNING':
        return 'lvl-warn';
      case 'DEBUG':
      case 'TRACE':
        return 'lvl-debug';
      default:
        return 'lvl-info';
    }
  }

  /** Charge la liste des paramètres depuis l'API. */
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
        const provider = (group['AI_PROVIDER']?.value || 'openai').toString();
        this.selectedAiProvider.set(provider);
        this.form.get('AI_PROVIDER')?.valueChanges.subscribe((value) => {
          const next = (value || 'openai').toString();
          this.selectedAiProvider.set(next);
          const models = [
            ...(this.aiProviders.find((p) => p.id === next)?.models ?? [])
          ];
          const currentModel = this.form.get('AI_MODEL')?.value;
          const firstModel = models.at(0);
          if (firstModel && !models.some((m) => m.value === currentModel)) {
            this.form.get('AI_MODEL')?.setValue(firstModel.value);
          }
        });
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message || 'Chargement du setup impossible.');
      }
    });
  }

  /** Charge ateliers, casinos et utilisateurs responsables. */
  loadAteliers(): void {
    this.ateliersLoading.set(true);
    this.atelierError.set(null);
    this.casinoError.set(null);
    forkJoin({
      ateliers: this.atelierService.list(),
      casinos: this.atelierService.listCasinos(),
      users: this.atelierService.listUsers()
    }).subscribe({
      next: ({ ateliers, casinos, users }) => {
        this.ateliers.set(ateliers);
        this.casinos.set(casinos);
        this.groupeUsers.set(users);
        this.ateliersLoading.set(false);
        if (casinos.length > 0 && this.atelierForm.controls.casinoId.value == null) {
          const firstCasino = casinos.at(0);
          if (firstCasino) {
            this.atelierForm.patchValue({ casinoId: firstCasino.id });
          }
        }
      },
      error: (err) => {
        this.ateliersLoading.set(false);
        this.atelierError.set(err?.error?.message || 'Chargement casino / atelier impossible.');
      }
    });
  }

  casinoFormTitle(): string {
    return this.editingCasinoId() == null ? 'Nouveau casino' : 'Modifier le casino';
  }

  /** Ouvre la modale de gestion des casinos. */
  openCasinoDialog(): void {
    this.casinoError.set(null);
    this.casinoSuccess.set(null);
    this.cancelCasinoEdit();
    this.casinoDialogOpen.set(true);
  }

  /** Ferme la modale casinos. */
  closeCasinoDialog(): void {
    if (this.casinoSaving()) {
      return;
    }
    this.casinoDialogOpen.set(false);
    this.cancelCasinoEdit();
    this.casinoError.set(null);
  }

  startCreateCasino(): void {
    this.editingCasinoId.set(null);
    this.casinoForm.reset({ nom: '' });
    this.casinoError.set(null);
    this.casinoSuccess.set(null);
  }

  startEditCasino(casino: CasinoSummary): void {
    this.editingCasinoId.set(casino.id);
    this.casinoForm.reset({ nom: casino.nom });
    this.casinoError.set(null);
    this.casinoSuccess.set(null);
  }

  cancelCasinoEdit(): void {
    this.editingCasinoId.set(null);
    this.casinoForm.reset({ nom: '' });
  }

  saveCasino(): void {
    if (this.casinoForm.invalid) {
      this.casinoForm.markAllAsTouched();
      return;
    }
    const payload: CasinoRequest = { nom: this.casinoForm.controls.nom.value.trim() };
    this.casinoSaving.set(true);
    this.casinoError.set(null);
    this.casinoSuccess.set(null);
    const id = this.editingCasinoId();
    const req$ =
      id == null
        ? this.atelierService.createCasino(payload)
        : this.atelierService.updateCasino(id, payload);
    req$.subscribe({
      next: () => {
        this.casinoSaving.set(false);
        this.casinoSuccess.set(id == null ? 'Casino créé.' : 'Casino mis à jour.');
        this.cancelCasinoEdit();
        this.loadAteliers();
      },
      error: (err) => {
        this.casinoSaving.set(false);
        this.casinoError.set(err?.error?.message || 'Enregistrement du casino impossible.');
      }
    });
  }

  askDeleteCasino(casino: CasinoSummary): void {
    this.pendingDeleteCasino = casino;
    this.pendingDeleteCasinoNom.set(casino.nom);
    this.confirmDeleteCasinoOpen.set(true);
  }

  cancelDeleteCasino(): void {
    this.confirmDeleteCasinoOpen.set(false);
    this.pendingDeleteCasino = null;
    this.pendingDeleteCasinoNom.set('');
  }

  confirmDeleteCasino(): void {
    const casino = this.pendingDeleteCasino;
    this.cancelDeleteCasino();
    if (!casino) {
      return;
    }
    this.casinoSaving.set(true);
    this.casinoError.set(null);
    this.atelierService.deleteCasino(casino.id).subscribe({
      next: () => {
        this.casinoSaving.set(false);
        this.casinoSuccess.set('Casino supprimé.');
        if (this.editingCasinoId() === casino.id) {
          this.cancelCasinoEdit();
        }
        this.loadAteliers();
      },
      error: (err) => {
        this.casinoSaving.set(false);
        this.casinoError.set(err?.error?.message || 'Suppression du casino impossible.');
      }
    });
  }

  /** Retourne le contrôle de formulaire associé à une clé de paramètre. */
  control(key: string): FormControl<string> {
    return this.form.get(key) as FormControl<string>;
  }

  /** Indique si le paramètre est un booléen (toggle). */
  isBooleanSetting(key: string): boolean {
    return key === 'MAIL_ENABLED' || key === 'S3_ENABLED' || key === 'AI_ENABLED';
  }

  /** Indique si le paramètre correspond au choix du fournisseur IA. */
  isAiProviderSetting(key: string): boolean {
    return key === 'AI_PROVIDER';
  }

  /** Indique si le paramètre correspond au choix du modèle IA. */
  isAiModelSetting(key: string): boolean {
    return key === 'AI_MODEL';
  }

  /** Vérifie si une clé API est configurée pour le fournisseur IA donné. */
  hasAiProviderKey(providerId: string): boolean {
    if (!this.aiService.statusLoaded()) {
      return true;
    }
    return this.aiService.hasProviderKey(providerId);
  }

  /** Libellé d'option fournisseur IA, avec mention si la clé est absente. */
  aiProviderOptionLabel(provider: { id: string; label: string }): string {
    if (!this.aiService.statusLoaded()) {
      return provider.label;
    }
    return this.hasAiProviderKey(provider.id)
      ? provider.label
      : `${provider.label} (clé absente)`;
  }

  /** Indique si le modèle IA sélectionné n'est pas dans la liste prédéfinie. */
  isCustomAiModel(key: string): boolean {
    const value = this.control(key)?.value;
    return !!value && !this.aiModelsForProvider().some((m) => m.value === value);
  }

  /** Interprète la valeur textuelle d'un paramètre booléen. */
  booleanValue(key: string): boolean {
    const value = this.control(key)?.value ?? '';
    return value === 'true' || value === '1' || value.toLowerCase() === 'yes';
  }

  /** Met à jour un paramètre booléen sous forme de chaîne « true » / « false ». */
  toggleBoolean(key: string, checked: boolean): void {
    this.control(key)?.setValue(checked ? 'true' : 'false');
  }

  /** Ajoute une ligne e-mail au formulaire atelier. */
  addEmail(): void {
    this.emails.push(this.newEmailGroup());
  }

  /** Supprime ou réinitialise une ligne e-mail du formulaire atelier. */
  removeEmail(index: number): void {
    if (this.emails.length <= 1) {
      this.emails.at(index).reset({ valeur: '', principal: true });
      return;
    }
    this.emails.removeAt(index);
  }

  /** Ajoute une ligne téléphone au formulaire atelier. */
  addTelephone(): void {
    this.telephones.push(this.newTelephoneGroup());
  }

  /** Supprime ou réinitialise une ligne téléphone du formulaire atelier. */
  removeTelephone(index: number): void {
    if (this.telephones.length <= 1) {
      this.telephones.at(index).reset({ valeur: '', label: '', principal: true });
      return;
    }
    this.telephones.removeAt(index);
  }

  /** Ajoute un réseau social au formulaire atelier. */
  addReseau(): void {
    this.reseauxSociaux.push(this.newReseauGroup());
  }

  /** Supprime un réseau social du formulaire atelier. */
  removeReseau(index: number): void {
    this.reseauxSociaux.removeAt(index);
  }

  /** Libellé affiché pour un utilisateur (responsable / préféré) dans les listes. */
  responsableLabel(user: AtelierResponsable): string {
    const name = `${user.prenom || ''} ${user.nom || ''}`.trim();
    return name ? `${name} (${user.username})` : user.username;
  }

  /** Libellé court pour l'affichage dans la liste des ateliers. */
  userShortLabel(user: AtelierResponsable): string {
    const name = `${user.prenom || ''} ${user.nom || ''}`.trim();
    return name || user.username;
  }

  /** Liste de noms pour l'affichage compact (responsables / préférés). */
  usersLabel(users: AtelierResponsable[] | undefined | null): string {
    if (!users?.length) {
      return '—';
    }
    return users.map((u) => this.userShortLabel(u)).join(', ');
  }

  /** Identifiant de tuile pour un casino dans la section Ateliers. */
  casinoAtelierTileId(casinoId: number): string {
    return `atelier-casino:${casinoId}`;
  }

  /** Ouvre la modale de création d'atelier (casino prérempli si fourni). */
  startCreateAtelier(casinoId?: number): void {
    this.editingAtelierId.set(null);
    this.atelierSuccess.set(null);
    this.atelierError.set(null);
    this.resetAtelierForm(casinoId);
    this.atelierDialogOpen.set(true);
  }

  /** Ouvre la modale d'édition d'un atelier existant. */
  startEditAtelier(item: AtelierSummary): void {
    this.editingAtelierId.set(item.id);
    this.atelierSuccess.set(null);
    this.atelierError.set(null);
    const coord = item.coordonnees;
    const adresse = coord?.adresse;
    this.emails.clear();
    const emails = coord?.emails?.length ? coord.emails : [{ valeur: '', principal: true }];
    for (const email of emails) {
      this.emails.push(this.newEmailGroup(email.valeur || '', !!email.principal));
    }
    this.telephones.clear();
    const tels = coord?.telephones?.length ? coord.telephones : [{ valeur: '', label: '', principal: true }];
    for (const tel of tels) {
      this.telephones.push(this.newTelephoneGroup(tel.valeur || '', tel.label || '', !!tel.principal));
    }
    this.reseauxSociaux.clear();
    for (const reseau of coord?.reseauxSociaux ?? []) {
      this.reseauxSociaux.push(this.newReseauGroup(reseau.type || 'AUTRE', reseau.url || ''));
    }
    this.atelierForm.patchValue({
      nom: item.nom,
      casinoId: item.casinoId,
      ligne1: adresse?.ligne1 || '',
      ligne2: adresse?.ligne2 || '',
      codePostal: adresse?.codePostal || '',
      ville: adresse?.ville || '',
      pays: adresse?.pays || 'France',
      responsableIds: (item.responsables ?? []).map((r) => r.id),
      utilisateurPrefereIds: (item.utilisateursPreferes ?? []).map((u) => u.id)
    });
    this.atelierDialogOpen.set(true);
  }

  /** Ferme la modale atelier et réinitialise le formulaire. */
  closeAtelierDialog(): void {
    if (this.atelierSaving()) {
      return;
    }
    this.atelierDialogOpen.set(false);
    this.editingAtelierId.set(null);
    this.resetAtelierForm();
  }

  /** Enregistre un atelier (création ou mise à jour). */
  saveAtelier(): void {
    if (this.atelierForm.invalid) {
      this.atelierForm.markAllAsTouched();
      return;
    }
    const raw = this.atelierForm.getRawValue();
    const payload: AtelierRequest = {
      nom: String(raw.nom || '').trim(),
      casinoId: Number(raw.casinoId),
      adresse: {
        ligne1: String(raw.ligne1 || '').trim() || undefined,
        ligne2: String(raw.ligne2 || '').trim() || undefined,
        codePostal: String(raw.codePostal || '').trim() || undefined,
        ville: String(raw.ville || '').trim() || undefined,
        pays: String(raw.pays || '').trim() || undefined
      },
      emails: (raw.emails as { valeur: string; principal: boolean }[])
        .filter((e) => (e.valeur || '').trim())
        .map((e) => ({ valeur: e.valeur.trim(), principal: !!e.principal })),
      telephones: (raw.telephones as { valeur: string; label: string; principal: boolean }[])
        .filter((t) => (t.valeur || '').trim())
        .map((t) => ({
          valeur: t.valeur.trim(),
          label: (t.label || '').trim() || undefined,
          principal: !!t.principal
        })),
      reseauxSociaux: (raw.reseauxSociaux as { type: TypeReseauSocial; url: string }[])
        .filter((r) => (r.url || '').trim())
        .map((r) => ({ type: r.type || 'AUTRE', url: r.url.trim() })),
      responsableIds: raw.responsableIds || [],
      utilisateurPrefereIds: raw.utilisateurPrefereIds || []
    };
    this.atelierSaving.set(true);
    this.atelierError.set(null);
    this.atelierSuccess.set(null);
    const editId = this.editingAtelierId();
    const req$ =
      editId == null
        ? this.atelierService.create(payload)
        : this.atelierService.update(editId, payload);
    req$.subscribe({
      next: () => {
        this.atelierSaving.set(false);
        this.atelierSuccess.set(editId == null ? 'Atelier créé.' : 'Atelier mis à jour.');
        this.atelierDialogOpen.set(false);
        this.editingAtelierId.set(null);
        this.resetAtelierForm();
        this.loadAteliers();
        this.auth.refreshAteliers();
      },
      error: (err) => {
        this.atelierSaving.set(false);
        this.atelierError.set(err?.error?.message || 'Enregistrement de l’atelier impossible.');
      }
    });
  }

  /** Ouvre la boîte de dialogue de confirmation de suppression d'atelier. */
  askDeleteAtelier(item: AtelierSummary): void {
    this.pendingDelete = item;
    this.pendingDeleteNom.set(item.nom);
    this.confirmDeleteOpen.set(true);
  }

  /** Supprime l'atelier en attente après confirmation. */
  confirmDeleteAtelier(): void {
    const item = this.pendingDelete;
    this.confirmDeleteOpen.set(false);
    this.pendingDelete = null;
    this.pendingDeleteNom.set('');
    if (!item) {
      return;
    }
    this.atelierSaving.set(true);
    this.atelierError.set(null);
    this.atelierService.delete(item.id).subscribe({
      next: () => {
        this.atelierSaving.set(false);
        this.atelierSuccess.set('Atelier supprimé.');
        if (this.editingAtelierId() === item.id) {
          this.closeAtelierDialog();
        }
        this.loadAteliers();
        this.auth.refreshAteliers();
      },
      error: (err) => {
        this.atelierSaving.set(false);
        this.atelierError.set(err?.error?.message || 'Suppression impossible.');
      }
    });
  }

  /** Annule la suppression d'atelier en cours. */
  cancelDeleteAtelier(): void {
    this.confirmDeleteOpen.set(false);
    this.pendingDelete = null;
    this.pendingDeleteNom.set('');
  }

  /** Enregistre l'ensemble des paramètres applicatifs. */
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
        this.aiService.refreshStatus();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(err?.error?.message || 'Enregistrement impossible.');
      }
    });
  }

  /** Sauvegarde les paramètres puis envoie un e-mail de test. */
  testMail(): void {
    this.testingMail.set(true);
    this.error.set(null);
    this.mailTestMessage.set(null);
    const values = this.form.getRawValue() as Record<string, string>;
    this.setupService.update(values).subscribe({
      next: (data) => {
        this.settings.set(data);
        for (const item of data) {
          this.control(item.key)?.setValue(item.value ?? '', { emitEvent: false });
        }
        this.aiService.refreshStatus();
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

  private resetAtelierForm(preferredCasinoId?: number): void {
    this.emails.clear();
    this.emails.push(this.newEmailGroup('', true));
    this.telephones.clear();
    this.telephones.push(this.newTelephoneGroup('', '', true));
    this.reseauxSociaux.clear();
    const casinoId =
      preferredCasinoId ??
      this.casinos()[0]?.id ??
      null;
    this.atelierForm.reset({
      nom: '',
      casinoId,
      ligne1: '',
      ligne2: '',
      codePostal: '',
      ville: '',
      pays: 'France',
      responsableIds: [],
      utilisateurPrefereIds: []
    });
  }

  private newEmailGroup(valeur = '', principal = false): FormGroup {
    return this.fb.group({
      valeur: this.fb.nonNullable.control(valeur),
      principal: this.fb.nonNullable.control(principal)
    });
  }

  private newTelephoneGroup(valeur = '', label = '', principal = false): FormGroup {
    return this.fb.group({
      valeur: this.fb.nonNullable.control(valeur),
      label: this.fb.nonNullable.control(label),
      principal: this.fb.nonNullable.control(principal)
    });
  }

  private newReseauGroup(type: TypeReseauSocial = 'SITE_WEB', url = ''): FormGroup {
    return this.fb.group({
      type: this.fb.nonNullable.control<TypeReseauSocial>(type),
      url: this.fb.nonNullable.control(url)
    });
  }
}
