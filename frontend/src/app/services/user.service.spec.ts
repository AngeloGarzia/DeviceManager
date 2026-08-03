import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { UserService } from './user.service';

describe('UserService', () => {
  let service: UserService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [UserService]
    });
    service = TestBed.inject(UserService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should list users', () => {
    service.list().subscribe((users) => expect(users.length).toBe(1));
    http.expectOne('/api/users').flush([
      { id: 1, username: 'admin', nom: 'Admin', prenom: 'Sys', email: 'admin@test.local', role: 'ADMIN' }
    ]);
  });

  it('should create user', () => {
    const payload = {
      username: 'tech',
      password: 'tech123',
      role: 'TECHNICIEN',
      nom: 'Martin',
      prenom: 'Alice',
      email: 'alice.martin@test.local'
    };
    service.create(payload).subscribe((u) => expect(u.username).toBe('tech'));
    const req = http.expectOne('/api/users');
    expect(req.request.method).toBe('POST');
    req.flush({
      id: 2,
      username: 'tech',
      nom: 'Martin',
      prenom: 'Alice',
      email: 'alice.martin@test.local',
      role: 'TECHNICIEN'
    });
  });

  it('should update and delete user', () => {
    service
      .update(2, {
        username: 'tech',
        role: 'TECHNICIEN',
        nom: 'Martin',
        prenom: 'Alice',
        email: 'alice.martin@test.local'
      })
      .subscribe((u) => expect(u.username).toBe('tech'));
    const put = http.expectOne('/api/users/2');
    expect(put.request.method).toBe('PUT');
    put.flush({
      id: 2,
      username: 'tech',
      nom: 'Martin',
      prenom: 'Alice',
      email: 'alice.martin@test.local',
      role: 'TECHNICIEN'
    });

    service.delete(2).subscribe({ next: () => expect().nothing() });
    const del = http.expectOne('/api/users/2');
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
  });
});
