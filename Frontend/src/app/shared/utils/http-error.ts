import { HttpErrorResponse } from '@angular/common/http';

/** User-facing message for a failed backend call. */
export function httpErrorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'Cannot reach the backend server';
    if (err.status === 401) return 'Your session has expired. Please sign in again.';
    if (typeof err.error?.message === 'string') return err.error.message;
    return `Request failed (${err.status} ${err.statusText})`;
  }
  return 'Unexpected error';
}
