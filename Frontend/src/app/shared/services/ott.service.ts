import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, retry } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface OttAutomationTestResult {
  id?: number;
  ottAppName: string;
  contentName: string;
  playbackTimeSeconds: number;
  timestamp: string;
  deviceId: string;
  status: string;
  screenshotsFolder?: string;
  errorType?: string | null;
  errorMessage?: string | null;
  failedStep?: number | null;
}

@Injectable({
  providedIn: 'root'
})
export class OttService {
  private apiUrl = `${environment.apiUrl}/ott-results`;

  constructor(private http: HttpClient) {}

  save(result: OttAutomationTestResult): Observable<OttAutomationTestResult> {
    return this.http.post<OttAutomationTestResult>(this.apiUrl, result)
      .pipe(catchError(this.handleError));
  }

  getAll(): Observable<OttAutomationTestResult[]> {
    return this.http.get<OttAutomationTestResult[]>(this.apiUrl)
      .pipe(retry(2), catchError(this.handleError));
  }

  getById(id: number): Observable<OttAutomationTestResult> {
    return this.http.get<OttAutomationTestResult>(`${this.apiUrl}/${id}`)
      .pipe(retry(2), catchError(this.handleError));
  }

  getByApp(app: string): Observable<OttAutomationTestResult[]> {
    return this.http.get<OttAutomationTestResult[]>(`${this.apiUrl}/app/${encodeURIComponent(app)}`)
      .pipe(retry(2), catchError(this.handleError));
  }

  getByStatus(status: string): Observable<OttAutomationTestResult[]> {
    return this.http.get<OttAutomationTestResult[]>(`${this.apiUrl}/status/${encodeURIComponent(status)}`)
      .pipe(retry(2), catchError(this.handleError));
  }

  getByDevice(device: string): Observable<OttAutomationTestResult[]> {
    return this.http.get<OttAutomationTestResult[]>(`${this.apiUrl}/device/${encodeURIComponent(device)}`)
      .pipe(retry(2), catchError(this.handleError));
  }

  filter(app: string, status: string): Observable<OttAutomationTestResult[]> {
    return this.http.get<OttAutomationTestResult[]>(
      `${this.apiUrl}/filter?app=${encodeURIComponent(app)}&status=${encodeURIComponent(status)}`
    ).pipe(retry(2), catchError(this.handleError));
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`)
      .pipe(catchError(this.handleError));
  }

  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'An unknown error occurred';
    if (error.error instanceof ErrorEvent) {
      errorMessage = `Client error: ${error.error.message}`;
    } else {
      errorMessage = `Server error ${error.status}: ${error.message}`;
    }
    console.error(errorMessage);
    return throwError(() => new Error(errorMessage));
  }
}
