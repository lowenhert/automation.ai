import { Injectable, inject } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { Observable, map } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';

@Injectable({
  providedIn: 'root'
})
export class AdminGuard implements CanActivate {
  private authService = inject(AuthService);
  private router = inject(Router);
  private notificationService = inject(NotificationService);

  canActivate(): Observable<boolean> {
    return this.authService.userProfile$.pipe(
      map(userProfile => {
        if (!userProfile) {
          // If no user profile is loaded, try to fetch it
          this.authService.fetchUserProfile().subscribe();
          return false;
        }

        if (userProfile.role === 'ADMIN') {
          return true;
        } else {
          this.notificationService.showError(
            'Access Denied', 
            'You do not have permission to access this page. Admin privileges required.'
          );
          this.router.navigate(['/']);
          return false;
        }
      })
    );
  }
}