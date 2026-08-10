import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/** Champs RGPD publics (clé → valeur texte). */
export type PrivacyPolicyFields = Record<string, string>;

@Injectable({ providedIn: 'root' })
export class PrivacyService {
  private readonly base = `${environment.apiUrl}/api/privacy`;

  constructor(private http: HttpClient) {}

  /** Charge les mentions éditables (accès public). */
  getPolicyFields(): Observable<PrivacyPolicyFields> {
    return this.http.get<PrivacyPolicyFields>(this.base);
  }
}
