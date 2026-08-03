import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { OrderRequestService } from './order-request.service';

describe('OrderRequestService', () => {
  let service: OrderRequestService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [OrderRequestService]
    });
    service = TestBed.inject(OrderRequestService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should create order request', () => {
    const payload = { message: 'Besoin urgent', lignes: [{ deviceId: 1, quantite: 2 }] };
    service.create(payload).subscribe((o) => expect(o.message).toBe('Besoin urgent'));
    const req = http.expectOne('/api/order-requests');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 1, message: 'Besoin urgent', status: 'SENT', lignes: [] });
    const countReq = http.expectOne('/api/order-requests/pending-count');
    countReq.flush({ count: 1 });
  });

  it('should list order requests', () => {
    service.list().subscribe((list) => expect(list.length).toBe(0));
    http.expectOne('/api/order-requests').flush([]);
  });

  it('should refresh pending count', () => {
    service.refreshPendingCount();
    const req = http.expectOne('/api/order-requests/pending-count');
    req.flush({ count: 3 });
    expect(service.pendingCount()).toBe(3);
  });
});
