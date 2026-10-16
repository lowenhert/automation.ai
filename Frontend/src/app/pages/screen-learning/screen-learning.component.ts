import { Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe, DecimalPipe, NgStyle } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, catchError, finalize, forkJoin, of, switchMap } from 'rxjs';
import { PageBreadcrumbComponent } from '../../shared/components/common/page-breadcrumb/page-breadcrumb.component';
import { ComponentCardComponent } from '../../shared/components/common/component-card/component-card.component';
import { AdbDevice, ScreenLearningAdbService, parseAdbTarget } from '../../shared/services/screen-learning-adb.service';
import { ScreenCaptureService } from '../../shared/services/screen-capture.service';
import {
  PageResponse, ScreenDetail, ScreenHistoryService, ScreenSummary, ScreenTextRegion
} from '../../shared/services/screen-history.service';
import { NotificationService } from '../../shared/services/notification.service';

@Component({
  selector: 'app-screen-learning',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, NgStyle, PageBreadcrumbComponent, ComponentCardComponent],
  templateUrl: './screen-learning.component.html'
})
export class ScreenLearningComponent implements OnInit, OnDestroy {
  private adbService = inject(ScreenLearningAdbService);
  private captureService = inject(ScreenCaptureService);
  private historyService = inject(ScreenHistoryService);
  private notifications = inject(NotificationService);
  private destroyRef = inject(DestroyRef);

  devices = signal<AdbDevice[]>([]);
  loadingDevices = signal(false);
  devicesError = signal<string | null>(null);

  connectHost = '';
  connectPort: number | null = 5555;
  connecting = signal(false);
  connectError = signal<string | null>(null);

  disconnectingSerial = signal<string | null>(null);
  refreshingSerial = signal<string | null>(null);

  onlineDevices = computed(() => this.devices().filter(d => d.state === 'device'));

  selectedDeviceId = signal<string | null>(null);
  capturing = signal(false);
  captureError = signal<string | null>(null);

  // Currently displayed screen (from GET /api/screens/{id})
  selectedScreen = signal<ScreenDetail | null>(null);
  loadingScreen = signal(false);
  screenError = signal<string | null>(null);
  previewUrl = signal<string | null>(null);
  showTextBoxes = signal(true);
  highlightedRegionIndex = signal<number | null>(null);
  textRegions = computed<ScreenTextRegion[]>(() => this.selectedScreen()?.textRegions ?? []);
  analysisError = computed<string | null>(() => {
    const s = this.selectedScreen();
    if (!s) return null;
    if (s.analysisStatus !== 'SUCCESS') return s.analysisError || 'Screen analysis did not run';
    if (s.ocrStatus !== 'SUCCESS') return s.analysisError || 'OCR failed for this screen';
    return null;
  });

  // Screen history (GET /api/screens, /api/screens/search)
  readonly historyPageSize = 10;
  history = signal<PageResponse<ScreenSummary> | null>(null);
  loadingHistory = signal(false);
  historyError = signal<string | null>(null);
  historyPage = signal(0);
  searchInput = '';
  activeSearch = signal<string | null>(null);

  private selectScreen$ = new Subject<string>();

