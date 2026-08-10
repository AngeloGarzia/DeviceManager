import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { driver, type DriveStep, type Driver } from 'driver.js';
import { AuthService } from './auth.service';

const TOUR_KEY_PREFIX = 'dm_tour_done_';

interface TourStepDef {
  /** Route à charger avant de surligner (optionnel). */
  route?: string;
  /** Sélecteur CSS de la cible (absent = popover centré). */
  element?: string;
  title: string;
  description: string;
  /** Si true, étape réservée aux administrateurs. */
  adminOnly?: boolean;
  /** Préparation DOM (ouvrir une tuile, etc.). */
  before?: () => void | Promise<void>;
}

/**
 * Parcours guidé multi-pages (driver.js) — 1er login + relance manuelle.
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
          side: 'top',
          align: 'start'
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
    return [
      {
        title: 'Bienvenue dans DeviceManager',
        description:
          'Parcours complet : atelier, SFM et contacts, MAS, pièces, stock (export), commandes et assistant IA. Relançable depuis le pied de page.'
      },
      {
        element: '[data-tour="shell-brand"]',
        title: 'Bandeau',
        description:
          'Identité, groupe (ex. Circus) et ville de l’atelier sélectionné.'
      },
      {
        element: '[data-tour="shell-atelier"]',
        title: 'Casino → Atelier',
        description:
          'Toutes les données sont filtrées par atelier. Les admins changent d’atelier ici.'
      },

      // --- Atelier (admin) ---
      {
        adminOnly: true,
        route: '/setup',
        element: '[data-tour="setup-ateliers"]',
        title: 'Ajouter un atelier',
        description:
          'Dans Paramètres, ouvrez « Atelier par Casino ». Créez d’abord un casino si besoin, puis un atelier rattaché.',
        before: async () => {
          await this.expandToggle('[data-tour="setup-ateliers-toggle"]');
        }
      },
      {
        adminOnly: true,
        route: '/setup',
        element: '[data-tour="setup-manage-casinos"]',
        title: 'Gérer les casinos',
        description:
          'Créez ou renommez les casinos du groupe. Chaque atelier appartient à un casino.',
        before: async () => {
          await this.expandToggle('[data-tour="setup-ateliers-toggle"]');
        }
      },
      {
        adminOnly: true,
        route: '/setup',
        element: '[data-tour="setup-new-atelier"]',
        title: 'Nouvel atelier',
        description:
          'Ouvrez un casino, puis « Nouvel atelier » : nom, adresse (ville), contacts et responsables.',
        before: async () => {
          await this.expandToggle('[data-tour="setup-ateliers-toggle"]');
          await this.expandToggle('[data-tour="setup-casino-toggle"]');
        }
      },

      // --- SFM + contacts ---
      {
        route: '/sfm',
        element: '[data-tour="page-sfm"]',
        title: 'SFM',
        description:
          'Référentiel des sites / services fournisseurs liés aux pièces et aux commandes.'
      },
      {
        route: '/sfm',
        element: '[data-tour="btn-new-sfm"]',
        title: 'Ajouter un SFM',
        description: 'Cliquez ici pour créer un SFM : nom, marques couvertes, puis contacts.'
      },
      {
        route: '/sfm/new',
        element: '[data-tour="page-sfm-form"]',
        title: 'Formulaire SFM',
        description:
          'Renseignez le nom et les marques. Au moins un contact est requis pour les e-mails de commande.'
      },
      {
        route: '/sfm/new',
        element: '[data-tour="sfm-contacts"]',
        title: 'Ajouter un contact',
        description:
          'Ajoutez nom, téléphone, e-mail. Cochez « reçoit les e-mails de commande » pour les validations. « Technicien SFM » permet de réutiliser le contact sur plusieurs SFM.'
      },

      // --- MAS ---
      {
        route: '/mas',
        element: '[data-tour="page-mas"]',
        title: 'Machines à sous',
        description: 'Référentiel MAS de l’atelier : numéros et marques.'
      },
      {
        route: '/mas',
        element: '[data-tour="btn-new-mas"]',
        title: 'Ajouter une MAS',
        description: 'Créez une machine (numéro + marque) pour rattacher des pièces détachées.'
      },
      {
        route: '/mas/new',
        element: '[data-tour="page-mas-form"]',
        title: 'Formulaire MAS',
        description: 'Saisissez le numéro et la marque (ou créez une nouvelle marque si besoin).'
      },

      // --- Pièces ---
      {
        route: '/devices',
        element: '[data-tour="page-devices"]',
        title: 'Pièces détachées',
        description:
          'Inventaire de l’atelier. Les tuiles indiquent total, obsolètes et stock à zéro.'
      },
      {
        route: '/devices',
        element: '[data-tour="btn-new-device"]',
        title: 'Ajouter une pièce',
        description: 'Lance la création d’une fiche pièce (photos, SFM, MAS, stock…).'
      },
      {
        route: '/devices/new',
        element: '[data-tour="page-device-form"]',
        title: 'Formulaire pièce',
        description:
          'Nom, référence, usage, stock, photos, liens SFM/MAS. Vous pouvez créer un SFM ou une MAS depuis ce formulaire.'
      },

      // --- Stock + export ---
      {
        route: '/devices/stock',
        element: '[data-tour="page-stock"]',
        title: 'Stock',
        description:
          'Ajustez les quantités, regroupez par SFM ou marque, puis exportez.'
      },
      {
        route: '/devices/stock',
        element: '[data-tour="btn-stock-export"]',
        title: 'Exporter le stock',
        description: 'Export Excel (.xlsx) ou PDF de l’inventaire selon le regroupement choisi.'
      },

      // --- Commandes ---
      {
        element: '[data-tour="nav-commandes"]',
        title: 'Commandes',
        description: 'Nouvelle demande ou suivi des demandes (validation / réception côté admin).'
      },
      {
        route: '/order-request',
        element: '[data-tour="page-order-request"]',
        title: 'Nouvelle demande',
        description: 'Ajoutez des lignes (pièce × quantité) et envoyez la demande.'
      },
      {
        route: '/order-requests',
        element: '[data-tour="page-order-requests"]',
        title: 'Demandes en cours',
        description: this.auth.isAdmin()
          ? 'Validez (e-mails SFM), ajustez les quantités reçues, confirmez la réception → stock mis à jour.'
          : 'Suivez le statut (en attente, validée, reçue). Validation et réception : admin uniquement.'
      },

      // --- IA ---
      {
        element: '[data-tour="shell-ai"]',
        title: 'Assistant IA',
        description:
          'Icône en haut à droite. Si le module est activé dans Paramètres, il aide à rédiger demandes, pièces, MAS/SFM.'
      },
      {
        route: '/ai',
        element: '[data-tour="page-ai"]',
        title: 'Écran Assistant IA',
        description:
          'Posez une question métier (devis SFM, statuts de commande, rédaction…). Désactivé si non configuré.'
      },

      {
        adminOnly: true,
        element: '[data-tour="shell-admin"]',
        title: 'Administration',
        description: 'Comptes et Paramètres (mail, S3, IA, ateliers). Relancez aussi le tutoriel ici.'
      },
      {
        element: '[data-tour="footer-tour"]',
        title: 'Relancer le tutoriel',
        description:
          '« Tutoriel » en bas de page' +
          (this.auth.isAdmin() ? ' ou le bouton dans Paramètres.' : '.')
      }
    ];
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
