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
        const ateliers = res.ateliers ?? [];
        this.ateliers.set(ateliers);
        localStorage.setItem(ATELIERS_KEY, JSON.stringify(ateliers));
        const atelierId = res.atelierId ?? ateliers[0]?.id ?? null;
        this.setAtelierId(atelierId);
      })
    );
  }

  setAtelierId(id: number | null): void {
    this.atelierId.set(id);
    if (id == null) {
      localStorage.removeItem(ATELIER_KEY);
    } else {
      localStorage.setItem(ATELIER_KEY, String(id));
    }
  }

  switchAtelier(id: number): void {
    this.setAtelierId(id);
    // Mémorise l'atelier préféré sur le compte (prochaine connexion)
    this.http.put(`${environment.apiUrl}/api/ateliers/preferred`, { atelierId: id }).subscribe({
      error: () => {
        // Le changement local reste actif même si la persistance échoue
      },
    });
    // Recharge la vue courante pour recharger les données de l'atelier
    const url = this.router.url;
    void this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
      void this.router.navigateByUrl(url);
    });
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

  private readUser(): string | null {
    return localStorage.getItem(USER_KEY);
  }

  private readRole(): string | null {
    return localStorage.getItem(ROLE_KEY);
  }

  private readAtelierId(): number | null {
    const raw = localStorage.getItem(ATELIER_KEY);
    return raw ? Number(raw) : null;
  }

  private readAteliers(): AtelierSummary[] {
    try {
      const raw = localStorage.getItem(ATELIERS_KEY);
      return raw ? (JSON.parse(raw) as AtelierSummary[]) : [];
    } catch {
      return [];
    }
  }
}
