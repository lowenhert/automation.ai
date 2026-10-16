import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ScreenCaptureService } from './screen-capture.service';
import { environment } from '../../../environments/environment';

describe('ScreenCaptureService', () => {
  const base = `${environment.apiUrl}/screens`;
  let service: ScreenCaptureService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ScreenCaptureService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts capture with the device id', () => {
    service.capture('emulator-5554').subscribe(res => expect(res.captureId).toBe('abc'));
    const req = http.expectOne(`${base}/capture`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ deviceId: 'emulator-5554' });
    req.flush({ captureId: 'abc', status: 'SUCCESS' });
  });
});
