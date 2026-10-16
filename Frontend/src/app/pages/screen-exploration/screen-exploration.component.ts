import { Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription, finalize, forkJoin, interval, startWith, switchMap } from 'rxjs';
import { PageBreadcrumbComponent } from '../../shared/components/common/page-breadcrumb/page-breadcrumb.component';
import { ComponentCardComponent } from '../../shared/components/common/component-card/component-card.component';
import { AdbDevice, ScreenLearningAdbService } from '../../shared/services/screen-learning-adb.service';
import { ScreenHistoryService } from '../../shared/services/screen-history.service';
import { NotificationService } from '../../shared/services/notification.service';
import {
  ExplorationMode, ExplorationSession, ExplorationStep, RemoteKey, ScreenExplorationService, ScreenGraph,
  ScreenGraphNode, isOpen
} from '../../shared/services/screen-exploration.service';
import { DEFAULT_LAYOUT, GraphLayout, actionLabel, layoutGraph } from './screen-graph-layout';

@Component({
  selector: 'app-screen-exploration',
  standalone: true,
  imports: [FormsModule, DatePipe, NgClass, PageBreadcrumbComponent, ComponentCardComponent],
  templateUrl: './screen-exploration.component.html'
})
export class ScreenExplorationComponent implements OnInit, OnDestroy {
  private adb = inject(ScreenLearningAdbService);
  private exploration = inject(ScreenExplorationService);
  private history = inject(ScreenHistoryService);
  private notifications = inject(NotificationService);
  private destroyRef = inject(DestroyRef);

  readonly layoutOptions = DEFAULT_LAYOUT;
  readonly actionLabel = actionLabel;
  readonly isOpen = isOpen;

  devices = signal<AdbDevice[]>([]);
  onlineDevices = computed(() => this.devices().filter(d => d.state === 'device'));
  deviceId = signal<string | null>(null);
  devicesError = signal<string | null>(null);

  // Start form
  mode: ExplorationMode = 'MANUAL';
  maxSteps = 30;
  maxDurationSeconds = 600;
  settleMs = 1500;
  allowSelect = false;
  allowHome = false;
  starting = signal(false);
  startError = signal<string | null>(null);

  session = signal<ExplorationSession | null>(null);
  steps = signal<ExplorationStep[]>([]);
  recentSessions = signal<ExplorationSession[]>([]);
  acting = signal<RemoteKey | null>(null);
  lastStep = signal<ExplorationStep | null>(null);
  stopping = signal(false);

  graph = signal<ScreenGraph | null>(null);
  graphError = signal<string | null>(null);
  layout = computed<GraphLayout | null>(() => {
    const g = this.graph();
    if (!g) return null;
    const s = this.session();
    const root = s?.deviceSerial === g.deviceSerial ? this.startNodeId(g) : undefined;
    return layoutGraph(g.nodes.map(n => n.id), g.edges, root);
  });
  nodeById = computed(() => new Map((this.graph()?.nodes ?? []).map(n => [n.id, n])));
  layoutNodes = computed(() => [...(this.layout()?.nodes.values() ?? [])]);

  selectedNode = signal<ScreenGraphNode | null>(null);
  selectedNodeImage = signal<string | null>(null);
  loadingNodeImage = signal(false);

  readonly sessionOpen = computed(() => isOpen(this.session()?.status));
  readonly remoteKeys: RemoteKey[] = ['DPAD_UP', 'DPAD_LEFT', 'SELECT', 'DPAD_RIGHT', 'DPAD_DOWN', 'BACK', 'HOME'];

  private poll?: Subscription;

  ngOnInit(): void {
    this.loadDevices();
    this.loadRecentSessions();
  }

  ngOnDestroy(): void {
    this.poll?.unsubscribe();
    this.setNodeImage(null);
  }

  loadDevices(): void {
    this.devicesError.set(null);
    this.adb.getDevices().subscribe({
      next: devices => {
        this.devices.set(devices);
        const online = devices.filter(d => d.state === 'device');
        if (!online.some(d => d.serial === this.deviceId())) {
          this.selectDevice(online.length ? online[0].serial : null);
        }
      },
      error: err => this.devicesError.set(this.errorMessage(err))
    });
  }

  selectDevice(serial: string | null): void {
    this.deviceId.set(serial);
    this.selectedNode.set(null);
    this.setNodeImage(null);
    if (serial) this.loadGraph(serial);
    else this.graph.set(null);
  }

  loadRecentSessions(): void {
    this.exploration.sessions(0, 10).subscribe({
      next: page => {
        this.recentSessions.set(page.content);
        // Re-attach to a session that is still open (e.g. after a page reload)
        const open = page.content.find(s => isOpen(s.status));
        if (open && !this.session()) this.openSession(open);
      }
    });
  }

  start(): void {
    const deviceId = this.deviceId();
    if (!deviceId) return;
    this.starting.set(true);
    this.startError.set(null);
    this.exploration.start({
      deviceId, mode: this.mode, maxSteps: this.maxSteps, maxDurationSeconds: this.maxDurationSeconds,
      settleMs: this.settleMs,
      allowSelect: this.mode === 'AUTO' ? this.allowSelect : undefined,
      allowHome: this.mode === 'AUTO' ? this.allowHome : undefined
    }).pipe(finalize(() => this.starting.set(false)))
      .subscribe({
        next: session => {
          this.notifications.showSuccess('Exploration started', `${session.mode} session #${session.id} on ${session.deviceSerial}`);
          this.openSession(session);
          this.loadRecentSessions();
        },
        error: err => this.startError.set(this.errorMessage(err))
      });
  }

