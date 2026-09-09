import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { driver, type DriveStep, type Driver } from 'driver.js';
import { AuthService } from './auth.service';

/** Nouvelle clé : force un passage du parcours v2 pour tous les comptes. */
const TOUR_KEY_PREFIX = 'dm_tour_v2_done_';

interface TourStepDef {
  /** Route à charger avant de surligner (optionnel). */
  route?: string;
  /** Sélecteur CSS de la cible (absent = popover centré). */
  element?: string;
  title: string;
  description: string;
  /** Position du popover driver.js. */
  side?: 'top' | 'right' | 'bottom' | 'left';
  align?: 'start' | 'center' | 'end';
  /** Si true, étape réservée aux administrateurs. */
  adminOnly?: boolean;
  /** Préparation DOM (ouvrir une tuile, un menu, etc.). */
  before?: () => void | Promise<void>;
}

/**
 * Parcours guidé multi-pages (driver.js) — 1er login + relance manuelle.
 * Chapitres : orientation → organisation → référentiels → pièces →
 * commandes → ops MAS / todo → IA.
 */
@Injectable({ providedIn: 'root' })
export class AppTourService {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private active: Driver | null = null;
  private starting = false;

  /** Indique si l'utilisateur courant a déjà terminé (ou passé) le tutoriel. */
  hasCompletedTour(): boolean {
    const user = this.auth.username();
    if (!user) {
      return true;
    }
    return localStorage.getItem(TOUR_KEY_PREFIX + user) === '1';
  }

  /** Marque le tutoriel comme terminé pour l'utilisateur courant. */
  markCompleted(): void {
    const user = this.auth.username();
    if (!user) {
      return;
    }
    localStorage.setItem(TOUR_KEY_PREFIX + user, '1');
  }

  /** Efface le flag pour permettre une relance. */
  resetTour(): void {
    const user = this.auth.username();
    if (!user) {
      return;
    }
    localStorage.removeItem(TOUR_KEY_PREFIX + user);
  }

  /**
   * Démarre le parcours.
   * @param force ignore le flag « déjà vu »
   */
  async startTour(force = false): Promise<void> {
    if (this.starting || this.active) {
      return;
    }
    if (!this.auth.getToken() || this.auth.mustChangePassword()) {
      return;
    }
    if (!force && this.hasCompletedTour()) {
      return;
    }

    this.starting = true;
    try {
      const defs = this.buildSteps().filter((s) => !s.adminOnly || this.auth.isAdmin());
      if (defs.length === 0) {
        return;
      }

      await this.prepareStep(defs[0]);

      const steps: DriveStep[] = defs.map((def) => ({
        element: def.element,
        popover: {
          title: def.title,
          description: def.description,
          side: def.side ?? 'top',
          align: def.align ?? 'start'
        }
      }));

      this.active = driver({
        showProgress: true,
        animate: true,
        allowClose: true,
        skipMissingElement: true,
        waitForElement: 3500,
        overlayOpacity: 0.55,
        stagePadding: 8,
        stageRadius: 10,
        nextBtnText: 'Suivant',
        prevBtnText: 'Précédent',
        doneBtnText: 'Terminer',
        progressText: '{{current}} / {{total}}',
        steps,
        onNextClick: async (_el, _step, { driver: d }) => {
          const idx = d.getActiveIndex() ?? 0;
          if (idx >= defs.length - 1) {
            d.destroy();
            return;
          }
          await this.prepareStep(defs[idx + 1]);
          d.moveNext();
        },
        onPrevClick: async (_el, _step, { driver: d }) => {
          const idx = d.getActiveIndex() ?? 0;
          const prev = defs[idx - 1];
          if (prev) {
            await this.prepareStep(prev);
          }
          d.movePrevious();
        },
        onCloseClick: (_el, _step, { driver: d }) => {
          d.destroy();
        },
        onDestroyStarted: (_el, _step, { driver: d }) => {
          if (!d.isActive()) {
            return;
          }
          d.destroy();
        },
        onDestroyed: () => {
          this.markCompleted();
          this.active = null;
          this.closeOpenMenus();
        }
      });

      this.active.drive();
    } finally {
      this.starting = false;
    }
  }

