import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, tap, catchError, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { NotificationService } from './notification.service';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  username: string;
  role: string;
  firstName: string;
  lastName: string;
}

export interface User {
  username: string;
  token: string;
  role: string;
  firstName: string;
  lastName: string;
}

export interface UserProfile {
  id: number;
  username: string;
  email: string;
  role: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;
  provider: string;
  active: boolean;
  enabled: boolean;
  emailVerified: boolean;
  createdAt: string;
  updatedAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private notificationService = inject(NotificationService);
  
  private readonly API_URL = `${environment.apiUrl}/auth`;
  private readonly TOKEN_KEY = 'auth_token';
  private readonly USER_KEY = 'auth_user';

  private isAuthenticatedSubject = new BehaviorSubject<boolean>(this.hasValidToken());
  public isAuthenticated$ = this.isAuthenticatedSubject.asObservable();

  private currentUserSubject = new BehaviorSubject<User | null>(this.getCurrentUser());
  public currentUser$ = this.currentUserSubject.asObservable();

  private userProfileSubject = new BehaviorSubject<UserProfile | null>(null);
  public userProfile$ = this.userProfileSubject.asObservable();

  constructor() {
    // Check if token is expired on service initialization
    if (this.hasValidToken() && this.isTokenExpired()) {
      this.logout();
    } else if (this.hasValidToken() && !this.isTokenExpired()) {
      // If logged in but role is missing (old session), fetch profile to get role info
      const user = this.getCurrentUser();
      if (!user?.role) {
        this.fetchUserProfile().subscribe({
          next: (profile) => {
            // Update stored user with role/name from profile
            const updatedUser: User = {
              username: profile.username,
              token: this.getToken()!,
              role: profile.role,
              firstName: profile.firstName,
              lastName: profile.lastName
            };
            localStorage.setItem(this.USER_KEY, JSON.stringify(updatedUser));
            this.currentUserSubject.next(updatedUser);
          },
          error: () => {} // silently fail - user can re-login
        });
      }
    }
  }

  login(username: string, password: string): Observable<LoginResponse> {
    const loginRequest: LoginRequest = { username, password };
    
    return this.http.post<LoginResponse>(`${this.API_URL}/login`, loginRequest).pipe(
      tap(response => {
        this.setAuthData(response);
        this.isAuthenticatedSubject.next(true);
        this.currentUserSubject.next({
          username: response.username,
          token: response.token,
          role: response.role,
          firstName: response.firstName,
          lastName: response.lastName
        });

        // Handle redirect after successful login
        const redirectUrl = localStorage.getItem('redirectUrl');
        if (redirectUrl) {
          localStorage.removeItem('redirectUrl');
          this.router.navigate([redirectUrl]);
        } else {
          this.router.navigate(['/']); // Default to dashboard
        }
      }),
      catchError((error: HttpErrorResponse) => {
        this.handleLoginError(error);
        return throwError(() => error);
      })
    );
  }

  private handleLoginError(error: HttpErrorResponse): void {
    console.error('Login error:', error);
    
    switch (error.status) {
      case 401:
        // Unauthorized - invalid credentials
        this.notificationService.showUnauthorizedError();
        break;
      case 403:
        // Forbidden - user not active or account doesn't exist
        this.notificationService.showAccountInactiveError();
        break;
      case 0:
        // Network error or server unreachable
        this.notificationService.showError(
          'Connection Error',
          'Unable to connect to the server. Please check your internet connection and try again.'
        );
        break;
      case 500:
        // Internal server error
        this.notificationService.showError(
          'Server Error',
          'An internal server error occurred. Please try again later.'
        );
        break;
      default:
        // Generic error for other status codes
        const errorMessage = error.error?.message || error.message || 'An unexpected error occurred during login.';
        this.notificationService.showError(
          'Login Failed',
          errorMessage
        );
        break;
    }
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.isAuthenticatedSubject.next(false);
    this.currentUserSubject.next(null);
    this.router.navigate(['/signin']);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    return this.hasValidToken() && !this.isTokenExpired();
  }

  private setAuthData(response: LoginResponse): void {
    localStorage.setItem(this.TOKEN_KEY, response.token);
    localStorage.setItem(this.USER_KEY, JSON.stringify({
      username: response.username,
      token: response.token,
      role: response.role,
      firstName: response.firstName,
      lastName: response.lastName
    }));
  }

  private hasValidToken(): boolean {
    const token = localStorage.getItem(this.TOKEN_KEY);
    return !!token;
  }

  private getCurrentUser(): User | null {
    const userStr = localStorage.getItem(this.USER_KEY);
    if (userStr) {
      try {
        return JSON.parse(userStr);
      } catch {
        return null;
      }
    }
    return null;
  }

  private isTokenExpired(): boolean {
    const token = this.getToken();
    if (!token) return true;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const currentTime = Math.floor(Date.now() / 1000);
      return payload.exp < currentTime;
    } catch (error) {
      console.error('Error parsing token:', error);
      return true;
    }
  }

  // Method to refresh user data if needed
  refreshUser(): void {
    if (this.isAuthenticated()) {
      const user = this.getCurrentUser();
      this.currentUserSubject.next(user);
    }
  }

  // Fetch user profile from API
  fetchUserProfile(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${environment.apiUrl}/users/me`, {
      headers: {
        'Authorization': `Bearer ${this.getToken()}`
      }
    }).pipe(
      tap(profile => {
        this.userProfileSubject.next(profile);
      })
    );
  }

  // Get user profile from subject
  getUserProfile(): UserProfile | null {
    return this.userProfileSubject.value;
  }

  // Role helpers
  getUserRole(): string {
    const user = this.getCurrentUser();
    if (user?.role) return user.role;
    // Fallback to fetched profile
    const profile = this.userProfileSubject.value;
    return profile?.role || '';
  }

  isAdmin(): boolean {
    return this.getUserRole().toUpperCase() === 'ADMIN';
  }

  getCurrentUsername(): string {
    const user = this.getCurrentUser();
    return user?.username || '';
  }

  getCurrentFullName(): string {
    const user = this.getCurrentUser();
    if (user?.firstName) {
      return ((user.firstName || '') + ' ' + (user.lastName || '')).trim();
    }
    // Fallback to fetched profile
    const profile = this.userProfileSubject.value;
    if (profile?.firstName) {
      return ((profile.firstName || '') + ' ' + (profile.lastName || '')).trim();
    }
    return '';
  }
}