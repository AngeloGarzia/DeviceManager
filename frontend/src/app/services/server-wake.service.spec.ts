import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ServerWakeService } from './server-wake.service';

describe('ServerWakeService', () => {
  let service: ServerWakeService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ServerWakeService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('shows overlay immediately and completes as ready when health responds OK', fakeAsync(() => {
    let lastReady = false;
    let lastProgress = 0;
    let sawOverlay = false;

    const sub = service.wake().subscribe((s) => {
      if (s.showOverlay) {
        sawOverlay = true;
      }
      lastReady = s.ready;
      lastProgress = s.progress;
    });

    tick(0);
    expect(sawOverlay).toBeTrue();

    const req = httpMock.expectOne('/actuator/health/liveness');
    req.flush('{"status":"UP"}', { status: 200, statusText: 'OK' });
    // minDisplayMs = 1200
    tick(1300);

    expect(lastReady).toBeTrue();
    expect(lastProgress).toBe(100);
    sub.unsubscribe();
  }));
});
