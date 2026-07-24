import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Device, DeviceForm } from '../models/models';

@Injectable({ providedIn: 'root' })
export class DeviceService {
  private readonly base = `${environment.apiUrl}/api/devices`;

  constructor(private http: HttpClient) {}

  list(q = ''): Observable<Device[]> {
    let params = new HttpParams();
    if (q.trim()) {
      params = params.set('q', q.trim());
    }
    return this.http.get<Device[]>(this.base, { params });
  }

  get(id: number): Observable<Device> {
    return this.http.get<Device>(`${this.base}/${id}`);
  }

  create(payload: DeviceForm, photo: File): Observable<Device> {
    return this.http.post<Device>(this.base, this.toFormData(payload, photo));
  }

  update(id: number, payload: DeviceForm, photo?: File | null): Observable<Device> {
    return this.http.put<Device>(`${this.base}/${id}`, this.toFormData(payload, photo ?? undefined));
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  resolvePhotoUrl(photoUrl?: string | null): string {
    if (!photoUrl) {
      return '';
    }
    if (photoUrl.startsWith('http')) {
      return photoUrl;
    }
    return `${environment.apiUrl}${photoUrl}`;
  }

  private toFormData(payload: DeviceForm, photo?: File): FormData {
    const formData = new FormData();
    formData.append(
      'data',
      new Blob([JSON.stringify(payload)], { type: 'application/json' })
    );
    if (photo) {
      formData.append('photo', photo, photo.name || 'photo.jpg');
    }
    return formData;
  }
}
