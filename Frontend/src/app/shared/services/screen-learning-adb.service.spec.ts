import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ScreenLearningAdbService, parseAdbTarget } from './screen-learning-adb.service';
import { environment } from '../../../environments/environment';

describe('ScreenLearningAdbService', () => {
  const base = `${environment.apiUrl}/screen-learning/adb`;
  let service: ScreenLearningAdbService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ScreenLearningAdbService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists devices', () => {
    service.getDevices().subscribe(devices => expect(devices.length).toBe(1));
    const req = http.expectOne(`${base}/devices`);
    expect(req.request.method).toBe('GET');
    req.flush([{ serial: 'emulator-5554', state: 'device', connectionType: 'EMULATOR' }]);
  });

  it('requests device info with an encoded serial query param', () => {
    service.getDeviceInfo('192.168.1.10:5555').subscribe();
    const req = http.expectOne(r => r.url === `${base}/devices/info`);
    expect(req.request.params.get('serial')).toBe('192.168.1.10:5555');
    req.flush({});
  });

  it('posts connect with host and port', () => {
    service.connect('192.168.1.10', 5555).subscribe();
    const req = http.expectOne(`${base}/connect`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ host: '192.168.1.10', port: 5555 });
    req.flush({ success: true, serial: '192.168.1.10:5555', message: 'connected' });
  });

  it('posts disconnect with serial', () => {
    service.disconnect('192.168.1.10:5555').subscribe();
    const req = http.expectOne(`${base}/disconnect`);
    expect(req.request.body).toEqual({ serial: '192.168.1.10:5555' });
    req.flush({ success: true, serial: '192.168.1.10:5555', message: 'disconnected' });
  });
});

describe('parseAdbTarget', () => {
  it('splits host:port typed into the host field', () => {
    expect(parseAdbTarget(' 192.168.1.10:5037 ', 5555)).toEqual({ host: '192.168.1.10', port: 5037 });
  });

  it('uses the port field when no port is typed', () => {
    expect(parseAdbTarget('192.168.1.10', 5555)).toEqual({ host: '192.168.1.10', port: 5555 });
    expect(parseAdbTarget('stb-01.lab', null)).toEqual({ host: 'stb-01.lab', port: null });
  });

  it('leaves malformed input for the backend to reject', () => {
    expect(parseAdbTarget('1.2.3.4:abc', 5555)).toEqual({ host: '1.2.3.4:abc', port: 5555 });
  });
});
