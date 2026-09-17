import { TestBed } from '@angular/core/testing';
import { NgZone } from '@angular/core';
import { AuthService } from './auth.service';
import { IdleSessionService } from './idle-session.service';

describe('IdleSessionService', () => {
  let service: IdleSessionService;
  let logoutDueToIdle: jasmine.Spy;

  beforeEach(() => {
    jasmine.clock().install();
    logoutDueToIdle = jasmine.createSpy('logoutDueToIdle');
    TestBed.configureTestingModule({
      providers: [
        IdleSessionService,
        {
          provide: AuthService,
          useValue: {
            getToken: () => 'token',
            logoutDueToIdle
          }
        }
      ]
    });
    const zone = TestBed.inject(NgZone);
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    spyOn(zone, 'run').and.callFake((fn: any) => fn());
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    spyOn(zone, 'runOutsideAngular').and.callFake((fn: any) => fn());
    service = TestBed.inject(IdleSessionService);
  });

  afterEach(() => {
    service.stop();
    jasmine.clock().uninstall();
  });

  it('logs out after 30 minutes of inactivity', () => {
    service.start();
    jasmine.clock().tick(30 * 60 * 1000);
    expect(logoutDueToIdle).toHaveBeenCalled();
  });

  it('opens warning 2 minutes before logout and staySignedIn resets', () => {
    service.start();
    jasmine.clock().tick(28 * 60 * 1000);
    expect(service.warningOpen()).toBeTrue();
    expect(logoutDueToIdle).not.toHaveBeenCalled();

    service.staySignedIn();
    expect(service.warningOpen()).toBeFalse();

    jasmine.clock().tick(30 * 60 * 1000);
    expect(logoutDueToIdle).toHaveBeenCalled();
  });
});
