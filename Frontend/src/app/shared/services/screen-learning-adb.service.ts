import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type AdbConnectionType = 'EMULATOR' | 'NETWORK' | 'USB';

export interface AdbDevice {
  serial: string;
  state: string;
  connectionType: AdbConnectionType;
  model: string | null;
  product: string | null;
  deviceName: string | null;
  transportId: string | null;
  manufacturer: string | null;
  androidVersion: string | null;
  sdkVersion: string | null;
  resolution: string | null;
  infoError: string | null;
}

export interface AdbActionResponse {
  success: boolean;
  serial: string;
  message: string;
}

/** Splits user input such as "192.168.1.10:5555" into host and port; the port field is used when none is typed. */
export function parseAdbTarget(input: string, portField: number | null): { host: string; port: number | null } {
  const value = input.trim();
  const match = /^([^:\s]+):(\d{1,5})$/.exec(value);
  if (match) {
    return { host: match[1], port: Number(match[2]) };
  }
  return { host: value, port: portField };
}

@Injectable({ providedIn: 'root' })
export class ScreenLearningAdbService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/screen-learning/adb`;

  getDevices(): Observable<AdbDevice[]> {
    return this.http.get<AdbDevice[]>(`${this.API_URL}/devices`);
  }

  getDeviceInfo(serial: string): Observable<AdbDevice> {
    const params = new HttpParams().set('serial', serial);
    return this.http.get<AdbDevice>(`${this.API_URL}/devices/info`, { params });
  }

  connect(host: string, port?: number | null): Observable<AdbActionResponse> {
    return this.http.post<AdbActionResponse>(`${this.API_URL}/connect`, { host, port: port ?? null });
  }

  disconnect(serial: string): Observable<AdbActionResponse> {
    return this.http.post<AdbActionResponse>(`${this.API_URL}/disconnect`, { serial });
  }
}
