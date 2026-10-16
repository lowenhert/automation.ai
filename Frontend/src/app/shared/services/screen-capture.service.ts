import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ScreenCapture {
  captureId: string;
  deviceId: string;
  imageUrl: string;
  fileName: string;
  timestamp: string;
  width: number;
  height: number;
  resolution: string;
  sizeBytes: number;
  status: string;
  analysisStatus: 'SUCCESS' | 'FAILED';
  analysisUrl: string | null;
  ocrStatus: 'SUCCESS' | 'FAILED' | null;
  textRegionCount: number;
  analysisError: string | null;
}

@Injectable({ providedIn: 'root' })
export class ScreenCaptureService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/screens`;

  capture(deviceId: string): Observable<ScreenCapture> {
    return this.http.post<ScreenCapture>(`${this.API_URL}/capture`, { deviceId });
  }
}
