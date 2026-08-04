import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AiChatResponse {
  reply: string;
  enabled: boolean;
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
}
