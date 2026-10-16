import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ScreenHistoryService } from './screen-history.service';
import { environment } from '../../../environments/environment';

describe('ScreenHistoryService', () => {
  const base = `${environment.apiUrl}/screens`;
  const id = '3f1c2b6e-1a2b-4c3d-8e9f-0a1b2c3d4e5f';
  let service: ScreenHistoryService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ScreenHistoryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists a page of screens', () => {
    service.list(2, 10).subscribe(p => expect(p.page).toBe(2));
    const req = http.expectOne(r => r.url === base);
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.has('deviceId')).toBeFalse();
    req.flush({ content: [], page: 2, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('filters the list by device', () => {
    service.list(0, 10, '192.168.1.10:5555').subscribe();
    const req = http.expectOne(r => r.url === base);
    expect(req.request.params.get('deviceId')).toBe('192.168.1.10:5555');
    req.flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('searches with the text as a query parameter', () => {
    service.search('Wi-Fi & 100%', 0, 10).subscribe();
    const req = http.expectOne(r => r.url === `${base}/search`);
    expect(req.request.params.get('text')).toBe('Wi-Fi & 100%');
    req.flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('gets screen detail and image', () => {
    service.get(id).subscribe(d => expect(d.id).toBe(id));
    http.expectOne(`${base}/${id}`).flush({ id });

    service.getImage(id).subscribe(b => expect(b.size).toBe(1));
    const img = http.expectOne(`${base}/${id}/image`);
    expect(img.request.responseType).toBe('blob');
    img.flush(new Blob([new Uint8Array([1])], { type: 'image/png' }));
  });
});
