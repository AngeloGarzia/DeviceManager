import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SfmService } from './sfm.service';

describe('SfmService', () => {
  let service: SfmService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [SfmService]
    });
    service = TestBed.inject(SfmService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should list SFM with query', () => {
    service.list('nord').subscribe((list) => expect(list.length).toBe(1));
    const req = http.expectOne((r) => r.url === '/api/sfm' && r.params.get('q') === 'nord');
    req.flush([{
      id: 1,
      nom: 'SFM Nord',
      responsable: 'A',
      telephone: '06',
      email: 'a@b.c',
      contacts: []
    }]);
  });

  it('should create SFM', () => {
    const payload = {
      nom: 'SFM Est',
      marqueIds: [1],
      contacts: [{ nom: 'Alice', telephone: '06', email: 'a@b.c' }]
    };
    service.create(payload).subscribe((s) => expect(s.nom).toBe('SFM Est'));
    const req = http.expectOne('/api/sfm');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 2, ...payload, responsable: 'Alice', telephone: '06', email: 'a@b.c', marques: [] });
  });

  it('should update and delete', () => {
    service.update(1, {
      nom: 'X',
      marqueIds: [2],
      contacts: [{ nom: 'B', telephone: '07', email: 'b@b.c' }]
    }).subscribe((s) => {
      expect(s.nom).toBe('X');
    });
    const put = http.expectOne('/api/sfm/1');
    expect(put.request.method).toBe('PUT');
    put.flush({ id: 1, nom: 'X', responsable: 'B', telephone: '07', email: 'b@b.c', contacts: [], marqueIds: [2], marques: [] });

    service.delete(1).subscribe({ next: () => expect().nothing() });
    const del = http.expectOne('/api/sfm/1');
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
  });
});
