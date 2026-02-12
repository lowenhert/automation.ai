# Authentication System Implementation

## Overview
This authentication system provides comprehensive security for your Angular application with JWT token-based authentication, route guards, and automatic token refresh handling.

## Components

### 1. AuthService (`shared/services/auth.service.ts`)
- Handles login/logout operations
- Manages JWT tokens in localStorage
- Provides authentication state observables
- Automatic token expiration checking
- Redirect handling after login

### 2. AuthGuard (`shared/guards/auth.guard.ts`)
- **authGuard**: Protects routes requiring authentication
- **guestGuard**: Protects routes that should only be accessible to non-authenticated users

### 3. AuthInterceptor (`shared/interceptors/auth.interceptor.ts`)
- Automatically adds JWT token to HTTP requests
- Handles 401 responses by automatically logging out users

### 4. Updated Components
- **SigninFormComponent**: Integrated with AuthService for login
- **UserDropdownComponent**: Added logout functionality
- **Routes**: Protected with appropriate guards

## Usage

### Login Process
```typescript
// The signin form automatically handles this
this.authService.login(username, password).subscribe({
  next: (response) => {
    // User is automatically logged in and redirected
  },
  error: (error) => {
    // Handle login errors
  }
});
```

### Logout
```typescript
this.authService.logout(); // Automatically redirects to signin
```

### Check Authentication Status
```typescript
// In components
this.authService.isAuthenticated$ // Observable
// Or
this.authService.isAuthenticated() // Synchronous check
```

### Get Current User
```typescript
this.authService.currentUser$ // Observable with user data
```

## Route Protection

### Protected Routes (Require Authentication)
All routes under the main layout are protected:
```typescript
{
  path: '',
  component: AppLayoutComponent,
  canActivate: [authGuard], // Protects all child routes
  children: [
    // All dashboard routes
  ]
}
```

### Guest Routes (Accessible only when not authenticated)
```typescript
{
  path: 'signin',
  component: SignInComponent,
  canActivate: [guestGuard] // Prevents access if already logged in
}
```

## API Integration

### Backend Requirements
Your Spring Boot backend should:
1. Accept POST requests to `/api/auth/login` with:
   ```json
   {
     "username": "admin",
     "password": "Wipro@123"
   }
   ```

2. Return response:
   ```json
   {
     "token": "eyJhbGciOiJIUzI1NiJ9...",
     "username": "admin"
   }
   ```

3. Accept JWT tokens in Authorization header: `Bearer <token>`

### Environment Configuration
Update `src/environments/environment.ts` and `environment.prod.ts`:
```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api'
};
```

## Features

### Automatic Token Management
- JWT tokens are stored in localStorage
- Automatic token expiration checking
- Automatic logout when token expires
- Token included in all HTTP requests

### Redirect Handling
- Remembers attempted URL before login
- Redirects to intended page after successful authentication
- Default redirect to dashboard if no intended URL

### Error Handling
- Display user-friendly error messages
- Automatic logout on 401 responses
- Loading states during authentication

## Security Considerations

1. **Token Storage**: Currently uses localStorage. Consider sessionStorage for increased security.
2. **HTTPS**: Always use HTTPS in production
3. **Token Expiration**: Implement refresh tokens for better security
4. **CORS**: Configure CORS properly on your backend

## Testing

### Test Login
Use the credentials configured in your Spring Boot backend:
- Username: `admin`
- Password: `Wipro@123`

### Test Protected Routes
1. Try accessing any dashboard route without logging in - should redirect to signin
2. Log in successfully - should redirect to dashboard or intended page
3. Try accessing signin page when logged in - should redirect to dashboard

## Troubleshooting

### Common Issues

1. **CORS Errors**: Ensure your Spring Boot backend has CORS configured
2. **401 Errors**: Check that JWT token is being sent correctly
3. **Token Expiration**: Implement refresh token logic if needed
4. **Redirect Issues**: Check localStorage for 'redirectUrl' key

### Development Setup
1. Ensure your Spring Boot backend is running on `http://localhost:8080`
2. Update environment files if using different ports
3. Check browser console for authentication-related errors