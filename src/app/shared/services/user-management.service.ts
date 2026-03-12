import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UserProfile } from './auth.service';

export interface CreateUserRequest {
  username: string;
  email: string;
  password: string;
  role: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;
  provider: string;
}

export interface CreateUserRequestBody {
  data: CreateUserRequest;
}

export interface UsersListResponse {
  count: number;
  rows: UserProfile[];
}

@Injectable({
  providedIn: 'root'
})
export class UserManagementService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/users`;

  // Get all users (admin only)
  getAllUsers(): Observable<UsersListResponse> {
    return this.http.get<UsersListResponse>(this.API_URL);
  }

  // Create new user (admin only)
  createUser(userData: CreateUserRequest): Observable<UserProfile> {
    const requestBody: CreateUserRequestBody = {
      data: userData
    };
    return this.http.post<UserProfile>(this.API_URL, requestBody);
  }

  // Update user (admin only)
  updateUser(userId: number, userData: Partial<CreateUserRequest>): Observable<UserProfile> {
    const requestBody = {
      data: userData
    };
    return this.http.put<UserProfile>(`${this.API_URL}/${userId}`, requestBody);
  }

  // Delete user (admin only)
  deleteUser(userId: number): Observable<void> {
    return this.http.delete<void>(`${this.API_URL}/${userId}`);
  }

  // Get user by ID
  getUserById(userId: number): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${this.API_URL}/${userId}`);
  }
}