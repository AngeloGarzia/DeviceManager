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

  it('should create order request and refresh pending count', () => {
    const payload = { message: 'Besoin urgent', lignes: [{ deviceId: 1, quantite: 2 }] };
    service.create(payload).subscribe((o) => expect(o.message).toBe('Besoin urgent'));
    const req = http.expectOne('/api/order-requests');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 1, message: 'Besoin urgent', status: 'SENT', lignes: [] });
    const countReq = http.expectOne('/api/order-requests/pending-count');
    countReq.flush({ count: 1 });
    expect(service.pendingCount()).toBe(1);
  });

  it('should list order requests', () => {
    service.list().subscribe((list) => expect(list.length).toBe(0));
    http.expectOne('/api/order-requests').flush([]);
  });

  it('should validate order request and refresh pending count', () => {
    service.validate(5).subscribe((o) => expect(o.status).toBe('VALIDATED'));
    const req = http.expectOne('/api/order-requests/5/validate');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 5, message: 'ok', status: 'VALIDATED', lignes: [] });
    const countReq = http.expectOne('/api/order-requests/pending-count');
    countReq.flush({ count: 0 });
    expect(service.pendingCount()).toBe(0);
  });

  it('should delete order request and refresh pending count', () => {
    service.delete(5).subscribe({ next: () => expect().nothing() });
    const req = http.expectOne('/api/order-requests/5');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    const countReq = http.expectOne('/api/order-requests/pending-count');
    countReq.flush({ count: 0 });
  });

  it('should preview create and validate mails', () => {
    const payload = { message: 'Devis', lignes: [{ deviceId: 1, quantite: 1 }] };
    service.previewCreate(payload).subscribe((mails) => expect(mails.length).toBe(1));
    const createReq = http.expectOne('/api/order-requests/mail-preview');
    expect(createReq.request.method).toBe('POST');
    createReq.flush([{ kind: 'ADMIN', to: 'a@b.c', subject: 's', body: 'b' }]);

    service.previewValidate(9).subscribe((mails) => expect(mails.length).toBe(1));
    const validateReq = http.expectOne('/api/order-requests/9/mail-preview');
    expect(validateReq.request.method).toBe('GET');
    validateReq.flush([{ kind: 'SFM', to: 's@f.m', subject: 'devis', body: 'body' }]);
  });

  it('should refresh pending count', () => {
    service.refreshPendingCount();
    const req = http.expectOne('/api/order-requests/pending-count');
    req.flush({ count: 3 });
    expect(service.pendingCount()).toBe(3);
  });
});
