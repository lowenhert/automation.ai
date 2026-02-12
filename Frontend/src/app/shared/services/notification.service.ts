import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface Notification {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  title: string;
  message: string;
  autoClose?: boolean;
  duration?: number;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private notificationsSubject = new BehaviorSubject<Notification[]>([]);
  public notifications$ = this.notificationsSubject.asObservable();
  
  private nextId = 1;

  constructor() { }

  private addNotification(notification: Omit<Notification, 'id'>): void {
    const newNotification: Notification = {
      ...notification,
      id: `notification-${this.nextId++}`,
      autoClose: notification.autoClose ?? true,
      duration: notification.duration ?? 5000
    };

    const currentNotifications = this.notificationsSubject.value;
    this.notificationsSubject.next([...currentNotifications, newNotification]);

    // Auto-remove notification if autoClose is enabled
    if (newNotification.autoClose && newNotification.duration) {
      setTimeout(() => {
        this.removeNotification(newNotification.id);
      }, newNotification.duration);
    }
  }

  showSuccess(title: string, message: string, autoClose: boolean = true, duration: number = 5000): void {
    this.addNotification({
      type: 'success',
      title,
      message,
      autoClose,
      duration
    });
  }

  showError(title: string, message: string, autoClose: boolean = true, duration: number = 7000): void {
    this.addNotification({
      type: 'error',
      title,
      message,
      autoClose,
      duration
    });
  }

  showWarning(title: string, message: string, autoClose: boolean = true, duration: number = 6000): void {
    this.addNotification({
      type: 'warning',
      title,
      message,
      autoClose,
      duration
    });
  }

  showInfo(title: string, message: string, autoClose: boolean = true, duration: number = 5000): void {
    this.addNotification({
      type: 'info',
      title,
      message,
      autoClose,
      duration
    });
  }

  removeNotification(id: string): void {
    const currentNotifications = this.notificationsSubject.value;
    this.notificationsSubject.next(
      currentNotifications.filter(notification => notification.id !== id)
    );
  }

  clearAllNotifications(): void {
    this.notificationsSubject.next([]);
  }

  // Auth-specific notification methods
  showUnauthorizedError(): void {
    this.showError(
      'Authentication Failed',
      'Invalid credentials. Please check your username and password.',
      true,
      7000
    );
  }

  showAccountInactiveError(): void {
    this.showError(
      'Account Not Active',
      'Your account is not active or does not exist. Please contact support for assistance.',
      false // Don't auto-close for important messages
    );
  }

  showLoginSuccess(username: string): void {
    this.showSuccess(
      'Login Successful',
      `Welcome back, ${username}!`,
      true,
      3000
    );
  }
}