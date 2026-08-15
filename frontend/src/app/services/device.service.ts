import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Device, DeviceDocumentType, DeviceForm } from '../models/models';

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

  create(
    payload: DeviceForm,
    photos: File[],
    documents: { file: File; type: DeviceDocumentType }[] = []
  ): Observable<Device> {
    return this.http.post<Device>(this.base, this.toFormData(payload, photos, documents));
  }

  update(
    id: number,
    payload: DeviceForm,
    photos: File[] = [],
    documents: { file: File; type: DeviceDocumentType }[] = []
  ): Observable<Device> {
    return this.http.put<Device>(`${this.base}/${id}`, this.toFormData(payload, photos, documents));
  }

  /** Met à jour uniquement la quantité en stock. */
  updateStock(id: number, stock: number): Observable<Device> {
    return this.http.patch<Device>(`${this.base}/${id}/stock`, { stock });
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

  resolveDocumentUrl(fileUrl?: string | null): string {
    return this.resolvePhotoUrl(fileUrl);
  }

  /**
   * Relance une fois le chargement d'une photo (API Render free souvent froide → 502).
   */
  retryPhotoOnError(event: Event): void {
    const img = event.target as HTMLImageElement | null;
    if (!img?.src || img.dataset['dmPhotoRetried'] === '1') {
      return;
    }
    img.dataset['dmPhotoRetried'] = '1';
    const base = img.src.split('?')[0];
    window.setTimeout(() => {
      img.src = `${base}?r=${Date.now()}`;
    }, 2500);
  }

  private toFormData(
    payload: DeviceForm,
    photos: File[] = [],
    documents: { file: File; type: DeviceDocumentType }[] = []
  ): FormData {
    const formData = new FormData();
    const data: DeviceForm = {
      ...payload,
      newDocumentTypes: documents.map((d) => d.type)
    };
    formData.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }));
    for (const photo of photos) {
      formData.append('photos', photo, photo.name || 'photo.jpg');
    }
    for (const doc of documents) {
      formData.append('documents', doc.file, doc.file.name || `${doc.type.toLowerCase()}.pdf`);
    }
    return formData;
  }
}
