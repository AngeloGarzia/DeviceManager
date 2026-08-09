import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(() => {
    auth = jasmine.createSpyObj('AuthService', ['getToken', 'isTokenExpired', 'isLoggedIn', 'handleSessionExpired']);
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: auth }, provideRouter([])]
    });
    router = TestBed.inject(Router);
    spyOn(router, 'createUrlTree').and.callThrough();
  });

  it('allows logged-in users', () => {
    auth.getToken.and.returnValue('jwt');
    auth.isTokenExpired.and.returnValue(false);
    auth.isLoggedIn.and.returnValue(true);
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(result).toBeTrue();
  });

  it('redirects anonymous users to login', () => {
    auth.getToken.and.returnValue(null);
    auth.isLoggedIn.and.returnValue(false);
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
    expect(result).toEqual(jasmine.anything());
  });
});
