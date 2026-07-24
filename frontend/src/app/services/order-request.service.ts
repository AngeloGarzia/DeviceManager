import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { OrderRequest, OrderRequestForm } from '../models/models';

@Injectable({ providedIn: 'root' })
export class OrderRequestService {
  private readonly base = `${environment.apiUrl}/api/order-requests`;

  constructor(private http: HttpClient) {}

  create(payload: OrderRequestForm): Observable<OrderRequest> {
    return this.http.post<OrderRequest>(this.base, payload);
  }

  list(): Observable<OrderRequest[]> {
    return this.http.get<OrderRequest[]>(this.base);
  }
}
