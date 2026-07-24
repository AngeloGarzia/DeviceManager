import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { DeviceService } from './device.service';
import { Device, DeviceForm } from '../models/models';

describe('DeviceService', () => {
  let service: DeviceService;
  let http: HttpTestingController;

  const form: DeviceForm = {
    nom: 'Carte',
    reference: 'REF-1',
    usage: 'Usage',
    dateAcquisition: '2024-01-01',
    obsolete: false,
    sfmId: 1,
    masId: 2
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [DeviceService]
    });
    service = TestBed.inject(DeviceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should list devices with query', () => {
    service.list('carte').subscribe((list) => expect(list.length).toBe(1));
    const req = http.expectOne((r) => r.url === '/api/devices' && r.params.get('q') === 'carte');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 1, ...form, photoUrl: '/u', sfmNom: 'SFM', masNumero: 'M1', masMarque: 'N' } as Device]);
  });

  it('should create multipart payload', () => {
    const photo = new File([new Uint8Array([1])], 'a.jpg', { type: 'image/jpeg' });
    service.create(form, photo).subscribe((d) => expect(d.id).toBe(1));
    const req = http.expectOne('/api/devices');
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBeTrue();
    req.flush({ id: 1, ...form } as Device);
  });

  it('should delete device', () => {
    service.delete(5).subscribe();
    const req = http.expectOne('/api/devices/5');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('should resolve photo url', () => {
    expect(service.resolvePhotoUrl('/uploads/a.jpg')).toBe('/uploads/a.jpg');
    expect(service.resolvePhotoUrl('https://cdn/x.jpg')).toBe('https://cdn/x.jpg');
    expect(service.resolvePhotoUrl(null)).toBe('');
  });
});
