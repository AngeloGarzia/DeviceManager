import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AiChatResponse {
  reply: string;
  enabled: boolean;
}

export interface AiLabelScanResponse {
  enabled: boolean;
  nom?: string | null;
  reference?: string | null;
  marque?: string | null;
  usage?: string | null;
  rawText?: string | null;
  notes?: string | null;
}

@Injectable({ providedIn: 'root' })
export class AiService {
  private readonly base = `${environment.apiUrl}/api/ai`;

  constructor(private http: HttpClient) {}

  status(): Observable<AiChatResponse> {
    return this.http.get<AiChatResponse>(`${this.base}/status`);
  }

  chat(message: string): Observable<AiChatResponse> {
    return this.http.post<AiChatResponse>(`${this.base}/chat`, { message });
  }

  scanLabel(image: File | Blob): Observable<AiLabelScanResponse> {
    const form = new FormData();
    const file =
      image instanceof File
        ? image
        : new File([image], `label-${Date.now()}.jpg`, { type: image.type || 'image/jpeg' });
    form.append('image', file);
    return this.http.post<AiLabelScanResponse>(`${this.base}/label-scan`, form);
  }
}
