import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { AppSetting } from '../models/models';

@Injectable({ providedIn: 'root' })
export class SetupService {
  private readonly base = `${environment.apiUrl}/api/setup`;

  constructor(private http: HttpClient) {}

  list(): Observable<AppSetting[]> {
    return this.http.get<AppSetting[]>(this.base);
  }

  update(values: Record<string, string>): Observable<AppSetting[]> {
    return this.http.put<AppSetting[]>(this.base, { values });
  }
}
