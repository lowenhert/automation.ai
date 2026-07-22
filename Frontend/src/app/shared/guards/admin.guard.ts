import { Injectable, inject } from '@angular/core';
import { CanActivate, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';

@Injectable({
  providedIn: 'root'
})
export class AdminGuard implements CanActivate {
  private authService = inject(AuthService);
  private router = inject(Router);
  private notificationService = inject(NotificationService);

  canActivate(): boolean {
    if (this.authService.isAdmin()) {
      return true;
    }

    this.notificationService.showError(
      'Access Denied', 
      'You do not have permission to access this page. Admin privileges required.'
    );
    this.router.navigate(['/']);
    return false;
  }
}