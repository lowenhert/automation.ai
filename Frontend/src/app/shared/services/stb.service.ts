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

@Injectable({
  providedIn: 'root'
})
export class StbService {
  private apiUrl = 'http://localhost:8080/api/stbs';

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