  constructor() {
    // switchMap drops responses for a screen the user has already navigated away from
    this.selectScreen$.pipe(
      switchMap(id => {
        this.loadingScreen.set(true);
        this.screenError.set(null);
        return forkJoin({ detail: this.historyService.get(id), image: this.historyService.getImage(id) }).pipe(
          catchError(err => {
            this.screenError.set(this.errorMessage(err));
            return of(null);
          }),
          finalize(() => this.loadingScreen.set(false))
        );
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(result => {
      if (!result) return;
      this.highlightedRegionIndex.set(null);
      this.selectedScreen.set(result.detail);
      this.setPreview(URL.createObjectURL(result.image));
    });
  }

  ngOnInit(): void {
    this.refreshDevices();
    this.loadHistory(0);
  }

  ngOnDestroy(): void {
    this.setPreview(null);
  }

  refreshDevices(): void {
    this.loadingDevices.set(true);
    this.devicesError.set(null);
    this.adbService.getDevices()
      .pipe(finalize(() => this.loadingDevices.set(false)))
      .subscribe({
        next: devices => {
          this.devices.set(devices);
          const online = this.onlineDevices();
          if (!online.some(d => d.serial === this.selectedDeviceId())) {
            this.selectedDeviceId.set(online.length ? online[0].serial : null);
          }
        },
        error: err => this.devicesError.set(this.errorMessage(err))
      });
  }

  captureScreen(): void {
    const deviceId = this.selectedDeviceId();
    if (!deviceId) return;

    this.capturing.set(true);
    this.captureError.set(null);
    this.captureService.capture(deviceId)
      .pipe(finalize(() => this.capturing.set(false)))
      .subscribe({
        next: capture => {
          this.notifications.showSuccess('Screen captured', `${capture.resolution} from ${deviceId}`);
          this.selectScreen(capture.captureId);
          this.loadHistory(0);
        },
        error: err => this.captureError.set(this.errorMessage(err))
      });
  }

  selectScreen(id: string): void {
    this.selectScreen$.next(id);
  }

  loadHistory(page: number): void {
    const search = this.activeSearch();
    const request = search
      ? this.historyService.search(search, page, this.historyPageSize)
      : this.historyService.list(page, this.historyPageSize);

    this.loadingHistory.set(true);
    this.historyError.set(null);
    request.pipe(finalize(() => this.loadingHistory.set(false)))
      .subscribe({
        next: result => {
          this.history.set(result);
          this.historyPage.set(result.page);
        },
        error: err => this.historyError.set(this.errorMessage(err))
      });
  }

  searchHistory(): void {
    const text = this.searchInput.trim();
    if (text.length > 0 && text.length < 2) {
      this.historyError.set('Enter at least 2 characters to search');
      return;
    }
    this.activeSearch.set(text || null);
    this.loadHistory(0);
  }

  clearSearch(): void {
    this.searchInput = '';
    this.activeSearch.set(null);
    this.loadHistory(0);
  }

  /** Converts an image-pixel box into percentages so the overlay scales with the displayed image. */
  boxStyle(region: ScreenTextRegion): Record<string, string> {
    const screen = this.selectedScreen();
    if (!screen) return {};
    const b = region.bbox;
    return {
      left: `${(b.x / screen.width) * 100}%`,
      top: `${(b.y / screen.height) * 100}%`,
      width: `${(b.width / screen.width) * 100}%`,
      height: `${(b.height / screen.height) * 100}%`
    };
  }

  confidenceClass(confidence: number | null): string {
    if (confidence == null) return 'text-gray-400';
    if (confidence >= 85) return 'text-success-600 dark:text-success-500';
    if (confidence >= 65) return 'text-warning-600 dark:text-orange-400';
    return 'text-error-600 dark:text-error-500';
  }

  analysisBadgeClass(status: string | null): string {
    return status === 'SUCCESS'
      ? 'bg-success-50 text-success-600 dark:bg-success-500/15 dark:text-success-500'
      : 'bg-warning-50 text-warning-600 dark:bg-warning-500/15 dark:text-orange-400';
  }

  private setPreview(url: string | null): void {
    const previous = this.previewUrl();
    if (previous) URL.revokeObjectURL(previous);
    this.previewUrl.set(url);
  }

  connect(): void {
    const { host, port } = parseAdbTarget(this.connectHost, this.connectPort);
    if (!host) {
      this.connectError.set('Enter a device IP address or hostname');
      return;
    }
    if (port != null && (port < 1 || port > 65535)) {
      this.connectError.set('Port must be between 1 and 65535');
      return;
    }

    this.connecting.set(true);
    this.connectError.set(null);
    this.adbService.connect(host, port)
      .pipe(finalize(() => this.connecting.set(false)))
      .subscribe({
        next: res => {
          this.notifications.showSuccess('Device connected', res.message);
          this.connectHost = '';
          this.refreshDevices();
        },
        error: err => this.connectError.set(this.errorMessage(err))
      });
  }

  disconnect(device: AdbDevice): void {
    this.disconnectingSerial.set(device.serial);
    this.adbService.disconnect(device.serial)
      .pipe(finalize(() => this.disconnectingSerial.set(null)))
      .subscribe({
        next: res => {
          this.notifications.showSuccess('Device disconnected', res.message);
          this.refreshDevices();
        },
        error: err => this.notifications.showError('Disconnect failed', this.errorMessage(err))
      });
  }

  reloadInfo(device: AdbDevice): void {
    this.refreshingSerial.set(device.serial);
    this.adbService.getDeviceInfo(device.serial)
      .pipe(finalize(() => this.refreshingSerial.set(null)))
      .subscribe({
        next: updated => this.devices.update(list => list.map(d => d.serial === updated.serial ? updated : d)),
        error: err => this.notifications.showError('Device info unavailable', this.errorMessage(err))
      });
  }

  canDisconnect(device: AdbDevice): boolean {
    return device.connectionType === 'NETWORK' && device.serial.includes(':');
  }

  statusLabel(state: string): string {
    switch (state) {
      case 'device': return 'Online';
      case 'offline': return 'Offline';
      case 'unauthorized': return 'Unauthorized';
      default: return state;
    }
  }

  statusClass(state: string): string {
    switch (state) {
      case 'device': return 'bg-success-50 text-success-600 dark:bg-success-500/15 dark:text-success-500';
      case 'unauthorized': return 'bg-warning-50 text-warning-600 dark:bg-warning-500/15 dark:text-orange-400';
      default: return 'bg-error-50 text-error-600 dark:bg-error-500/15 dark:text-error-500';
    }
  }

  typeLabel(type: AdbDevice['connectionType']): string {
    switch (type) {
      case 'EMULATOR': return 'Emulator';
      case 'NETWORK': return 'Network';
      default: return 'USB';
    }
  }

  private errorMessage(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 0) return 'Cannot reach the backend server';
      if (err.status === 401) return 'Your session has expired. Please sign in again.';
      // Blob requests (screenshots) return the JSON error body as a Blob, which can't be read synchronously
      if (typeof err.error?.message === 'string') return err.error.message;
      if (err.status === 404) return 'The requested screen or screenshot was not found';
      if (err.status === 503) return 'Service temporarily unavailable (ADB, Python or OCR)';
      return `Request failed (${err.status} ${err.statusText})`;
    }
    return 'Unexpected error';
  }
}