  openSession(session: ExplorationSession): void {
    this.session.set(session);
    this.lastStep.set(null);
    if (this.deviceId() !== session.deviceSerial) this.deviceId.set(session.deviceSerial);
    this.startPolling(session.id);
  }

  /** Polls session, history and graph while the session is open; one final refresh after it ends. */
  private startPolling(id: number): void {
    this.poll?.unsubscribe();
    this.poll = interval(2000).pipe(
      startWith(0),
      switchMap(() => forkJoin({
        session: this.exploration.session(id),
        steps: this.exploration.steps(id, 0, 50),
        graph: this.exploration.graph(this.session()?.deviceSerial ?? this.deviceId()!)
      })),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: ({ session, steps, graph }) => {
        this.session.set(session);
        this.steps.set(steps.content);
        this.graph.set(graph);
        this.graphError.set(null);
        if (!isOpen(session.status)) {
          this.poll?.unsubscribe();
          this.loadRecentSessions();
        }
      },
      error: err => {
        this.graphError.set(this.errorMessage(err));
        this.poll?.unsubscribe();
      }
    });
  }

  press(key: RemoteKey): void {
    const s = this.session();
    if (!s || s.mode !== 'MANUAL' || !isOpen(s.status) || this.acting()) return;
    this.acting.set(key);
    this.exploration.act(s.id, key)
      .pipe(finalize(() => this.acting.set(null)))
      .subscribe({
        next: step => {
          this.lastStep.set(step);
          if (step.status === 'BLOCKED') this.notifications.showWarning('Action blocked', step.message ?? 'Blocked for safety');
          else if (step.status === 'FAILED') this.notifications.showError('Action failed', step.message ?? 'Step failed');
          this.startPolling(s.id);
        },
        error: err => this.notifications.showError('Action rejected', this.errorMessage(err))
      });
  }

  stop(): void {
    const s = this.session();
    if (!s || !isOpen(s.status)) return;
    this.stopping.set(true);
    this.exploration.stop(s.id).pipe(finalize(() => this.stopping.set(false))).subscribe({
      next: updated => {
        this.session.set(updated);
        this.startPolling(updated.id);
      },
      error: err => this.notifications.showError('Stop failed', this.errorMessage(err))
    });
  }

  loadGraph(serial: string): void {
    this.exploration.graph(serial).subscribe({
      next: g => {
        this.graph.set(g);
        this.graphError.set(null);
      },
      error: err => this.graphError.set(this.errorMessage(err))
    });
  }

  selectNode(node: ScreenGraphNode): void {
    this.selectedNode.set(node);
    this.setNodeImage(null);
    this.loadingNodeImage.set(true);
    this.history.getImage(node.representativeCaptureId)
      .pipe(finalize(() => this.loadingNodeImage.set(false)))
      .subscribe({
        next: blob => {
          if (this.selectedNode()?.id === node.id) this.setNodeImage(URL.createObjectURL(blob));
        },
        error: () => this.setNodeImage(null)
      });
  }

  private setNodeImage(url: string | null): void {
    const previous = this.selectedNodeImage();
    if (previous) URL.revokeObjectURL(previous);
    this.selectedNodeImage.set(url);
  }

  private startNodeId(graph: ScreenGraph): number | undefined {
    const firstStep = this.steps().length ? this.steps()[this.steps().length - 1] : null;
    return firstStep?.from?.id ?? this.session()?.currentNode?.id ?? graph.nodes[0]?.id;
  }

  nodeLabel(id: number): string {
    return this.nodeById().get(id)?.label ?? `#${id}`;
  }

  selfLoopSummary(loops: { action: RemoteKey; count: number }[]): string {
    return loops.map(l => `${actionLabel(l.action)}×${l.count}`).join(' ');
  }

  truncate(text: string, max: number): string {
    return text.length > max ? text.slice(0, max - 1) + '…' : text;
  }

  interpretationText(input: ScreenGraphNode['interpretationInput']): string {
    switch (input) {
      case 'IMAGE': return 'Gemma saw the screenshot (verified against OCR)';
      case 'IMAGE_UNVERIFIED': return 'Screenshot sent, but the model\'s answer did not match OCR — treat with care';
      case 'TEXT': return 'Gemma used OCR text and metadata only (no image)';
      default: return 'Not interpreted by the model (OCR heuristic label)';
    }
  }

  riskClass(risk: string): string {
    switch (risk) {
      case 'DESTRUCTIVE': return 'bg-error-50 text-error-600 dark:bg-error-500/15 dark:text-error-500';
      case 'CAUTION': return 'bg-warning-50 text-warning-600 dark:bg-warning-500/15 dark:text-orange-400';
      default: return 'bg-success-50 text-success-600 dark:bg-success-500/15 dark:text-success-500';
    }
  }

  statusClass(status: string): string {
    switch (status) {
      case 'ACTIVE':
      case 'RUNNING': return 'bg-brand-50 text-brand-500 dark:bg-brand-500/15 dark:text-brand-400';
      case 'COMPLETED':
      case 'OK': return 'bg-success-50 text-success-600 dark:bg-success-500/15 dark:text-success-500';
      case 'FAILED':
      case 'INTERRUPTED': return 'bg-error-50 text-error-600 dark:bg-error-500/15 dark:text-error-500';
      default: return 'bg-warning-50 text-warning-600 dark:bg-warning-500/15 dark:text-orange-400';
    }
  }

  nodeFill(node: ScreenGraphNode | undefined): string {
    if (!node) return '#f9fafb';
    if (node.riskLevel === 'DESTRUCTIVE') return '#fef3f2';
    if (node.riskLevel === 'CAUTION') return '#fffaeb';
    return '#ffffff';
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
