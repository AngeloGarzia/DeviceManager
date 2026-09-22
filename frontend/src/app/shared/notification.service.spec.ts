import { TestBed } from '@angular/core/testing';
import { MatSnackBar, MatSnackBarConfig } from '@angular/material/snack-bar';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  beforeEach(() => {
    const spy = jasmine.createSpyObj<MatSnackBar>('MatSnackBar', ['open']);
    TestBed.configureTestingModule({
      providers: [
        NotificationService,
        { provide: MatSnackBar, useValue: spy }
      ]
    });
    service = TestBed.inject(NotificationService);
    snackBar = TestBed.inject(MatSnackBar) as jasmine.SpyObj<MatSnackBar>;
  });

  it('opens a success snackbar with panel class dm-snack--success', () => {
    service.success('Enregistré');
    expect(snackBar.open).toHaveBeenCalled();
    const [message, action, config] = snackBar.open.calls.mostRecent().args as [
      string,
      string,
      MatSnackBarConfig
    ];
    expect(message).toBe('Enregistré');
    expect(action).toBe('Fermer');
    expect(config.duration).toBe(3500);
    expect(config.panelClass as string[]).toContain('dm-snack--success');
    expect(config.horizontalPosition).toBe('right');
    expect(config.verticalPosition).toBe('bottom');
  });

  it('opens an error snackbar with longer duration and error class', () => {
    service.error('Boom');
    const [, , config] = snackBar.open.calls.mostRecent().args as [
      string,
      string,
      MatSnackBarConfig
    ];
    expect(config.duration).toBe(6000);
    expect(config.panelClass as string[]).toContain('dm-snack--error');
  });

  it('opens warning and info variants with matching classes', () => {
    service.warning('Attention');
    service.info('Info');

    const warningCall = snackBar.open.calls.first().args as [string, string, MatSnackBarConfig];
    const infoCall = snackBar.open.calls.mostRecent().args as [string, string, MatSnackBarConfig];

    expect(warningCall[0]).toBe('Attention');
    expect(warningCall[2].panelClass as string[]).toContain('dm-snack--warning');
    expect(warningCall[2].duration).toBe(5000);

    expect(infoCall[0]).toBe('Info');
    expect(infoCall[2].panelClass as string[]).toContain('dm-snack--info');
    expect(infoCall[2].duration).toBe(4000);
  });

  it('ignores blank or whitespace-only messages', () => {
    service.success('');
    service.error('   ');
    expect(snackBar.open).not.toHaveBeenCalled();
  });
});
