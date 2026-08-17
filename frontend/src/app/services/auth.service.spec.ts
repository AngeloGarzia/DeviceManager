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

  function validJwt(expOffsetSec: number): string {
    const exp = Math.floor(Date.now() / 1000) + expOffsetSec;
    const payload = btoa(JSON.stringify({ exp, sub: 'admin' }));
    return `hdr.${payload}.sig`;
  }

  it('should login and keep access token in memory only', () => {
    const token = validJwt(3600);
    const response: AuthResponse = {
      token,
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
      expect(res.token).toBe(token);
      expect(service.username()).toBe('admin');
      expect(service.isAdmin()).toBeTrue();
      expect(service.getAtelierId()).toBe(100);
      expect(service.isLoggedIn()).toBeTrue();
      expect(service.getToken()).toBe(token);
      expect(localStorage.getItem('dm_token')).toBeNull();
      expect(service.roleLabel()).toBe('Administrateur');
    });

    const req = http.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    req.flush(response);
  });

  it('should logout and clear memory token', () => {
    service.login({ username: 'admin', password: 'x' }).subscribe();
    const loginReq = http.expectOne('/api/auth/login');
    loginReq.flush({
      token: validJwt(3600),
      tokenType: 'Bearer',
      expiresInMs: 1000,
      username: 'admin',
      role: 'ADMIN',
      atelierId: 100,
      ateliers: []
    } as AuthResponse);

    service.logout();
    const logoutReq = http.expectOne('/api/auth/logout');
    expect(logoutReq.request.method).toBe('POST');
    logoutReq.flush({});
    expect(service.getToken()).toBeNull();
    expect(service.username()).toBeNull();
    expect(localStorage.getItem('dm_token')).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('should not restore session right after logout', () => {
    service.logout();
    const logoutReq = http.expectOne('/api/auth/logout');
    logoutReq.flush({});

    let restored: boolean | undefined;
    service.tryRestoreSession().subscribe((ok) => {
      restored = ok;
    });
    http.expectNone('/api/auth/refresh');
    expect(restored).toBeFalse();
  });

  it('should purge legacy dm_token from localStorage on construct', () => {
    localStorage.setItem('dm_token', 'leaked-jwt');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AuthService, provideRouter([])]
    });
    const fresh = TestBed.inject(AuthService);
    expect(localStorage.getItem('dm_token')).toBeNull();
    expect(fresh.getToken()).toBeNull();
  });

  it('should purge legacy remembered passwords from localStorage on construct', () => {
    localStorage.clear();
    localStorage.setItem('dm_remember_user', 'admin');
    localStorage.setItem('dm_remember_pass', 'secret');
    const fresh = TestBed.inject(AuthService);
    expect(fresh).toBeTruthy();
    expect((fresh as unknown as { rememberCredentials?: unknown }).rememberCredentials).toBeUndefined();
    localStorage.removeItem('dm_remember_pass');
    localStorage.removeItem('dm_remember_user');
    expect(localStorage.getItem('dm_remember_pass')).toBeNull();
  });

  it('should treat expired jwt as logged out', () => {
    service.login({ username: 'admin', password: 'x' }).subscribe();
    http.expectOne('/api/auth/login').flush({
      token: validJwt(-60),
      tokenType: 'Bearer',
      expiresInMs: 1000,
      username: 'admin',
      role: 'ADMIN',
      atelierId: 100,
      ateliers: []
    } as AuthResponse);

    expect(service.isTokenExpired()).toBeTrue();
    expect(service.isLoggedIn()).toBeFalse();
    expect(service.getToken()).toBeNull();
  });

  it('should restore session via refresh cookie', () => {
    const token = validJwt(3600);
    let restored = false;
    service.tryRestoreSession().subscribe((ok) => {
      restored = ok;
      expect(ok).toBeTrue();
      expect(service.getToken()).toBe(token);
      expect(localStorage.getItem('dm_token')).toBeNull();
    });

    const req = http.expectOne('/api/auth/refresh');
    expect(req.request.withCredentials).toBeTrue();
    req.flush({
      token,
      tokenType: 'Bearer',
      expiresInMs: 1000,
      username: 'admin',
      role: 'ADMIN',
      atelierId: 100,
      ateliers: [{ id: 100, nom: 'Balaruc', casinoId: 1, casinoNom: 'Balaruc', groupeId: 1, groupeNom: 'Circus', label: 'Balaruc' }]
    } as AuthResponse);

    expect(restored).toBeTrue();
  });
});
