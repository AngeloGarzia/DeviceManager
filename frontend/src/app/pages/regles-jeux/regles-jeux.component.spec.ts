import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ReglesJeuxComponent } from './regles-jeux.component';

describe('ReglesJeuxComponent', () => {
  let fixture: ComponentFixture<ReglesJeuxComponent>;
  let component: ReglesJeuxComponent;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReglesJeuxComponent, HttpClientTestingModule]
    }).compileComponents();

    fixture = TestBed.createComponent(ReglesJeuxComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads regles and filters by search query', () => {
    fixture.detectChanges();
    http.expectOne('/api/mas/regles-jeux').flush([
      { id: 1, label: 'Book of Ra', description: 'Novomatic', masCount: 0 },
      { id: 2, label: 'Roulette live', description: 'Table', masCount: 0 }
    ]);
    http.expectOne('/api/mas').flush([]);

    expect(component.items().length).toBe(2);
    component.searchQuery.set('book');
    expect(component.filteredItems().length).toBe(1);
    expect(component.filteredItems()[0].label).toBe('Book of Ra');
  });
});