  /** Relance depuis Setup / footer : reset + départ depuis /devices. */
  async restartTour(): Promise<void> {
    if (this.active) {
      this.active.destroy();
      this.active = null;
    }
    this.resetTour();
    await this.router.navigateByUrl('/devices');
    await this.delay(300);
    await this.startTour(true);
  }

  private async prepareStep(def: TourStepDef): Promise<void> {
    this.closeOpenMenus();
    if (def.route) {
      const target = def.route.split('?')[0];
      if (!this.router.url.startsWith(target)) {
        await this.router.navigateByUrl(def.route);
      }
    }
    if (def.before) {
      await def.before();
    }
    await this.waitForDom(def.element);
  }

  private buildSteps(): TourStepDef[] {
    const isAdmin = this.auth.isAdmin();

    return [
      // ── Orientation ──────────────────────────────────────────────
      {
        title: 'Bienvenue dans DeviceManager',
        description:
          'Petit parcours métier : votre atelier, les référentiels (SFM / MAS), les pièces au quotidien, ' +
          'les commandes, le suivi terrain, puis l’assistant IA et sa mémoire synaptique. ' +
          'Vous pourrez le relancer à tout moment depuis le pied de page.'
      },
      {
        element: '[data-tour="shell-brand"]',
        title: 'Votre contexte',
        description:
          'Nom, rôle, groupe (ex. Circus) et ville de l’atelier actif. Tout ce que vous voyez ensuite est filtré par cet atelier.'
      },
      {
        element: '[data-tour="shell-atelier"]',
        title: 'Casino → Atelier',
        description: isAdmin
          ? 'Changez d’atelier ici. À chaque bascule, DeviceManager recalcule la mémoire synaptique et affiche un briefing de situation.'
          : 'Votre atelier de travail. Les données (pièces, stock, MAS, commandes…) restent toujours dans ce périmètre.'
      },
      {
        element: '[data-tour="nav-pieces"]',
        side: 'top',
        align: 'center',
        title: 'Navigation du quotidien',
        description:
          'La barre du bas : Pièces, Commandes, MAS, Todo et SFM. C’est le fil conducteur de votre journée terrain.'
      },

      // ── Organisation (admin) ─────────────────────────────────────
      {
        adminOnly: true,
        title: 'Organisation des ateliers',
        description:
          'Côté admin : casinos, ateliers, contacts et le flag « Utilisé / Non utilisé » pour archiver sans supprimer.'
      },
      {
        adminOnly: true,
        route: '/setup',
        element: '[data-tour="setup-ateliers"]',
        title: 'Atelier par casino',
        description:
          'Dans Paramètres, ouvrez cette tuile pour structurer le groupe : d’abord les casinos, puis les ateliers rattachés.',
        before: async () => {
          await this.expandToggle('[data-tour="setup-ateliers-toggle"]');
        }
      },
      {
        adminOnly: true,
        route: '/setup',
        element: '[data-tour="setup-manage-casinos"]',
        title: 'Gérer les casinos',
        description: 'Créez ou renommez les casinos. Chaque atelier appartient à un casino du groupe.',
        before: async () => {
          await this.expandToggle('[data-tour="setup-ateliers-toggle"]');
        }
      },
      {
        adminOnly: true,
        route: '/setup',
        element: '[data-tour="setup-new-atelier"]',
        title: 'Créer un atelier',
        description: 'Ouvrez un casino, puis « Nouvel atelier » : nom, adresse, contacts et responsables préférés.',
        before: async () => {
          await this.expandToggle('[data-tour="setup-ateliers-toggle"]');
          await this.expandToggle('[data-tour="setup-casino-toggle"]');
        }
      },
      {
        adminOnly: true,
        route: '/setup',
        element: '[data-tour="setup-atelier-utilise"]',
        title: 'Utilisé / Non utilisé',
        description:
          'Désactivez un atelier pour l’archiver : il reste visible ici, mais n’est plus proposé aux techniciens ni comme atelier préféré.',
        before: async () => {
          await this.expandToggle('[data-tour="setup-ateliers-toggle"]');
          await this.expandToggle('[data-tour="setup-casino-toggle"]');
          await this.openAtelierFormForTour();
        }
      },

      // ── Référentiels ─────────────────────────────────────────────
      {
        title: 'Référentiels SFM & MAS',
        description:
          'Avant les pièces : les fournisseurs (SFM + contacts e-mail) et les machines (MAS) auxquelles rattacher le stock.'
      },
      {
        element: '[data-tour="nav-sfm"]',
        side: 'top',
        align: 'end',
        title: 'SFM',
        description: 'Sites / services fournisseurs. Indispensables pour les e-mails de commande.'
      },
      {
        route: '/sfm',
        element: '[data-tour="page-sfm"]',
        title: 'Liste des SFM',
        description: 'Consultez les SFM de l’atelier et leurs marques couvertes.'
      },
      {
        route: '/sfm',
        element: '[data-tour="btn-new-sfm"]',
        title: 'Nouveau SFM',
        description: 'Créez un SFM : nom, marques, puis au moins un contact pour les validations.'
      },
      {
        route: '/sfm/new',
        element: '[data-tour="sfm-contacts"]',
        title: 'Contacts & e-mails',
        description:
          'Ajoutez nom, téléphone, e-mail. Cochez « reçoit les e-mails de commande ». « Technicien SFM » permet de réutiliser un contact sur plusieurs SFM.'
      },
      {
        route: '/mas',
        element: '[data-tour="page-mas"]',
        title: 'Machines à sous',
        description: 'Référentiel MAS de l’atelier : numéros, marques, dénominations.'
      },
      {
        route: '/mas',
        element: '[data-tour="btn-new-mas"]',
        title: 'Nouvelle MAS',
        description: 'Créez une machine pour rattacher pièces, interventions et suivi.'
      },
      {
        route: '/mas/new',
        element: '[data-tour="page-mas-form"]',
        title: 'Fiche MAS',
        description:
          'Numéro, marque, multi-dénominations si besoin. Une MAS bien renseignée alimente le suivi et la mémoire atelier.'
      },

      // ── Pièces ───────────────────────────────────────────────────
      {
        title: 'Pièces au quotidien',
        description:
          'Inventaire, création, utilisation sur machine, bons d’intervention et édition du stock — le cœur opérationnel.'
      },
      {
        element: '[data-tour="nav-pieces"]',
        side: 'top',
        align: 'start',
        title: 'Menu Pièces',
        description: 'Liste, création, utilisation, bons et stock : tout part de ce menu.',
        before: async () => {
          await this.openNavMenu('[data-tour="nav-pieces"]');
        }
      },
      {
        route: '/devices?open=pieces',
        element: '[data-tour="page-devices"]',
        title: 'Inventaire',
        description:
          'Tuile « Pièces détachées » : total, obsolètes, stock à zéro (raccourci vers une demande de commande).',
        before: async () => {
          await this.expandDevicesTile();
        }
      },
      {
        route: '/devices?open=pieces',
        element: '[data-tour="btn-new-device"]',
        title: 'Nouvelle pièce',
        description: 'Ouvre la fiche : photos, SFM, MAS, stock, usage…',
        before: async () => {
          await this.expandDevicesTile();
        }
      },
      {
        route: '/devices/new',
        element: '[data-tour="page-device-form"]',
        title: 'Fiche pièce',
        description:
          'Renseignez la pièce. Vous pouvez créer un SFM ou une MAS à la volée depuis ce formulaire.'
      },
      {
        element: '[data-tour="nav-utiliser-piece"]',
        side: 'top',
        title: 'Utiliser une pièce',
        description: 'Consommez du stock sur une MAS : bon d’intervention généré automatiquement.',
        before: async () => {
          await this.openNavMenu('[data-tour="nav-pieces"]');
        }
      },
      {
        route: '/devices/utiliser',
        element: '[data-tour="page-device-use"]',
        title: 'Consommation stock',
        description: 'Choisissez la pièce, la MAS et la quantité. Le stock se met à jour immédiatement.'
      },
      {
        route: '/devices/interventions',
        element: '[data-tour="page-interventions"]',
        title: 'Bons d’intervention',
        description: 'Historique des utilisations de pièces — traçabilité atelier.'
      },
      {
        route: '/devices/stock',
        element: '[data-tour="page-stock"]',
        title: 'Éditer le stock',
        description: 'Ajustez les quantités, regroupez par SFM ou marque, puis exportez.'
      },
      {
        route: '/devices/stock',
        element: '[data-tour="btn-stock-export"]',
        title: 'Export stock',
        description: 'Excel (.xlsx) ou PDF selon le regroupement choisi — idéal pour inventaire ou audit.'
      },

      // ── Commandes ────────────────────────────────────────────────
      {
        title: 'Commandes & suivi',
        description:
          'Demande → validation (e-mails SFM) → réception (stock) → timeline. Côté admin : devis PDF/image analysé par l’IA.'
      },
      {
        element: '[data-tour="nav-commandes"]',
        side: 'top',
        align: 'center',
        title: 'Menu Commandes',
        description: 'Nouvelle demande, liste (badge si en attente) et timeline d’activité.',
        before: async () => {
          await this.openNavMenu('[data-tour="nav-commandes"]');
        }
      },
      {
        route: '/order-request',
        element: '[data-tour="page-order-request"]',
        title: 'Nouvelle demande',
        description: 'Ajoutez des lignes (pièce × quantité) et envoyez. Les pièces en rupture sont pré-proposées.'
      },
      {
        route: '/order-requests',
        element: '[data-tour="page-order-requests"]',
        title: 'Liste des commandes',
        description: isAdmin
          ? 'Validez (e-mails contacts SFM), ajustez à la réception, confirmez → stock mis à jour.'
          : 'Suivez le statut (en attente, validée, reçue). Validation et réception : admin uniquement.'
      },
      {
        adminOnly: true,
        route: '/order-requests',
        element: '[data-tour="order-devis-ai"]',
        title: 'Devis & IA',
        description:
          'Sur une commande, associez un devis (PDF ou image). L’IA propose des mises à jour de désignations / références et un passage des prix — à valider avant application.'
      },
      {
        route: '/order-timeline',
        element: '[data-tour="page-order-timeline"]',
        title: 'Timeline',
        description:
          'Fil chronologique des événements commandes (et plus largement le suivi d’activité lié).'
      },

      // ── Ops MAS & Todo ───────────────────────────────────────────
      {
        title: 'Terrain MAS & tâches',
        description:
          'Suivi machine, arrêts maintenance, visites quadri, interventions techniques, FIT — et la todo pour ne rien perdre.'
      },
      {
        element: '[data-tour="nav-mas"]',
        side: 'top',
        align: 'center',
        title: 'Menu MAS',
        description:
          'Liste, suivi, arrêts, règles de jeux, visites, interventions techniques et fiches FIT. Le badge signale les visites à traiter.',
        before: async () => {
          await this.openNavMenu('[data-tour="nav-mas"]');
        }
      },
      {
        route: '/mas/suivi',
        element: '[data-tour="page-mas-suivi"]',
        title: 'Suivi MAS',
        description:
          'Timeline par machine : interventions, commandes, todos liées… Une vue synthétique de l’historique.'
      },
      {
        route: '/mas/arrets-maintenance',
        element: '[data-tour="page-arrets-maintenance"]',
        title: 'Arrêts maintenance',
        description:
          'Déclarez et suivez les arrêts. Une alerte clignote aussi dans le bandeau quand des reprises sont attendues.'
      },
      {
        route: '/mas/visites-quadri',
        element: '[data-tour="page-visites-quadri"]',
        title: 'Visites quadritrimestrielles',
        description: 'Planifiez et validez les passages périodiques. Le badge MAS rappelle ce qu’il reste à faire.'
      },
      {
        element: '[data-tour="nav-todo"]',
        side: 'top',
        align: 'end',
        title: 'Todo',
        description: 'Tâches à faire avec cycle de vie. Le badge indique les actives ; elles apparaissent aussi dans le suivi MAS.'
      },
      {
        route: '/todos',
        element: '[data-tour="page-todo"]',
        title: 'Liste Todo',
        description: 'Créez, assignez et clôturez vos tâches. Raccourci aussi depuis la tuile « À faire » sur l’accueil pièces.'
      },

      // ── IA ───────────────────────────────────────────────────────
      {
        title: 'Assistant IA & mémoire',
        description:
          'L’IA s’appuie sur la mémoire synaptique de l’atelier (événements pièces, stock, commandes, interventions…) pour répondre au contexte.'
      },
      {
        element: '[data-tour="shell-ai"]',
        side: 'bottom',
        align: 'end',
        title: 'Ouvrir l’assistant',
        description:
          'Icône en haut à droite (activable dans Paramètres). Utile pour rédiger, questionner le stock ou le statut des commandes.'
      },
      {
        route: '/ai',
        element: '[data-tour="ai-memory"]',
        title: 'Mémoire synaptique',
        description:
          'Snapshot de situation de l’atelier + faits récents. Actualisez ou recalculez ; un briefing s’ouvre aussi quand un admin change d’atelier.',
        before: async () => {
          await this.expandAiMemory();
        }
      },
      {
        route: '/ai',
        element: '[data-tour="page-ai"]',
        title: 'Chat métier',
        description:
          'Posez une question (« pièces en rupture ? », « todos ouvertes ? »…). L’assistant lit la mémoire avant de répondre.'
      },

      {
        adminOnly: true,
        element: '[data-tour="shell-admin"]',
        side: 'bottom',
        align: 'end',
        title: 'Administration',
        description:
          'Comptes utilisateurs et Paramètres (mail, stockage, IA, ateliers). Vous pouvez aussi relancer ce tutoriel depuis Setup.'
      },
      {
        element: '[data-tour="footer-tour"]',
        side: 'top',
        title: 'Relancer le tutoriel',
        description:
          '« Tutoriel » reste toujours disponible en bas de page' +
          (isAdmin ? ' — et le bouton dans Paramètres.' : '.')
      }
    ];
  }

