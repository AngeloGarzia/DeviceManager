import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { of } from 'rxjs';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(() => {
    auth = jasmine.createSpyObj('AuthService', [
      'getToken',
      'isTokenExpired',
      'isLoggedIn',
      'handleSessionExpired',
      'tryRestoreSession'
    ]);
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: auth }, provideRouter([])]
    });
    router = TestBed.inject(Router);
    spyOn(router, 'createUrlTree').and.callThrough();
  });

  it('allows logged-in users', () => {
    auth.isLoggedIn.and.returnValue(true);
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(result).toBeTrue();
    expect(auth.tryRestoreSession).not.toHaveBeenCalled();
  });

  it('restores session then allows access', (done) => {
    auth.isLoggedIn.and.returnValue(false);
    auth.tryRestoreSession.and.returnValue(of(true));
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    (result as ReturnType<AuthService['tryRestoreSession']>).subscribe((value) => {
      expect(value).toBeTrue();
      done();
    });
  });

  it('redirects anonymous users to login when restore fails', (done) => {
    auth.isLoggedIn.and.returnValue(false);
    auth.tryRestoreSession.and.returnValue(of(false));
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    (result as ReturnType<AuthService['tryRestoreSession']>).subscribe((value) => {
      expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
      expect(value).toEqual(jasmine.any(UrlTree));
      done();
    });
  });
});
