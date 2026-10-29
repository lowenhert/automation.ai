import { Component, OnInit, OnDestroy, computed, inject, signal } from '@angular/core';
import { DatePipe, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { PageBreadcrumbComponent } from '../../shared/components/common/page-breadcrumb/page-breadcrumb.component';
import { ComponentCardComponent } from '../../shared/components/common/component-card/component-card.component';
import { AdbDevice, ScreenLearningAdbService } from '../../shared/services/screen-learning-adb.service';
import { ScreenExplorationService, ScreenGraphNode } from '../../shared/services/screen-exploration.service';
import { NotificationService } from '../../shared/services/notification.service';
import { TestLibraryService } from '../../shared/services/test-library.service';
import {
  AiTestPlannerService, Evidence, PlanDetail, PlanFlag, PlanStatus, PlanStep, PlanSummary
} from '../../shared/services/ai-test-planner.service';

@Component({
  selector: 'app-ai-test-planner',
  standalone: true,
  imports: [FormsModule, DatePipe, NgClass, PageBreadcrumbComponent, ComponentCardComponent],
  templateUrl: './ai-test-planner.component.html'
})
export class AiTestPlannerComponent implements OnInit, OnDestroy {
  private planner = inject(AiTestPlannerService);
  private adb = inject(ScreenLearningAdbService);
  private exploration = inject(ScreenExplorationService);
  private notifications = inject(NotificationService);
  private testLibrary = inject(TestLibraryService);

  devices = signal<AdbDevice[]>([]);
  screens = signal<ScreenGraphNode[]>([]);

  goal = '';
  deviceId = '';
  startScreenId: number | null = null;
  maxSteps = 15;
  generating = signal(false);
  executing = signal(false);
  generateError = signal<string | null>(null);

  plans = signal<PlanSummary[]>([]);
  statusFilter = signal<PlanStatus | null>(null);
  loadingPlans = signal(false);

  selected = signal<PlanDetail | null>(null);
  loadingPlan = signal(false);

  // Execution tracking & live logs
  executionLogs = signal<string>('');
  executionStatus = signal<string | null>(null);
  showLogs = signal<boolean>(true);
  private logOffset = 0;
  private logPollTimer: any = null;

  screenLabels = computed(() => new Map((this.selected()?.knowledge?.screens ?? []).map(s => [s.id, s.label])));
  flagsByStep = computed(() => {
    const map = new Map<number, PlanFlag[]>();
    for (const f of this.selected()?.flags ?? []) {
      if (f.stepOrder == null) continue;
      map.set(f.stepOrder, [...(map.get(f.stepOrder) ?? []), f]);
    }
    return map;
  });
  planFlags = computed(() => (this.selected()?.flags ?? []).filter(f => f.stepOrder == null));

  readonly goalMin = 5;
  readonly goalMax = 1000;

  ngOnInit(): void {
    this.adb.getDevices().subscribe({
      next: devices => {
        this.devices.set(devices);
        const online = devices.find(d => d.state === 'device');
        if (!this.deviceId && online) this.onDeviceChange(online.serial);
      },
      error: () => this.devices.set([])
    });
    this.loadPlans();
  }

  ngOnDestroy(): void {
    this.stopLogPolling();
  }

  onDeviceChange(serial: string): void {
    this.deviceId = serial;
    this.startScreenId = null;
    this.screens.set([]);
    if (!serial.trim()) return;
    this.exploration.graph(serial.trim()).subscribe({
      next: g => this.screens.set(g.nodes),
      error: () => this.screens.set([])
    });
  }

  canGenerate(): boolean {
    const g = this.goal.trim();
    return g.length >= this.goalMin && g.length <= this.goalMax && !!this.deviceId.trim() && !this.generating();
  }

  generate(): void {
    if (!this.canGenerate()) return;
    this.generating.set(true);
    this.generateError.set(null);
    this.stopLogPolling();
    this.executionLogs.set('');

    this.planner.generate({
      goal: this.goal.trim(), deviceId: this.deviceId.trim(), startScreenId: this.startScreenId, maxSteps: this.maxSteps
    }).pipe(finalize(() => this.generating.set(false)))
      .subscribe({
        next: plan => {
          this.selected.set(plan);
          this.notifications.showSuccess('Plan generated',
            `${plan.stepCount} steps · ${plan.errorFlags} errors · ${plan.warningFlags} warnings`);
          this.loadPlans();
          if (plan.executionId) {
            this.trackExecution(plan.executionId);
          }
        },
        error: err => this.generateError.set(this.errorMessage(err))
      });
  }

  execute(planId: number): void {
    this.executing.set(true);
    this.stopLogPolling();
    this.executionLogs.set('');

    this.planner.execute(planId, { deviceId: this.deviceId.trim() || undefined })
      .pipe(finalize(() => this.executing.set(false)))
      .subscribe({
        next: updated => {
          this.selected.set(updated);
          this.notifications.showSuccess('Execution started', `Plan #${updated.id} execution initiated`);
          this.loadPlans();
          if (updated.executionId) {
            this.trackExecution(updated.executionId);
          }
        },
        error: err => this.notifications.showError('Execution failed', this.errorMessage(err))
      });
  }

  private trackExecution(executionId: number): void {
    this.stopLogPolling();
    this.logOffset = 0;
    this.executionLogs.set('');
    this.executionStatus.set('RUNNING');

    this.logPollTimer = setInterval(() => {
      this.testLibrary.executionLog(executionId, this.logOffset).subscribe({
        next: chunk => {
          if (chunk.text) {
            this.executionLogs.update(prev => prev + chunk.text);
            this.logOffset = chunk.nextOffset;
          }
          this.executionStatus.set(chunk.status);
          if (chunk.complete) {
            this.stopLogPolling();
            this.loadPlans();
          }
        },
        error: () => this.stopLogPolling()
      });
    }, 1000);
  }

  private stopLogPolling(): void {
    if (this.logPollTimer) {
      clearInterval(this.logPollTimer);
      this.logPollTimer = null;
    }
  }

  loadPlans(): void {
    this.loadingPlans.set(true);
    this.planner.list(0, 30, this.statusFilter())
      .pipe(finalize(() => this.loadingPlans.set(false)))
      .subscribe({ next: page => this.plans.set(page.content), error: () => this.plans.set([]) });
  }

  setFilter(status: PlanStatus | null): void {
    this.statusFilter.set(status);
    this.loadPlans();
  }

  open(id: number): void {
    this.loadingPlan.set(true);
    this.stopLogPolling();
    this.executionLogs.set('');

    this.planner.get(id).pipe(finalize(() => this.loadingPlan.set(false))).subscribe({
      next: plan => {
        this.selected.set(plan);
        if (plan.executionId) {
          this.trackExecution(plan.executionId);
        }
      },
      error: err => this.notifications.showError('Could not load plan', this.errorMessage(err))
    });
  }

  screenName(id: number | null, unknownDescription?: string | null): string {
    if (id == null) return unknownDescription ? `Unknown: ${unknownDescription}` : '—';
    const label = this.screenLabels().get(id);
    return label ? `${label} (#${id})` : `#${id} (not explored)`;
  }

  evidence(step: PlanStep): Evidence {
    return this.selected()?.stepEvidence?.[step.order] ?? 'NOT_APPLICABLE';
  }

  evidenceLabel(e: Evidence): string {
    switch (e) {
      case 'OBSERVED': return 'Observed';
      case 'NOT_OBSERVED': return 'Not observed';
      case 'UNKNOWN': return 'Unknown screen';
      default: return '—';
    }
  }

  evidenceClass(e: Evidence): string {
    switch (e) {
      case 'OBSERVED': return 'bg-success-50 text-success-600 dark:bg-success-500/15 dark:text-success-500';
      case 'NOT_OBSERVED':
      case 'UNKNOWN': return 'bg-warning-50 text-warning-600 dark:bg-warning-500/15 dark:text-orange-400';
      default: return 'text-gray-400';
    }
  }

  severityClass(severity: string): string {
    return severity === 'ERROR'
      ? 'bg-error-50 text-error-600 dark:bg-error-500/15 dark:text-error-500'
      : 'bg-warning-50 text-warning-600 dark:bg-warning-500/15 dark:text-orange-400';
  }

  statusClass(status: PlanStatus): string {
    switch (status) {
      case 'EXECUTING': return 'bg-brand-50 text-[#465FFF] dark:bg-[#465FFF]/15 dark:text-blue-400 border border-[#465FFF]/30';
      case 'EXECUTED': return 'bg-success-50 text-success-600 dark:bg-success-500/15 dark:text-success-500';
      case 'APPROVED': return 'bg-success-50 text-success-600 dark:bg-success-500/15 dark:text-success-500';
      case 'REJECTED': return 'bg-error-50 text-error-600 dark:bg-error-500/15 dark:text-error-500';
      default: return 'bg-brand-50 text-brand-500 dark:bg-brand-500/15 dark:text-brand-400';
    }
  }

  private errorMessage(err: unknown): string {
    if (err instanceof HttpErrorResponse) {
      if (err.status === 0) return 'Cannot reach the backend server';
      if (err.status === 401) return 'Your session has expired. Please sign in again.';
      if (typeof err.error?.message === 'string') return err.error.message;
      return `Request failed (${err.status} ${err.statusText})`;
    }
    return 'Unexpected error';
  }
}
