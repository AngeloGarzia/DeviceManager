import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MasService } from './mas.service';

describe('MasService', () => {
  let service: MasService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [MasService]
    });
    service = TestBed.inject(MasService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should list MAS', () => {
    service.list().subscribe((list) => expect(list.length).toBe(1));
    const req = http.expectOne('/api/mas');
    req.flush([{ id: 1, numero: 'M1', marqueId: 1, marque: 'N', marqueLabel: 'Novomatic', utilise: true }]);
  });

  it('should create marque', () => {
    service.createMarque('Novomatic').subscribe((m) => expect(m.label).toBe('Novomatic'));
    const req = http.expectOne('/api/mas/marques');
    expect(req.request.body).toEqual({ label: 'Novomatic' });
    req.flush({ id: 1, code: 'NOVOMATIC', label: 'Novomatic', value: 1 });
  });

  it('should create MAS', () => {
    const payload = { numero: 'M1', marqueId: 1, utilise: true };
    service.create(payload).subscribe((m) => expect(m.numero).toBe('M1'));
    const req = http.expectOne('/api/mas');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 1, ...payload, marque: 'N', marqueLabel: 'Novomatic' });
  });

  it('should delete MAS', () => {
    service.delete(9).subscribe({ next: () => expect().nothing() });
    const req = http.expectOne('/api/mas/9');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
