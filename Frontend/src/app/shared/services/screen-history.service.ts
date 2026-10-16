import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface BoundingBox {
  x: number;
  y: number;
  width: number;
  height: number;
}

/** Text found by OCR. Not a verified interactive/clickable UI element. */
export interface ScreenTextRegion {
  index: number;
  type: 'OCR_TEXT';
  text: string;
  confidence: number | null;
  bbox: BoundingBox;
}

export interface ScreenSummary {
  id: string;
  deviceSerial: string;
  deviceModel: string | null;
  deviceType: string | null;
  capturedAt: string;
  width: number;
  height: number;
  resolution: string;
  phash: string | null;
  analysisStatus: 'SUCCESS' | 'FAILED';
  ocrStatus: 'SUCCESS' | 'FAILED' | null;
  textRegionCount: number;
  textPreview?: string;
  imageUrl: string;
  matchedTexts?: string[];
}

export interface ScreenDetail extends Omit<ScreenSummary, 'textPreview' | 'matchedTexts'> {
  sizeBytes: number | null;
  sha256: string | null;
  dhash: string | null;
  ahash: string | null;
  analysisError: string | null;
  textRegions: ScreenTextRegion[];
  note: string;
}

@Injectable({ providedIn: 'root' })
export class ScreenHistoryService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/screens`;

  list(page: number, size: number, deviceId?: string): Observable<PageResponse<ScreenSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (deviceId) params = params.set('deviceId', deviceId);
    return this.http.get<PageResponse<ScreenSummary>>(this.API_URL, { params });
  }

  search(text: string, page: number, size: number): Observable<PageResponse<ScreenSummary>> {
    const params = new HttpParams().set('text', text).set('page', page).set('size', size);
    return this.http.get<PageResponse<ScreenSummary>>(`${this.API_URL}/search`, { params });
  }

  get(id: string): Observable<ScreenDetail> {
    return this.http.get<ScreenDetail>(`${this.API_URL}/${encodeURIComponent(id)}`);
  }

  /** Fetched as a blob so the auth interceptor can attach the JWT (a plain <img src> cannot). */
  getImage(id: string): Observable<Blob> {
    return this.http.get(`${this.API_URL}/${encodeURIComponent(id)}/image`, { responseType: 'blob' });
  }
}
