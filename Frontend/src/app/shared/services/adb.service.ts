import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AdbResponse {
  success: boolean;
  output?: string;
  error?: string;
}

@Injectable({
  providedIn: 'root'
})
export class AdbService {
  private apiUrl = `${environment.apiUrl}/adb`;

  constructor(private http: HttpClient) {}

  connect(ip: string): Observable<AdbResponse> {
    return this.http.post<AdbResponse>(`${this.apiUrl}/connect`, { ip });
  }

  disconnect(ip: string): Observable<AdbResponse> {
    return this.http.post<AdbResponse>(`${this.apiUrl}/disconnect`, { ip });
  }

  sendKeyEvent(ip: string, keycode: string): Observable<AdbResponse> {
    return this.http.post<AdbResponse>(`${this.apiUrl}/key`, { ip, keycode });
  }

  sendText(ip: string, text: string): Observable<AdbResponse> {
    return this.http.post<AdbResponse>(`${this.apiUrl}/text`, { ip, text });
  }

  runCommand(ip: string, command: string): Observable<AdbResponse> {
    return this.http.post<AdbResponse>(`${this.apiUrl}/command`, { ip, command });
  }
}
