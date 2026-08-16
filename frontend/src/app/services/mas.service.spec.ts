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
    req.flush([{
      id: 1,
      numero: 'M1',
      marqueId: 1,
      marque: 'N',
      marqueLabel: 'Novomatic',
      statut: 'UTILISEE',
      statutLabel: 'Machine utilisée',
      utilise: true
    }]);
  });

  it('should create marque', () => {
    service.createMarque('Novomatic').subscribe((m) => expect(m.label).toBe('Novomatic'));
    const req = http.expectOne('/api/mas/marques');
    expect(req.request.body).toEqual({ label: 'Novomatic' });
    req.flush({ id: 1, code: 'NOVOMATIC', label: 'Novomatic', value: 1 });
  });

  it('should list denos', () => {
    service.listDenos().subscribe((list) => expect(list.length).toBe(1));
    const req = http.expectOne('/api/mas/denos');
    req.flush([{ id: 1, valeur: 0.5, label: '0,50 €', value: 1 }]);
  });

  it('should create MAS', () => {
    const payload = {
      numero: 'M1',
      marqueId: 1,
      statut: 'UTILISEE',
      utilise: true,
      numeroSocle: 'S-12',
      tauxRedistribution: 94.5,
      denoId: 1
    };
    service.create(payload).subscribe((m) => expect(m.numero).toBe('M1'));
    const req = http.expectOne('/api/mas');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 1, ...payload, marque: 'N', marqueLabel: 'Novomatic', denoLabel: '0,50 €' });
  });

  it('should attach bon de destruction', () => {
    const file = new File(['%PDF'], 'bon.pdf', { type: 'application/pdf' });
    service.attachBonDestruction(9, file).subscribe((m) => expect(m.id).toBe(9));
    const req = http.expectOne('/api/mas/9/bon-destruction');
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBeTrue();
    req.flush({
      id: 9,
      numero: 'M1',
      marqueId: 1,
      marque: 'N',
      marqueLabel: 'Novomatic',
      statut: 'DETRUITE',
      statutLabel: 'Détruite',
      utilise: false,
      destructionOriginalName: 'bon.pdf'
    });
  });
});
