import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs';
import { NotificationService, Notification } from '../../../services/notification.service';

@Component({
  selector: 'app-notification-container',
  imports: [CommonModule],
  template: `
    <div class="fixed top-4 right-4 z-[99999] space-y-3 max-w-sm">
      @for (notification of notifications; track notification.id) {
        <div 
          class="transform transition-all duration-300 ease-in-out rounded-lg border shadow-lg"
          [ngClass]="getNotificationClasses(notification.type)"
          role="alert"
        >
          <div class="flex items-start p-4">
            <div class="flex-shrink-0">
              <div [ngClass]="getIconClasses(notification.type)" [innerHTML]="getIcon(notification.type)"></div>
            </div>
            <div class="ml-3 flex-1">
              <h3 class="text-sm font-semibold" [ngClass]="getTitleClasses(notification.type)">
                {{ notification.title }}
              </h3>
              <p class="mt-1 text-sm" [ngClass]="getMessageClasses(notification.type)">
                {{ notification.message }}
              </p>
            </div>
            <div class="ml-4 flex-shrink-0">
              <button
                type="button"
                (click)="removeNotification(notification.id)"
                class="inline-flex rounded-md text-gray-400 hover:text-gray-600 focus:outline-none focus:ring-2 focus:ring-offset-2"
                [ngClass]="getCloseButtonClasses(notification.type)"
              >
                <span class="sr-only">Close</span>
                <svg class="h-5 w-5" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                  <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .notification-enter {
      transform: translateX(100%);
      opacity: 0;
    }
    .notification-enter-active {
      transform: translateX(0);
      opacity: 1;
    }
    .notification-leave {
      transform: translateX(0);
      opacity: 1;
    }
    .notification-leave-active {
      transform: translateX(100%);
      opacity: 0;
    }
  `]
})
export class NotificationContainerComponent implements OnInit, OnDestroy {
  private notificationService = inject(NotificationService);
  
  notifications: Notification[] = [];
  private subscription: Subscription = new Subscription();

  ngOnInit(): void {
    this.subscription.add(
      this.notificationService.notifications$.subscribe(notifications => {
        this.notifications = notifications;
      })
    );
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  removeNotification(id: string): void {
    this.notificationService.removeNotification(id);
  }

  getNotificationClasses(type: string): string {
    const baseClasses = 'bg-white dark:bg-gray-800 border';
    const typeClasses = {
      'success': 'border-success-200 dark:border-success-500/30',
      'error': 'border-error-200 dark:border-error-500/30',
      'warning': 'border-warning-200 dark:border-warning-500/30',
      'info': 'border-blue-light-200 dark:border-blue-light-500/30'
    };
    return `${baseClasses} ${typeClasses[type as keyof typeof typeClasses] || typeClasses.info}`;
  }

  getIconClasses(type: string): string {
    const typeClasses = {
      'success': 'text-success-500',
      'error': 'text-error-500',
      'warning': 'text-warning-500',
      'info': 'text-blue-light-500'
    };
    return typeClasses[type as keyof typeof typeClasses] || typeClasses.info;
  }

  getTitleClasses(type: string): string {
    return 'text-gray-800 dark:text-white';
  }

  getMessageClasses(type: string): string {
    return 'text-gray-600 dark:text-gray-400';
  }

  getCloseButtonClasses(type: string): string {
    const typeClasses = {
      'success': 'focus:ring-success-500',
      'error': 'focus:ring-error-500',
      'warning': 'focus:ring-warning-500',
      'info': 'focus:ring-blue-light-500'
    };
    return typeClasses[type as keyof typeof typeClasses] || typeClasses.info;
  }

  getIcon(type: string): string {
    const icons = {
      success: `<svg class="h-5 w-5" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
        <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd" />
      </svg>`,
      error: `<svg class="h-5 w-5" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
        <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clip-rule="evenodd" />
      </svg>`,
      warning: `<svg class="h-5 w-5" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
        <path fill-rule="evenodd" d="M8.485 3.495c.673-1.167 2.357-1.167 3.03 0l6.28 10.875c.673 1.167-.17 2.625-1.516 2.625H3.72c-1.347 0-2.189-1.458-1.515-2.625L8.485 3.495zM10 6a.75.75 0 01.75.75v3.5a.75.75 0 01-1.5 0v-3.5A.75.75 0 0110 6zm0 9a1 1 0 100-2 1 1 0 000 2z" clip-rule="evenodd" />
      </svg>`,
      info: `<svg class="h-5 w-5" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
        <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd" />
      </svg>`
    };
    return icons[type as keyof typeof icons] || icons.info;
  }
}