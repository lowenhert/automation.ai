import { Component, OnInit, OnDestroy, inject, signal, computed, ViewChild, ElementRef } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { finalize, Subscription } from 'rxjs';
import { PageBreadcrumbComponent } from '../../shared/components/common/page-breadcrumb/page-breadcrumb.component';
import { ComponentCardComponent } from '../../shared/components/common/component-card/component-card.component';
import { AgenticService, AgenticMetrics } from '../../shared/services/agentic.service';
import {
  TestLibraryService,
  ScriptDetail,
  Execution,
  ScriptLanguage,
  Validation
} from '../../shared/services/test-library.service';
import { ScreenLearningAdbService, AdbDevice } from '../../shared/services/screen-learning-adb.service';
import { AiTestPlannerService, PlanDetail } from '../../shared/services/ai-test-planner.service';
import { NotificationService } from '../../shared/services/notification.service';

export type DeviceType = 'ANDROID' | 'STB' | 'LINUX' | 'WINDOWS';

@Component({
  selector: 'app-agentic-automation',
  standalone: true,
  imports: [CommonModule, FormsModule, PageBreadcrumbComponent, ComponentCardComponent, DecimalPipe],
  templateUrl: './agentic-automation.component.html'
})
export class AgenticAutomationComponent implements OnInit, OnDestroy {
  private agenticService = inject(AgenticService);
  private testLibrary = inject(TestLibraryService);
  private adbService = inject(ScreenLearningAdbService);
  private plannerService = inject(AiTestPlannerService);
  private notifications = inject(NotificationService);

  @ViewChild('logTerminal') private logTerminal?: ElementRef<HTMLPreElement>;

  // --- Left Panel State ---
  deviceTypes: { type: DeviceType; label: string; desc: string; icon: string }[] = [
    {
      type: 'STB',
      label: 'Android TV / STB',
      desc: 'Set-top boxes & TV remotes',
      icon: `<svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z"/></svg>`
    },
    {
      type: 'ANDROID',
      label: 'Android Mobile',
      desc: 'Smartphones & tablets',
      icon: `<svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z"/></svg>`
    },
    {
      type: 'LINUX',
      label: 'Linux Device',
      desc: 'Embedded / server targets',
      icon: `<svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01"/></svg>`
    },
    {
      type: 'WINDOWS',
      label: 'Windows Desktop',
      desc: 'WinApp & desktop automation',
      icon: `<svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6a2 2 0 012-2h12a2 2 0 012 2v12a2 2 0 01-2 2H6a2 2 0 01-2-2V6z"/></svg>`
    }
  ];

  selectedDeviceType = signal<DeviceType>('STB');
  devices = signal<AdbDevice[]>([]);
  deviceId = '';
  goal = '';
  language = signal<ScriptLanguage>('PYTEST');
  maxSteps = 15;
  timeoutSeconds = 120;
  generating = signal(false);
  generateError = signal<string | null>(null);

  // --- Center Panel State ---
  currentPlan = signal<PlanDetail | null>(null);
  planSummaryCollapsed = signal(true);
  currentScript = signal<ScriptDetail | null>(null);
  scriptContent = '';
  validating = signal(false);
  reExecuting = signal(false);
  copied = signal(false);

  // --- Right Panel State ---
  currentExecution = signal<Execution | null>(null);
  logs = signal<string>('');
  autoScroll = signal(true);
  metrics = signal<AgenticMetrics | null>(null);
  screenshots = signal<string[]>([]);
  activeScreenshotIndex = signal(0);
  screenshotUrls: { [name: string]: string } = {};

  private logOffset = 0;
  private logPollInterval: any = null;
  private metricsPollInterval: any = null;
  private subscriptions: Subscription = new Subscription();

  ngOnInit(): void {
    this.adbService.getDevices().subscribe({
      next: devs => {
        this.devices.set(devs);
        const online = devs.find(d => d.state === 'device');
        if (online) {
          this.deviceId = online.serial;
        }
      },
      error: () => this.devices.set([])
    });
  }

  ngOnDestroy(): void {
    this.stopPolling();
    this.subscriptions.unsubscribe();
    // Revoke blob URLs
    Object.values(this.screenshotUrls).forEach(url => URL.revokeObjectURL(url));
  }

