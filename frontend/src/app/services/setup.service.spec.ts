import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SetupService } from './setup.service';

describe('SetupService', () => {
  let service: SetupService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [SetupService]
    });
    service = TestBed.inject(SetupService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should list settings', () => {
    service.list().subscribe((list) => expect(list.length).toBe(1));
    http.expectOne('/api/setup').flush([
      { key: 'MAIL_ENABLED', value: 'false', label: 'Mail', category: 'Messagerie', secret: false }
    ]);
  });

  it('should update settings', () => {
    service.update({ MAIL_ENABLED: 'true' }).subscribe((list) => expect(list[0].value).toBe('true'));
    const req = http.expectOne('/api/setup');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ values: { MAIL_ENABLED: 'true' } });
    req.flush([
      { key: 'MAIL_ENABLED', value: 'true', label: 'Mail', category: 'Messagerie', secret: false }
    ]);
  });
});
