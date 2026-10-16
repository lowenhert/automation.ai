import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ScreenExplorationService, isOpen } from './screen-exploration.service';
import { environment } from '../../../environments/environment';

describe('ScreenExplorationService', () => {
  const base = `${environment.apiUrl}/exploration`;
  let service: ScreenExplorationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ScreenExplorationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts a bounded session', () => {
    service.start({ deviceId: 'emulator-5554', mode: 'AUTO', maxSteps: 20, allowSelect: false }).subscribe();
    const req = http.expectOne(`${base}/sessions`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ deviceId: 'emulator-5554', mode: 'AUTO', maxSteps: 20, allowSelect: false });
    req.flush({ id: 1 });
  });

  it('sends an action and stops a session', () => {
    service.act(7, 'DPAD_DOWN').subscribe();
    const act = http.expectOne(`${base}/sessions/7/actions`);
    expect(act.request.body).toEqual({ action: 'DPAD_DOWN' });
    act.flush({});

    service.stop(7).subscribe();
    expect(http.expectOne(`${base}/sessions/7/stop`).request.method).toBe('POST');
  });

  it('loads steps and the device graph', () => {
    service.steps(7, 0, 50).subscribe();
    const steps = http.expectOne(r => r.url === `${base}/sessions/7/steps`);
    expect(steps.request.params.get('size')).toBe('50');
    steps.flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 });

    service.graph('192.168.1.20:5555').subscribe();
    const graph = http.expectOne(r => r.url === `${base}/graph`);
    expect(graph.request.params.get('deviceId')).toBe('192.168.1.20:5555');
    graph.flush({ deviceSerial: '192.168.1.20:5555', nodes: [], edges: [] });
  });

  it('knows which statuses are open', () => {
    expect(isOpen('ACTIVE')).toBeTrue();
    expect(isOpen('RUNNING')).toBeTrue();
    expect(isOpen('STOPPED')).toBeFalse();
    expect(isOpen('LIMIT_REACHED')).toBeFalse();
    expect(isOpen(undefined)).toBeFalse();
  });
});