  selectDeviceType(t: DeviceType): void {
    this.selectedDeviceType.set(t);
  }

  togglePlanSummary(): void {
    this.planSummaryCollapsed.set(!this.planSummaryCollapsed());
  }

  canRun(): boolean {
    return !!this.deviceId.trim() && this.goal.trim().length >= 5 && !this.generating() && !this.reExecuting();
  }

  runAgentic(): void {
    if (!this.canRun()) return;

    this.generating.set(true);
    this.generateError.set(null);
    this.resetExecutionView();

    this.agenticService.run({
      deviceId: this.deviceId.trim(),
      goal: this.goal.trim(),
      maxSteps: this.maxSteps,
      timeoutSeconds: this.timeoutSeconds,
      language: this.language()
    }).pipe(finalize(() => this.generating.set(false)))
      .subscribe({
        next: res => {
          this.notifications.showSuccess('Agentic Pipeline Triggered', `Execution #${res.executionId ?? 'queued'} initiated`);

          if (res.planId) {
            this.loadPlan(res.planId);
          }

          if (res.scriptId) {
            this.loadScript(res.scriptId);
          }

          if (res.executionId) {
            this.startExecutionTracking(res.executionId);
          } else if (res.status === 'VALIDATION_FAILED') {
            this.generateError.set('Script validation failed. Review the code in the center panel.');
          }
        },
        error: err => {
          const msg = err.error?.message || err.message || 'Execution failed';
          this.generateError.set(msg);
          this.notifications.showError('Execution Error', msg);
        }
      });
  }

  private loadPlan(planId: number): void {
    this.plannerService.get(planId).subscribe({
      next: p => this.currentPlan.set(p),
      error: () => {}
    });
  }

  private loadScript(scriptId: number): void {
    this.testLibrary.getScript(scriptId).subscribe({
      next: s => {
        this.currentScript.set(s);
        this.scriptContent = s.content;
      },
      error: () => {}
    });
  }

  validateCode(): void {
    const script = this.currentScript();
    if (!script) return;

    this.validating.set(true);
    // First save content if edited
    this.testLibrary.updateScript(script.id, { content: this.scriptContent }).subscribe({
      next: () => {
        this.testLibrary.validateScript(script.id).pipe(finalize(() => this.validating.set(false))).subscribe({
          next: validated => {
            this.currentScript.set(validated);
            if (validated.validation?.valid) {
              this.notifications.showSuccess('Validation Passed', 'No syntax or static rule errors detected.');
            } else {
              this.notifications.showWarning('Validation Issues', `${validated.validation?.errors.length ?? 0} errors found.`);
            }
          },
          error: err => this.notifications.showError('Validation Error', err.error?.message || err.message)
        });
      },
      error: err => {
        this.validating.set(false);
        this.notifications.showError('Save Error', err.error?.message || err.message);
      }
    });
  }

  saveAndReExecute(): void {
    const script = this.currentScript();
    if (!script || !this.deviceId.trim()) return;

    this.reExecuting.set(true);
    this.resetExecutionView();

    this.agenticService.reExecute({
      scriptId: script.id,
      deviceId: this.deviceId.trim(),
      content: this.scriptContent,
      timeoutSeconds: this.timeoutSeconds
    }).pipe(finalize(() => this.reExecuting.set(false)))
      .subscribe({
        next: res => {
          this.notifications.showSuccess('Re-Execution Started', `Run #${res.executionId}`);
          this.loadScript(script.id);
          if (res.executionId) {
            this.startExecutionTracking(res.executionId);
          }
        },
        error: err => this.notifications.showError('Re-Execute Failed', err.error?.message || err.message)
      });
  }

