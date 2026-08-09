import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { adminGuard } from './admin.guard';
import { AuthService } from '../services/auth.service';

describe('adminGuard', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let router: Router;

  beforeEach(() => {
    auth = jasmine.createSpyObj('AuthService', ['isLoggedIn', 'isAdmin']);
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: auth }, provideRouter([])]
    });
    router = TestBed.inject(Router);
    spyOn(router, 'createUrlTree').and.callThrough();
  });

  it('allows admins', () => {
    auth.isLoggedIn.and.returnValue(true);
    auth.isAdmin.and.returnValue(true);
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    expect(result).toBeTrue();
  });

  it('redirects non-admins to devices', () => {
    auth.isLoggedIn.and.returnValue(true);
    auth.isAdmin.and.returnValue(false);
    TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    expect(router.createUrlTree).toHaveBeenCalledWith(['/devices']);
  });
});
