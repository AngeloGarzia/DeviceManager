import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create the app and show wake overlay', fakeAsync(() => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    tick(0);
    expect(fixture.componentInstance.showOverlay()).toBeTrue();
    const req = httpMock.expectOne('/actuator/health/liveness');
    req.flush('{"status":"UP"}', { status: 200, statusText: 'OK' });
    tick(1600);
    expect(fixture.componentInstance).toBeTruthy();
    fixture.destroy();
  }));
});
