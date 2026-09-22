import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NotificationService } from '../shared/notification.service';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let notifications: jasmine.SpyObj<NotificationService>;

  beforeEach(() => {
    notifications = jasmine.createSpyObj<NotificationService>('NotificationService', [
      'success',
      'info',
      'warning',
      'error'
    ]);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: NotificationService, useValue: notifications }
      ]
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('shows error snackbar on offline (status 0)', () => {
    http.get('/api/devices').subscribe({
      next: () => fail('should error'),
      error: () => {
        /* expected */
      }
    });
    httpMock
      .expectOne('/api/devices')
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    expect(notifications.error).toHaveBeenCalledOnceWith(
      jasmine.stringMatching(/Serveur injoignable/i)
    );
  });

  it('shows warning snackbar on 403', () => {
    http.get('/api/setup').subscribe({ error: () => undefined });
    httpMock
      .expectOne('/api/setup')
      .flush({ message: 'Accès refusé pour cette action.' }, { status: 403, statusText: 'Forbidden' });

    expect(notifications.warning).toHaveBeenCalledOnceWith('Accès refusé pour cette action.');
  });

  it('shows warning snackbar on 429', () => {
    http.get('/api/auth/login').subscribe({ error: () => undefined });
    httpMock
      .expectOne('/api/auth/login')
      .flush({ message: 'Trop de tentatives. Réessayez plus tard.' }, {
        status: 429,
        statusText: 'Too Many Requests'
      });

    expect(notifications.warning).toHaveBeenCalledOnceWith('Trop de tentatives. Réessayez plus tard.');
  });

  it('shows error snackbar on 503 with API message', () => {
    http.get('/api/mas').subscribe({ error: () => undefined });
    httpMock
      .expectOne('/api/mas')
      .flush({ message: 'Base de données temporairement indisponible.' }, {
        status: 503,
        statusText: 'Service Unavailable'
      });

    expect(notifications.error).toHaveBeenCalledOnceWith(
      'Base de données temporairement indisponible.'
    );
  });

  it('shows error snackbar on generic 500', () => {
    http.get('/api/devices').subscribe({ error: () => undefined });
    httpMock
      .expectOne('/api/devices')
      .flush({ message: 'Boom' }, { status: 500, statusText: 'Internal Server Error' });

    expect(notifications.error).toHaveBeenCalled();
  });

  it('does not notify on 400 (handled locally)', () => {
    http.get('/api/mas').subscribe({ error: () => undefined });
    httpMock
      .expectOne('/api/mas')
      .flush({ message: 'validation' }, { status: 400, statusText: 'Bad Request' });

    expect(notifications.error).not.toHaveBeenCalled();
    expect(notifications.warning).not.toHaveBeenCalled();
  });

  it('does not notify on 404 (handled locally)', () => {
    http.get('/api/mas/999').subscribe({ error: () => undefined });
    httpMock
      .expectOne('/api/mas/999')
      .flush({ message: 'Introuvable' }, { status: 404, statusText: 'Not Found' });

    expect(notifications.error).not.toHaveBeenCalled();
    expect(notifications.warning).not.toHaveBeenCalled();
  });

  it('does not notify on 401 (handled by auth interceptor)', () => {
    http.get('/api/devices').subscribe({ error: () => undefined });
    httpMock
      .expectOne('/api/devices')
      .flush({ message: 'expired' }, { status: 401, statusText: 'Unauthorized' });

    expect(notifications.error).not.toHaveBeenCalled();
    expect(notifications.warning).not.toHaveBeenCalled();
  });

  it('ignores silent URLs (health / refresh / logout)', () => {
    http.get('/actuator/health/liveness').subscribe({ error: () => undefined });
    httpMock
      .expectOne('/actuator/health/liveness')
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    expect(notifications.error).not.toHaveBeenCalled();

    http.post('/api/auth/refresh', {}).subscribe({ error: () => undefined });
    httpMock
      .expectOne('/api/auth/refresh')
      .flush('boom', { status: 500, statusText: 'Internal Server Error' });

    expect(notifications.error).not.toHaveBeenCalled();
  });

  it('still notifies on 503 when API body is missing (Angular fills err.message)', () => {
    http.get('/api/mas').subscribe({ error: () => undefined });
    httpMock
      .expectOne('/api/mas')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });

    // apiErrorMessage renvoie e.message si présent : on vérifie surtout que la notif est bien tirée.
    expect(notifications.error).toHaveBeenCalledTimes(1);
  });
});