  /** Ouvre le formulaire nouvel atelier pour exposer le toggle Utilisé. */
  private async openAtelierFormForTour(): Promise<void> {
    const utilise = document.querySelector('[data-tour="setup-atelier-utilise"]');
    if (utilise) {
      utilise.scrollIntoView({ block: 'center', behavior: 'instant' as ScrollBehavior });
      await this.delay(120);
      return;
    }
    const btn = document.querySelector('[data-tour="setup-new-atelier"]') as HTMLElement | null;
    btn?.click();
    await this.delay(280);
    const target = document.querySelector('[data-tour="setup-atelier-utilise"]');
    target?.scrollIntoView({ block: 'center', behavior: 'instant' as ScrollBehavior });
    await this.delay(120);
  }

  /** Déplie la tuile inventaire pièces. */
  private async expandDevicesTile(): Promise<void> {
    const btn = document.querySelector('[data-tour="page-devices"]') as HTMLElement | null;
    if (!btn) {
      return;
    }
    if (btn.getAttribute('aria-expanded') !== 'true') {
      btn.click();
      await this.delay(220);
    }
  }

  /** Assure l’ouverture du panneau mémoire IA. */
  private async expandAiMemory(): Promise<void> {
    const card = document.querySelector('[data-tour="ai-memory"]');
    if (!card) {
      return;
    }
    const overview = card.querySelector('.memory-overview, .memory-facts, .memory-actions');
    if (!overview) {
      const toggle = document.querySelector('[data-tour="ai-memory-toggle"]') as HTMLElement | null;
      toggle?.click();
      await this.delay(220);
    }
  }

  /** Ouvre un mat-menu via son bouton déclencheur. */
  private async openNavMenu(triggerSelector: string): Promise<void> {
    const trigger = document.querySelector(triggerSelector) as HTMLElement | null;
    if (!trigger) {
      return;
    }
    trigger.click();
    await this.delay(280);
  }

  private closeOpenMenus(): void {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
  }

  private async expandToggle(selector: string): Promise<void> {
    const btn = document.querySelector(selector) as HTMLElement | null;
    if (!btn) {
      return;
    }
    if (btn.getAttribute('aria-expanded') === 'false') {
      btn.click();
      await this.delay(220);
    }
  }

  private async waitForDom(selector?: string): Promise<void> {
    if (!selector) {
      await this.delay(280);
      return;
    }
    const deadline = Date.now() + 4000;
    while (Date.now() < deadline) {
      if (document.querySelector(selector)) {
        await this.delay(120);
        return;
      }
      await this.delay(80);
    }
  }

  private delay(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}
