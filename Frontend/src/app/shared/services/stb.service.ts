import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, retry } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface STBBox {
  uuid: string;
  vendor: string;
  model: string;
  resolution: string;
  stbNo: number;
}

export interface STBAutomationReport {
  id?: number;
  stbNo: number;
  boxName: string;
  reportDate: string;
  fileName: string;
  contentType?: string;
  totalTests: number;
  passed: number;
  failed: number;
  uploadedAt?: string;
}

export interface StbTestCase {
  id?: number;
  moduleName: string;
  testName: string;
  description?: string;
  category?: string;
  totalSteps?: number;
  enabled?: boolean;
  createdAt?: string;
}

export interface StbExecution {
  id?: number;
  executionName: string;
  stbNo: number;
  boxName?: string;
  status: string;
  totalTests: number;
  passed: number;
  failed: number;
  skipped: number;
  triggeredBy?: string;
  startTime?: string;
  endTime?: string;
  durationSeconds?: number;
  notes?: string;
  createdAt?: string;
  results?: StbExecutionResult[];
}

export interface StbExecutionResult {
  id?: number;
  executionId: number;
  testCaseId?: number;
  moduleName?: string;
  stepNumber?: number;
  stepName?: string;
  status: string;
  errorMessage?: string;
  durationMs?: number;
  screenshotPath?: string;
  timestamp?: string;
}

@Injectable({
  providedIn: 'root'
})
export class StbService {
  private apiUrl = `${environment.apiUrl}/stbs`;
  private reportsUrl = `${environment.apiUrl}/stb-reports`;
  private execUrl = `${environment.apiUrl}/stb-executions`;

  constructor(private http: HttpClient) { }

  /**
   * Fetch all STB boxes from the API
   * @returns Observable<STBBox[]>
   */
  getStbBoxes(): Observable<STBBox[]> {
    return this.http.get<STBBox[]>(this.apiUrl)
      .pipe(
        retry(2), // Retry failed requests up to 2 times
        catchError(this.handleError)
      );
  }

  /**
   * Get a specific STB box by UUID
   * @param uuid - The UUID of the STB box
   * @returns Observable<STBBox>
   */
  getStbBoxById(uuid: string): Observable<STBBox> {
    return this.http.get<STBBox>(`${this.apiUrl}/${uuid}`)
      .pipe(
        retry(2),
        catchError(this.handleError)
      );
  }

  /**
   * Get STB boxes filtered by vendor
   * @param vendor - The vendor name
   * @returns Observable<STBBox[]>
   */
  getStbBoxesByVendor(vendor: string): Observable<STBBox[]> {
    return this.http.get<STBBox[]>(`${this.apiUrl}?vendor=${encodeURIComponent(vendor)}`)
      .pipe(
        retry(2),
        catchError(this.handleError)
      );
  }

  /**
   * Get STB boxes filtered by resolution
   * @param resolution - The resolution (HD/SD)
   * @returns Observable<STBBox[]>
   */
  getStbBoxesByResolution(resolution: string): Observable<STBBox[]> {
    return this.http.get<STBBox[]>(`${this.apiUrl}?resolution=${encodeURIComponent(resolution)}`)
      .pipe(
        retry(2),
        catchError(this.handleError)
      );
  }

  /**
   * Get automation reports for a specific STB box
   */
  getReportsByBox(stbNo: number): Observable<STBAutomationReport[]> {
    return this.http.get<STBAutomationReport[]>(`${this.reportsUrl}/box/${stbNo}`)
      .pipe(retry(2), catchError(this.handleError));
  }

  /**
   * Get all automation reports
   */
  getAllReports(): Observable<STBAutomationReport[]> {
    return this.http.get<STBAutomationReport[]>(this.reportsUrl)
      .pipe(retry(2), catchError(this.handleError));
  }