  copyCode(): void {
    if (!this.scriptContent) return;
    navigator.clipboard.writeText(this.scriptContent).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    });
  }

  stopExecution(): void {
    const exec = this.currentExecution();
    if (!exec || exec.status !== 'RUNNING') return;

    this.testLibrary.stopExecution(exec.id).subscribe({
      next: updated => {
        this.currentExecution.set(updated);
        this.notifications.showInfo('Execution Stopped', `Run #${exec.id} halted`);
        this.stopPolling();
      },
      error: err => this.notifications.showError('Stop Failed', err.error?.message || err.message)
    });
  }

  private startExecutionTracking(executionId: number): void {
    this.stopPolling();
    this.logOffset = 0;
    this.logs.set('');

    // Fetch initial execution
    this.testLibrary.getExecution(executionId).subscribe({
      next: exec => {
        this.currentExecution.set(exec);
        this.loadScreenshots(exec);
      }
    });

    // Poll logs every 1s
    this.logPollInterval = setInterval(() => {
      this.testLibrary.executionLog(executionId, this.logOffset).subscribe({
        next: chunk => {
          if (chunk.text) {
            this.logs.update(prev => prev + chunk.text);
            this.logOffset = chunk.nextOffset;
            if (this.autoScroll()) {
              this.scrollToBottom();
            }
          }
          if (chunk.complete) {
            this.stopLogPolling();
            this.testLibrary.getExecution(executionId).subscribe({
              next: exec => {
                this.currentExecution.set(exec);
                this.loadScreenshots(exec);
              }
            });
          }
        },
        error: () => {}
      });
    }, 1000);

    // Poll performance metrics every 2s
    this.pollMetrics(executionId);
    this.metricsPollInterval = setInterval(() => {
      const exec = this.currentExecution();
      if (!exec || exec.status !== 'RUNNING') {
        this.stopMetricsPolling();
        return;
      }
      this.pollMetrics(executionId);
    }, 2000);
  }

  private pollMetrics(executionId: number): void {
    this.agenticService.metrics(executionId).subscribe({
      next: m => this.metrics.set(m),
      error: () => {}
    });
  }

  private loadScreenshots(exec: Execution): void {
    if (!exec.screenshots || exec.screenshots.length === 0) return;
    this.screenshots.set(exec.screenshots);

    for (const name of exec.screenshots) {
      if (!this.screenshotUrls[name]) {
        this.testLibrary.artifact(exec.id, name).subscribe({
          next: blob => {
            this.screenshotUrls[name] = URL.createObjectURL(blob);
          },
          error: () => {}
        });
      }
    }
  }

  prevScreenshot(): void {
    if (this.activeScreenshotIndex() > 0) {
      this.activeScreenshotIndex.update(i => i - 1);
    }
  }

  nextScreenshot(): void {
    if (this.activeScreenshotIndex() < this.screenshots().length - 1) {
      this.activeScreenshotIndex.update(i => i + 1);
    }
  }

  private stopLogPolling(): void {
    if (this.logPollInterval) {
      clearInterval(this.logPollInterval);
      this.logPollInterval = null;
    }
  }

  private stopMetricsPolling(): void {
    if (this.metricsPollInterval) {
      clearInterval(this.metricsPollInterval);
      this.metricsPollInterval = null;
    }
  }

  private stopPolling(): void {
    this.stopLogPolling();
    this.stopMetricsPolling();
  }

  private resetExecutionView(): void {
    this.logs.set('');
    this.logOffset = 0;
    this.currentExecution.set(null);
    this.metrics.set(null);
    this.screenshots.set([]);
    this.activeScreenshotIndex.set(0);
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      if (this.logTerminal?.nativeElement) {
        this.logTerminal.nativeElement.scrollTop = this.logTerminal.nativeElement.scrollHeight;
      }
    }, 50);
  }

  statusBadgeClass(status?: string): string {
    switch (status) {
      case 'RUNNING': return 'bg-brand-50 text-[#465FFF] border border-[#465FFF]/30 dark:bg-[#465FFF]/15 dark:text-blue-400';
      case 'PASSED': return 'bg-success-50 text-success-600 border border-success-200 dark:bg-success-500/15 dark:text-success-400';
      case 'FAILED': return 'bg-error-50 text-error-600 border border-error-200 dark:bg-error-500/15 dark:text-error-400';
      case 'ERROR': return 'bg-error-50 text-error-700 border border-error-300 dark:bg-error-500/20 dark:text-error-400';
      case 'STOPPED': return 'bg-gray-100 text-gray-700 border border-gray-300 dark:bg-gray-800 dark:text-gray-300';
      case 'TIMED_OUT': return 'bg-warning-50 text-warning-700 border border-warning-300 dark:bg-warning-500/20 dark:text-amber-400';
      default: return 'bg-gray-100 text-gray-500 border border-gray-200 dark:bg-gray-800 dark:text-gray-400';
    }
  }
}
