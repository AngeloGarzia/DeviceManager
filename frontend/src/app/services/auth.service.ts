import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AtelierSummary, AuthResponse, LoginRequest } from '../models/models';

const TOKEN_KEY = 'dm_token';
const USER_KEY = 'dm_user';
const NOM_KEY = 'dm_nom';
const PRENOM_KEY = 'dm_prenom';
const ROLE_KEY = 'dm_role';
const ATELIER_KEY = 'dm_atelier_id';
const ATELIERS_KEY = 'dm_ateliers';
const GROUPE_KEY = 'dm_groupe_nom';
const REMEMBER_USER_KEY = 'dm_remember_user';
const REMEMBER_PASS_KEY = 'dm_remember_pass';

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly username = signal<string | null>(this.readUser());
  readonly nom = signal<string | null>(localStorage.getItem(NOM_KEY));
  readonly prenom = signal<string | null>(localStorage.getItem(PRENOM_KEY));
  readonly role = signal<string | null>(this.readRole());
  readonly atelierId = signal<number | null>(this.readAtelierId());
  readonly ateliers = signal<AtelierSummary[]>(this.readAteliers());
  readonly groupeNom = signal<string | null>(localStorage.getItem(GROUPE_KEY));
  /** Incrémenté à chaque changement d'atelier → remount du contenu (rechargement données). */
  readonly atelierRevision = signal(0);

  readonly isAdmin = computed(() => this.role() === 'ADMIN');
  readonly isTechnicien = computed(() => this.role() === 'TECHNICIEN' || this.role() === 'TECH');

  readonly displayName = computed(() => {
    const full = `${this.prenom() || ''} ${this.nom() || ''}`.trim();
    return full || this.username() || '';
  });

  readonly currentAtelier = computed(() => {
    const id = this.atelierId();
    return this.ateliers().find((a) => a.id === id) ?? null;
  });

  /** Ateliers regroupés par casino pour le sélecteur (structure casino → atelier). */
  readonly ateliersByCasino = computed(() => {
    const groups = new Map<string, AtelierSummary[]>();
    for (const atelier of this.ateliers()) {
      const key = atelier.casinoNom?.trim() || 'Casino';
      const list = groups.get(key) ?? [];
      list.push(atelier);
      groups.set(key, list);
    }
    return [...groups.entries()]
      .sort(([a], [b]) => a.localeCompare(b, 'fr'))
      .map(([casinoNom, ateliers]) => ({
        casinoNom,
        ateliers: [...ateliers].sort((a, b) => (a.nom || '').localeCompare(b.nom || '', 'fr'))
      }));
  });

  private sessionExpiredHandled = false;

  constructor(private http: HttpClient, private router: Router) {
    // Nettoie une session déjà expirée au démarrage (token encore en localStorage).
    if (this.getToken() && this.isTokenExpired()) {
      this.clearSession();
    }
  }

  login(payload: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${environment.apiUrl}/api/auth/login`, payload).pipe(
      tap((res) => {
        this.sessionExpiredHandled = false;
        localStorage.setItem(TOKEN_KEY, res.token);
        localStorage.setItem(USER_KEY, res.username);
        localStorage.setItem(ROLE_KEY, res.role);
        this.username.set(res.username);
        this.role.set(res.role);
        const nom = res.nom?.trim() || '';
        const prenom = res.prenom?.trim() || '';
        this.nom.set(nom || null);
        this.prenom.set(prenom || null);
        if (nom) {
          localStorage.setItem(NOM_KEY, nom);
        } else {
          localStorage.removeItem(NOM_KEY);
        }
        if (prenom) {
          localStorage.setItem(PRENOM_KEY, prenom);
        } else {
          localStorage.removeItem(PRENOM_KEY);
        }
        this.groupeNom.set(res.groupeNom ?? null);
        if (res.groupeNom) {
          localStorage.setItem(GROUPE_KEY, res.groupeNom);
        }
        const ateliers = (res.ateliers ?? []).map((a) => ({
          ...a,
          id: Number(a.id)
        }));
        this.ateliers.set(ateliers);
        localStorage.setItem(ATELIERS_KEY, JSON.stringify(ateliers));
        const preferred = this.toAtelierId(res.atelierId);
        const atelierId =
          preferred != null && ateliers.some((a) => a.id === preferred)
            ? preferred
            : (ateliers[0]?.id ?? null);
        this.setAtelierId(atelierId);
        this.atelierRevision.set(0);
      })
    );
  }

  setAtelierId(id: number | null): void {
    const normalized = this.toAtelierId(id);
    this.atelierId.set(normalized);
    if (normalized == null) {
      localStorage.removeItem(ATELIER_KEY);
    } else {
      localStorage.setItem(ATELIER_KEY, String(normalized));
    }
  }

  switchAtelier(id: number | string): void {
    if (!this.isAdmin()) {
      return;
    }
    const nextId = this.toAtelierId(id);
    if (nextId == null) {
      return;
    }
    if (this.atelierId() === nextId) {
      return;
    }
    this.setAtelierId(nextId);
    // Force le rechargement des écrans (listes / formulaires) pour le nouvel atelier
    this.atelierRevision.update((v) => v + 1);
    // Mémorise l'atelier préféré admin (prochaine connexion)
    this.http.put(`${environment.apiUrl}/api/ateliers/preferred`, { atelierId: nextId }).subscribe({
      error: () => {
        // Le changement local reste actif même si la persistance échoue
      }
    });
    // Réactive la route après remount de <router-outlet>
    const url = this.router.url;
    setTimeout(() => {
      void this.router.navigateByUrl(url);
    }, 0);
  }

  logout(): void {
    this.clearSession();
    this.router.navigate(['/login']);
  }

  /**
   * Appelé quand le serveur répond 401 (token invalide / expiré).
   * Nettoie la session et renvoie vers la page de connexion.
   */
  handleSessionExpired(): void {
    if (this.sessionExpiredHandled) {
      return;
    }
    this.sessionExpiredHandled = true;
    this.clearSession();
    if (!this.router.url.startsWith('/login')) {
      void this.router.navigate(['/login'], { queryParams: { reason: 'expired' } });
    }
  }

  /** Mémorise identifiant + mot de passe pour la prochaine visite. */
  rememberCredentials(username: string, password: string): void {
    localStorage.setItem(REMEMBER_USER_KEY, username);
    localStorage.setItem(REMEMBER_PASS_KEY, password);
  }

  clearRememberedCredentials(): void {
    localStorage.removeItem(REMEMBER_USER_KEY);
    localStorage.removeItem(REMEMBER_PASS_KEY);
  }

  getRememberedCredentials(): { username: string; password: string } | null {
    const username = localStorage.getItem(REMEMBER_USER_KEY);
    const password = localStorage.getItem(REMEMBER_PASS_KEY);
    if (!username || password == null || password === '') {
      return null;
    }
    return { username, password };
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  getAtelierId(): number | null {
    return this.atelierId();
  }

  isLoggedIn(): boolean {
    const token = this.getToken();
    if (!token) {
      return false;
    }
    if (this.isTokenExpired(token)) {
      this.clearSession();
      return false;
    }
    return true;
  }

  /** Indique si le JWT est expiré (lecture du claim {@code exp} côté client). */
  isTokenExpired(token: string | null = this.getToken()): boolean {
    if (!token) {
      return true;
    }
    const exp = this.readJwtExp(token);
    if (exp == null) {
      return false;
    }
    // Petite marge pour éviter les courses avec l'horloge serveur.
    return Date.now() >= exp * 1000 - 5_000;
  }

  roleLabel(): string {
    if (this.isAdmin()) {
      return 'Administrateur';
    }
    if (this.isTechnicien()) {
      return 'Technicien';
    }
    return this.role() || '';
  }

  private clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(NOM_KEY);
    localStorage.removeItem(PRENOM_KEY);
    localStorage.removeItem(ROLE_KEY);
    localStorage.removeItem(ATELIER_KEY);
    localStorage.removeItem(ATELIERS_KEY);
    localStorage.removeItem(GROUPE_KEY);
    this.username.set(null);
    this.nom.set(null);
    this.prenom.set(null);
    this.role.set(null);
    this.atelierId.set(null);
    this.ateliers.set([]);
    this.groupeNom.set(null);
    this.atelierRevision.set(0);
  }

  private readJwtExp(token: string): number | null {
    try {
      const parts = token.split('.');
      if (parts.length < 2) {
        return null;
      }
      const payload = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = payload + '='.repeat((4 - (payload.length % 4)) % 4);
      const json = JSON.parse(atob(padded)) as { exp?: number };
      return typeof json.exp === 'number' ? json.exp : null;
    } catch {
      return null;
    }
  }

  private toAtelierId(value: unknown): number | null {
    if (value == null || value === '') {
      return null;
    }
    const n = Number(value);
    return Number.isFinite(n) ? n : null;
  }

  private readUser(): string | null {
    return localStorage.getItem(USER_KEY);
  }

  private readRole(): string | null {
    return localStorage.getItem(ROLE_KEY);
  }

  private readAtelierId(): number | null {
    return this.toAtelierId(localStorage.getItem(ATELIER_KEY));
  }

  private readAteliers(): AtelierSummary[] {
    try {
      const raw = localStorage.getItem(ATELIERS_KEY);
      const list = raw ? (JSON.parse(raw) as AtelierSummary[]) : [];
      return list.map((a) => ({ ...a, id: Number(a.id) }));
    } catch {
      return [];
    }
  }

  /** Rafraîchit la liste des ateliers du header après une gestion admin. */
  refreshAteliers(): void {
    this.http.get<AtelierSummary[]>(`${environment.apiUrl}/api/ateliers`).subscribe({
      next: (list) => {
        const ateliers = (list ?? []).map((a) => ({ ...a, id: Number(a.id) }));
        this.ateliers.set(ateliers);
        localStorage.setItem(ATELIERS_KEY, JSON.stringify(ateliers));
        const current = this.atelierId();
        if (current != null && !ateliers.some((a) => a.id === current)) {
          const fallback = ateliers[0]?.id ?? null;
          this.setAtelierId(fallback);
          this.atelierRevision.update((v) => v + 1);
        }
      }
    });
  }
}
