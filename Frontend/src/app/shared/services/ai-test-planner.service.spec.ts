import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AiTestPlannerService } from './ai-test-planner.service';
import { environment } from '../../../environments/environment';

describe('AiTestPlannerService', () => {
  const base = `${environment.apiUrl}/ai/test-plans`;
  let service: AiTestPlannerService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AiTestPlannerService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('generates a plan from a goal', () => {
    service.generate({ goal: 'Open Wi-Fi settings', deviceId: 'emulator-5554', startScreenId: 3, maxSteps: 10 }).subscribe();
    const req = http.expectOne(base);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ goal: 'Open Wi-Fi settings', deviceId: 'emulator-5554', startScreenId: 3, maxSteps: 10 });
    req.flush({ id: 1 });
  });

  it('lists plans with an optional status filter', () => {
    service.list(0, 20, 'DRAFT').subscribe();
    const req = http.expectOne(r => r.url === base);
    expect(req.request.params.get('status')).toBe('DRAFT');
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });

    service.list(1, 10).subscribe();
    const all = http.expectOne(r => r.url === base);
    expect(all.request.params.has('status')).toBeFalse();
    all.flush({ content: [], page: 1, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('gets and reviews a plan', () => {
    service.get(5).subscribe();
    http.expectOne(`${base}/5`).flush({ id: 5 });

    service.review(5, 'REJECTED', '').subscribe();
    const req = http.expectOne(`${base}/5/review`);
    expect(req.request.body).toEqual({ decision: 'REJECTED', notes: null });
    req.flush({ id: 5 });
  });
});
