import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AppUser, AppUserForm } from '../models/models';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly base = `${environment.apiUrl}/api/users`;

  constructor(private http: HttpClient) {}

  list(): Observable<AppUser[]> {
    return this.http.get<AppUser[]>(this.base);
  }

  create(payload: AppUserForm): Observable<AppUser> {
    return this.http.post<AppUser>(this.base, payload);
  }

  update(id: number, payload: AppUserForm): Observable<AppUser> {
    return this.http.put<AppUser>(`${this.base}/${id}`, payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
