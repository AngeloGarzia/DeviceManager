import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthResponse } from '../models/models';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AuthService, provideRouter([])]
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('should login and persist session', () => {
    const response: AuthResponse = {
      token: 'jwt',
      tokenType: 'Bearer',
      expiresInMs: 1000,
      username: 'admin',
      role: 'ADMIN',
      groupeId: 1,
      groupeNom: 'Circus',
      atelierId: 100,
      ateliers: [{ id: 100, nom: 'Balaruc', casinoId: 1, casinoNom: 'Balaruc', groupeId: 1, groupeNom: 'Circus', label: 'Balaruc' }]
    };

    service.login({ username: 'admin', password: 'admin123' }).subscribe((res) => {
      expect(res.token).toBe('jwt');
      expect(service.username()).toBe('admin');
      expect(service.isAdmin()).toBeTrue();
      expect(service.getAtelierId()).toBe(100);
      expect(service.isLoggedIn()).toBeTrue();
      expect(service.roleLabel()).toBe('Administrateur');
    });

    const req = http.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    req.flush(response);
  });

  it('should logout and clear storage', () => {
    localStorage.setItem('dm_token', 'jwt');
    service.logout();
    expect(service.getToken()).toBeNull();
    expect(service.username()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});
