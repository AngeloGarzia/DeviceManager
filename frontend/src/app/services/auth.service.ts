import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AtelierSummary, AuthResponse, LoginRequest } from '../models/models';

const TOKEN_KEY = 'dm_token';
const USER_KEY = 'dm_user';
const ROLE_KEY = 'dm_role';
const ATELIER_KEY = 'dm_atelier_id';
const ATELIERS_KEY = 'dm_ateliers';
const GROUPE_KEY = 'dm_groupe_nom';

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly username = signal<string | null>(this.readUser());
  readonly role = signal<string | null>(this.readRole());
  readonly atelierId = signal<number | null>(this.readAtelierId());
  readonly ateliers = signal<AtelierSummary[]>(this.readAteliers());
  readonly groupeNom = signal<string | null>(localStorage.getItem(GROUPE_KEY));
  /** Incrémenté à chaque changement d'atelier → remount du contenu (rechargement données). */
  readonly atelierRevision = signal(0);

  readonly isAdmin = computed(() => this.role() === 'ADMIN');
  readonly isTechnicien = computed(() => this.role() === 'TECHNICIEN' || this.role() === 'TECH');

  readonly currentAtelier = computed(() => {
    const id = this.atelierId();
    return this.ateliers().find((a) => a.id === id) ?? null;
  });

  constructor(private http: HttpClient, private router: Router) {}

  login(payload: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${environment.apiUrl}/api/auth/login`, payload).pipe(
      tap((res) => {
        localStorage.setItem(TOKEN_KEY, res.token);
        localStorage.setItem(USER_KEY, res.username);
        localStorage.setItem(ROLE_KEY, res.role);
        this.username.set(res.username);
        this.role.set(res.role);
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
    // Mémorise l'atelier préféré sur le compte (prochaine connexion)
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
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(ROLE_KEY);
    localStorage.removeItem(ATELIER_KEY);
    localStorage.removeItem(ATELIERS_KEY);
    localStorage.removeItem(GROUPE_KEY);
    this.username.set(null);
    this.role.set(null);
    this.atelierId.set(null);
    this.ateliers.set([]);
    this.groupeNom.set(null);
    this.atelierRevision.set(0);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  getAtelierId(): number | null {
    return this.atelierId();
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
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
}
