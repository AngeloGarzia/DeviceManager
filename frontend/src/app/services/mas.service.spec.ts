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
      denoId: 1,
      regleJeuxIds: [1]
    };
    service.create(payload).subscribe((m) => expect(m.numero).toBe('M1'));
    const req = http.expectOne('/api/mas');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 1, ...payload, marque: 'N', marqueLabel: 'Novomatic', denoLabel: '0,50 €' });
  });

  it('should list regles jeux', () => {
    service.listReglesJeux().subscribe((list) => expect(list.length).toBe(1));
    const req = http.expectOne('/api/mas/regles-jeux');
    req.flush([{ id: 1, label: 'Règle A', masCount: 0 }]);
  });

  it('should create regle jeux with PDF', () => {
    const file = new File(['%PDF'], 'regle.pdf', { type: 'application/pdf' });
    service.createRegleJeux('Règle A', file, 'Description').subscribe((r) => expect(r.label).toBe('Règle A'));
    const req = http.expectOne('/api/mas/regles-jeux');
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBeTrue();
    req.flush({ id: 2, label: 'Règle A', masCount: 0 });
  });

  it('should get regle jeux detail', () => {
    service.getRegleJeux(2).subscribe((r) => expect(r.masIds).toEqual([20]));
    const req = http.expectOne('/api/mas/regles-jeux/2');
    req.flush({ id: 2, label: 'Règle A', masIds: [20], masCount: 1 });
  });

  it('should link MAS to regle jeux', () => {
    service.linkRegleJeuxMas(2, [20, 21]).subscribe((r) => expect(r.masCount).toBe(2));
    const req = http.expectOne('/api/mas/regles-jeux/2/mas');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ masIds: [20, 21] });
    req.flush({ id: 2, label: 'Règle A', masIds: [20, 21], masCount: 2 });
  });

  it('should analyze regle jeux PDF', () => {
    const file = new File(['%PDF'], 'regle.pdf', { type: 'application/pdf' });
    service.analyzeRegleJeuxPdf(file).subscribe((r) => expect(r.label).toBe('Règle proposée'));
    const req = http.expectOne('/api/mas/regles-jeux/analyze');
    expect(req.request.method).toBe('POST');
    req.flush({ enabled: true, label: 'Règle proposée', description: 'Synthèse' });
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