  /**
   * Upload an Excel report for a box
   */
  uploadReport(stbNo: number, boxName: string, file: File): Observable<STBAutomationReport> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('stbNo', stbNo.toString());
    formData.append('boxName', boxName);
    return this.http.post<STBAutomationReport>(`${this.reportsUrl}/upload`, formData)
      .pipe(catchError(this.handleError));
  }

  /**
   * Download a report file
   */
  downloadReport(id: number): Observable<Blob> {
    return this.http.get(`${this.reportsUrl}/${id}/download`, { responseType: 'blob' })
      .pipe(catchError(this.handleError));
  }

  /**
   * Delete a report
   */
  deleteReport(id: number): Observable<void> {
    return this.http.delete<void>(`${this.reportsUrl}/${id}`)
      .pipe(catchError(this.handleError));
  }

  // ─── Test Cases ─────────────────────────────────────────────────────────

  getTestCases(): Observable<StbTestCase[]> {
    return this.http.get<StbTestCase[]>(`${this.execUrl}/test-cases`)
      .pipe(retry(2), catchError(this.handleError));
  }

  getEnabledTestCases(): Observable<StbTestCase[]> {
    return this.http.get<StbTestCase[]>(`${this.execUrl}/test-cases/enabled`)
      .pipe(retry(2), catchError(this.handleError));
  }

  createTestCase(tc: { moduleName: string; testName: string; description?: string; category?: string; totalSteps?: number }): Observable<StbTestCase> {
    return this.http.post<StbTestCase>(`${this.execUrl}/test-cases`, tc)
      .pipe(catchError(this.handleError));
  }

  toggleTestCase(id: number): Observable<StbTestCase> {
    return this.http.patch<StbTestCase>(`${this.execUrl}/test-cases/${id}/toggle`, {})
      .pipe(catchError(this.handleError));
  }

  deleteTestCase(id: number): Observable<void> {
    return this.http.delete<void>(`${this.execUrl}/test-cases/${id}`)
      .pipe(catchError(this.handleError));
  }

  // ─── Executions ─────────────────────────────────────────────────────────

  getExecutions(): Observable<StbExecution[]> {
    return this.http.get<StbExecution[]>(this.execUrl)
      .pipe(retry(2), catchError(this.handleError));
  }

  getExecutionsByStb(stbNo: number): Observable<StbExecution[]> {
    return this.http.get<StbExecution[]>(`${this.execUrl}/box/${stbNo}`)
      .pipe(retry(2), catchError(this.handleError));
  }

  getExecutionDetail(id: number): Observable<StbExecution> {
    return this.http.get<StbExecution>(`${this.execUrl}/${id}`)
      .pipe(retry(2), catchError(this.handleError));
  }

  createExecution(req: { executionName: string; stbNo: number; boxName?: string; testCaseIds: number[]; triggeredBy?: string; notes?: string }): Observable<StbExecution> {
    return this.http.post<StbExecution>(this.execUrl, req)
      .pipe(catchError(this.handleError));
  }

  updateExecutionStatus(id: number, status: string): Observable<StbExecution> {
    return this.http.patch<StbExecution>(`${this.execUrl}/${id}/status?status=${encodeURIComponent(status)}`, {})
      .pipe(catchError(this.handleError));
  }

  deleteExecution(id: number): Observable<void> {
    return this.http.delete<void>(`${this.execUrl}/${id}`)
      .pipe(catchError(this.handleError));
  }

  getExecutionResults(executionId: number): Observable<StbExecutionResult[]> {
    return this.http.get<StbExecutionResult[]>(`${this.execUrl}/${executionId}/results`)
      .pipe(retry(2), catchError(this.handleError));
  }

  /**
   * Trigger an execution — starts the Python test runner on the backend
   */
  triggerExecution(executionId: number, deviceId: string, modules: string[]): Observable<any> {
    return this.http.post(`${this.execUrl}/${executionId}/trigger`, { deviceId, modules })
      .pipe(catchError(this.handleError));
  }

  /**
   * Handle HTTP errors
   * @param error - The error response
   * @returns Observable<never>
   */
  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'An unknown error occurred';

    if (error.error instanceof ErrorEvent) {
      // Client-side or network error
      errorMessage = `Client Error: ${error.error.message}`;
    } else {
      // Backend error
      switch (error.status) {
        case 0:
          errorMessage = 'Unable to connect to the server. Please check your internet connection.';
          break;
        case 400:
          errorMessage = 'Bad request. Please check your input.';
          break;
        case 401:
          errorMessage = 'Unauthorized. Please log in again.';
          break;
        case 403:
          errorMessage = 'Forbidden. You do not have permission to access this resource.';
          break;
        case 404:
          errorMessage = 'Resource not found.';
          break;
        case 500:
          errorMessage = 'Internal server error. Please try again later.';
          break;
        case 503:
          errorMessage = 'Service unavailable. Please try again later.';
          break;
        default:
          errorMessage = `Server Error: ${error.status} - ${error.message}`;
      }
    }

    console.error('STB Service Error:', errorMessage, error);
    return throwError(() => new Error(errorMessage));
  }
}