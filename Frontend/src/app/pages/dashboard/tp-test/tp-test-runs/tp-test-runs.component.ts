import { Component, OnInit, inject, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TpTestService, TpProject, TpTestRun, TpRunCase, TpSuite } from '../../../../shared/services/tp-test.service';
import { UserManagementService } from '../../../../shared/services/user-management.service';
import { AuthService, UserProfile } from '../../../../shared/services/auth.service';
import { StbService, STBBox } from '../../../../shared/services/stb.service';
import {
  NgApexchartsModule,
  ApexNonAxisChartSeries,
  ApexChart,
  ApexPlotOptions,
  ApexLegend,
  ApexDataLabels,
  ApexAxisChartSeries,
  ApexXAxis,
  ApexYAxis,
  ApexStroke,
  ApexFill,
  ApexGrid,
  ApexTooltip,
  ChartComponent
} from 'ng-apexcharts';

@Component({
  selector: 'app-tp-test-runs',
  standalone: true,
  imports: [CommonModule, FormsModule, NgApexchartsModule],
  templateUrl: './tp-test-runs.component.html'
})
export class TpTestRunsComponent implements OnInit {
  Math = Math;
  private tpService = inject(TpTestService);
  private userService = inject(UserManagementService);
  private authService = inject(AuthService);
  private stbService = inject(StbService);
  private router = inject(Router);

  projects: TpProject[] = [];
  selectedProjectId: number | null = null;
  runs: TpTestRun[] = [];
  users: UserProfile[] = [];
  stbBoxes: STBBox[] = [];
  isLoading = false;
  error: string | null = null;
  isAdminUser = false;
  currentUserFullName = '';

  // Filters & Search
  searchQuery = '';
  filterStatus = ''; // '', 'IN_PROGRESS', 'COMPLETED', 'NOT_STARTED', 'OVERDUE'
  filterAssignee = '';
  filterTestType = '';

  // Create run
  showCreateRunModal = false;
  suites: TpSuite[] = [];
  newRun: { name: string; projectId: number; suiteId: number; assignedTo: string; deadline: string; testType: string; stbBoxName: string } = { name: '', projectId: 0, suiteId: 0, assignedTo: '', deadline: '', testType: '', stbBoxName: '' };
  creating = false;

  // Execution view
  activeRun: TpTestRun | null = null;
  runCases: TpRunCase[] = [];
  activeRunCase: TpRunCase | null = null;
  executionLoading = false;
  resultComment = '';
  actualResultInput = '';
  remarkInput = '';

  // Detail view
  showDetailRun: TpTestRun | null = null;
  detailRunCases: TpRunCase[] = [];
  detailLoading = false;
  detailTab: 'overview' | 'cases' | 'timeline' = 'overview';
  caseFilterStatus = '';

  // Analytics charts
  donutSeries: ApexNonAxisChartSeries = [];
  donutChart: ApexChart = { type: 'donut', height: 260 };
  donutLabels: string[] = [];
  donutColors: string[] = [];
  donutPlotOptions: ApexPlotOptions = { pie: { donut: { size: '65%', labels: { show: true, total: { show: true, label: 'Total', fontSize: '14px', fontWeight: '700' } } } } };
  donutLegend: ApexLegend = { position: 'bottom', fontSize: '12px' };
  donutDataLabels: ApexDataLabels = { enabled: false };

  barSeries: ApexAxisChartSeries = [];
  barChart: ApexChart = { type: 'bar', height: 280, stacked: true, toolbar: { show: false } };
  barXAxis: ApexXAxis = { categories: [] };
  barYAxis: ApexYAxis = { title: { text: 'Cases' } };
  barColors: string[] = ['#22c55e', '#ef4444', '#a855f7', '#f59e0b', '#9ca3af'];
  barPlotOptions: ApexPlotOptions = { bar: { horizontal: false, columnWidth: '55%', borderRadius: 4 } };
  barLegend: ApexLegend = { position: 'top', fontSize: '11px' };
  barDataLabels: ApexDataLabels = { enabled: false };
  barGrid: ApexGrid = { borderColor: '#f1f1f1', xaxis: { lines: { show: false } } };
  barFill: ApexFill = { opacity: 1 };

  ngOnInit(): void {
    this.isAdminUser = this.authService.isAdmin();
    this.currentUserFullName = this.authService.getCurrentFullName();
    
    // Subscribe to user updates (handles async profile fetch for old sessions)
    this.authService.currentUser$.subscribe(user => {
      if (user?.role) {
        this.isAdminUser = user.role.toUpperCase() === 'ADMIN';
        this.currentUserFullName = ((user.firstName || '') + ' ' + (user.lastName || '')).trim();
        // Reload runs with correct filtering if data already loaded
        if (this.selectedProjectId && this.runs.length > 0) {
          this.loadRuns();
        }
      }
    });
    
    this.loadProjects();
    this.loadUsers();
    this.loadStbBoxes();
  }

  loadUsers(): void {
    this.userService.getAllUsers().subscribe({
      next: (res) => { this.users = res.rows || []; }
    });
  }

  loadStbBoxes(): void {
    this.stbService.getStbBoxes().subscribe({
      next: (data) => { this.stbBoxes = data; },
      error: () => {}
    });
  }

  loadProjects(): void {
    this.tpService.getProjects().subscribe({
      next: (data) => {
        this.projects = data;
        if (data.length > 0) {
          this.selectedProjectId = data[0].id!;
          this.loadRuns();
        }
      }
    });
  }

  loadRuns(): void {
    if (!this.selectedProjectId) return;
    this.isLoading = true;
    this.tpService.getRunsByProject(this.selectedProjectId).subscribe({
      next: (data) => {
        // Filter: admin sees all, user sees only their assigned runs
        if (!this.isAdminUser && this.currentUserFullName) {
          this.runs = data.filter(r => r.assignedTo === this.currentUserFullName);
        } else {
          this.runs = data;
        }
        this.isLoading = false;
        this.buildCharts();
      },
      error: () => { this.error = 'Failed to load runs.'; this.isLoading = false; }
    });
  }

  onProjectChange(): void {
    this.activeRun = null;
    this.showDetailRun = null;
    this.loadRuns();
  }

  // ─── Analytics ────────────────────────────────────────────────────────

  get totalCasesAcrossRuns(): number {
    return this.runs.reduce((s, r) => s + (r.totalCases ?? 0), 0);
  }

  get totalPassedAcrossRuns(): number {
    return this.runs.reduce((s, r) => s + (r.passedCount ?? 0), 0);
  }

  get totalFailedAcrossRuns(): number {
    return this.runs.reduce((s, r) => s + (r.failedCount ?? 0), 0);
  }

  get totalBlockedAcrossRuns(): number {
    return this.runs.reduce((s, r) => s + (r.blockedCount ?? 0), 0);
  }

  get totalUntestedAcrossRuns(): number {
    return this.totalCasesAcrossRuns - this.totalPassedAcrossRuns - this.totalFailedAcrossRuns - this.totalBlockedAcrossRuns;
  }

  get overallPassRate(): number {
    const executed = this.totalPassedAcrossRuns + this.totalFailedAcrossRuns + this.totalBlockedAcrossRuns;
    return executed === 0 ? 0 : Math.round((this.totalPassedAcrossRuns / executed) * 100);
  }

  get completedRunsCount(): number {
    return this.runs.filter(r => this.getProgressPercent(r) === 100).length;
  }

  get inProgressRunsCount(): number {
    return this.runs.filter(r => { const p = this.getProgressPercent(r); return p > 0 && p < 100; }).length;
  }

  get filteredRuns(): TpTestRun[] {
    return this.runs.filter(r => {
      // Search
      if (this.searchQuery) {
        const q = this.searchQuery.toLowerCase();
        const nameMatch = r.name.toLowerCase().includes(q);
        const assigneeMatch = r.assignedTo?.toLowerCase().includes(q);
        const stbMatch = r.stbBoxName?.toLowerCase().includes(q);
        if (!nameMatch && !assigneeMatch && !stbMatch) return false;
      }
      // Status filter
      if (this.filterStatus) {
        const p = this.getProgressPercent(r);
        if (this.filterStatus === 'COMPLETED' && p !== 100) return false;
        if (this.filterStatus === 'IN_PROGRESS' && (p === 0 || p === 100)) return false;
        if (this.filterStatus === 'NOT_STARTED' && p !== 0) return false;
        if (this.filterStatus === 'OVERDUE' && !this.isOverdue(r)) return false;
      }
      // Assignee filter
      if (this.filterAssignee && r.assignedTo !== this.filterAssignee) return false;
      // Test type filter
      if (this.filterTestType && r.testType !== this.filterTestType) return false;
      return true;
    });
  }

  get uniqueAssignees(): string[] {
    return [...new Set(this.runs.map(r => r.assignedTo).filter(Boolean))] as string[];
  }

  get uniqueTestTypes(): string[] {
    return [...new Set(this.runs.map(r => r.testType).filter(Boolean))] as string[];
  }

  buildCharts(): void {
    // Donut chart: aggregate status distribution
    const passed = this.totalPassedAcrossRuns;
    const failed = this.totalFailedAcrossRuns;
    const blocked = this.totalBlockedAcrossRuns;
    const untested = this.totalUntestedAcrossRuns;

    this.donutSeries = [passed, failed, blocked, untested];
    this.donutLabels = ['Passed', 'Failed', 'Blocked', 'Untested'];
    this.donutColors = ['#22c55e', '#ef4444', '#a855f7', '#d1d5db'];

    // Bar chart: per-run comparison
    const runNames = this.runs.map(r => r.name.length > 15 ? r.name.substring(0, 15) + '…' : r.name).reverse();
    const passedData = this.runs.map(r => r.passedCount ?? 0).reverse();
    const failedData = this.runs.map(r => r.failedCount ?? 0).reverse();
    const blockedData = this.runs.map(r => r.blockedCount ?? 0).reverse();
    const untestedData = this.runs.map(r => (r.totalCases ?? 0) - (r.passedCount ?? 0) - (r.failedCount ?? 0) - (r.blockedCount ?? 0)).reverse();

    this.barSeries = [
      { name: 'Passed', data: passedData },
      { name: 'Failed', data: failedData },
      { name: 'Blocked', data: blockedData },
      { name: 'Untested', data: untestedData }
    ];
    this.barXAxis = { categories: runNames, labels: { style: { fontSize: '10px' } } };
  }

  // ─── Detail View ──────────────────────────────────────────────────────

  openDetail(run: TpTestRun): void {
    this.showDetailRun = run;
    this.detailTab = 'overview';
    this.detailLoading = true;
    this.caseFilterStatus = '';
    this.tpService.getRunCases(run.id!).subscribe({
      next: (data) => { this.detailRunCases = data; this.detailLoading = false; },
      error: () => { this.detailLoading = false; }
    });
  }

  closeDetail(): void {
    this.showDetailRun = null;
    this.detailRunCases = [];
  }

  get filteredDetailCases(): TpRunCase[] {
    if (!this.caseFilterStatus) return this.detailRunCases;
    if (this.caseFilterStatus === 'UNTESTED') return this.detailRunCases.filter(c => !c.latestStatus);
    return this.detailRunCases.filter(c => c.latestStatus === this.caseFilterStatus);
  }

  getDetailDonutSeries(): ApexNonAxisChartSeries {
    if (!this.showDetailRun) return [];
    const s = this.getRunStats(this.showDetailRun);
    return [s.passed, s.failed, s.blocked, s.untested];
  }

  getAssigneeStats(): { name: string; total: number; passed: number; failed: number; blocked: number; untested: number }[] {
    const map: Record<string, { total: number; passed: number; failed: number; blocked: number; untested: number }> = {};
    for (const c of this.detailRunCases) {
      const assignee = c.assignedTo || 'Unassigned';
      if (!map[assignee]) map[assignee] = { total: 0, passed: 0, failed: 0, blocked: 0, untested: 0 };
      map[assignee].total++;
      if (c.latestStatus === 'PASSED') map[assignee].passed++;
      else if (c.latestStatus === 'FAILED') map[assignee].failed++;
      else if (c.latestStatus === 'BLOCKED') map[assignee].blocked++;
      else map[assignee].untested++;
    }
    return Object.entries(map).map(([name, stats]) => ({ name, ...stats })).sort((a, b) => b.total - a.total);
  }

  // ─── Create Run ──────────────────────────────────────────────────────
  openCreateRun(): void {
    if (!this.selectedProjectId) return;
    this.newRun = { name: '', projectId: this.selectedProjectId, suiteId: 0, assignedTo: '', deadline: '', testType: '', stbBoxName: '' };
    this.tpService.getSuites(this.selectedProjectId).subscribe({
      next: (data) => { this.suites = data; this.showCreateRunModal = true; }
    });
  }

  createRun(): void {
    if (!this.newRun.name.trim() || !this.newRun.suiteId) return;
    this.creating = true;
    // Build payload - convert empty strings to null so backend doesn't choke on them
    const payload: any = {
      name: this.newRun.name.trim(),
      projectId: this.newRun.projectId,
      suiteId: this.newRun.suiteId,
      assignedTo: this.newRun.assignedTo || null,
      deadline: this.newRun.deadline || null,
      testType: this.newRun.testType || null,
      stbBoxName: this.newRun.stbBoxName || null
    };
    this.tpService.createRun(payload).subscribe({
      next: () => { this.showCreateRunModal = false; this.creating = false; this.loadRuns(); },
      error: () => { this.creating = false; }
    });
  }

  // ─── Execute Run ─────────────────────────────────────────────────────
  openRun(run: TpTestRun): void {
    this.activeRun = run;
    this.executionLoading = true;
    this.tpService.getRunCases(run.id!).subscribe({
      next: (data) => {
        this.runCases = data;
        this.activeRunCase = data.length > 0 ? data[0] : null;
        this.executionLoading = false;
      },
      error: () => { this.executionLoading = false; }
    });
  }

  selectRunCase(rc: TpRunCase): void {
    this.activeRunCase = rc;
    this.resultComment = '';
    this.actualResultInput = rc.actualResult || '';
    this.remarkInput = rc.remark || '';
  }

  markResult(status: 'PASSED' | 'FAILED' | 'BLOCKED' | 'RETEST'): void {
    if (!this.activeRunCase) return;
    this.tpService.addResult(this.activeRunCase.id!, {
      status,
      comment: this.resultComment || undefined,
      actualResult: this.actualResultInput || undefined,
      remark: this.remarkInput || undefined
    }).subscribe({
      next: (result) => {
        this.activeRunCase!.latestStatus = status;
        this.activeRunCase!.actualResult = this.actualResultInput || undefined;
        this.activeRunCase!.remark = this.remarkInput || undefined;
        this.resultComment = '';
        this.actualResultInput = '';
        this.remarkInput = '';
        // Update run stats locally
        if (this.activeRun) {
          const stats = this.getRunStats(this.activeRun);
          // Recalculate from runCases
          this.activeRun.passedCount = this.runCases.filter(rc => rc.latestStatus === 'PASSED').length;
          this.activeRun.failedCount = this.runCases.filter(rc => rc.latestStatus === 'FAILED').length;
          this.activeRun.blockedCount = this.runCases.filter(rc => rc.latestStatus === 'BLOCKED').length;
        }
        // Move to next untested case
        const idx = this.runCases.indexOf(this.activeRunCase!);
        const next = this.runCases.find((rc, i) => i > idx && !rc.latestStatus);
        if (next) this.selectRunCase(next);
      }
    });
  }

  closeRun(): void {
    this.activeRun = null;
    this.runCases = [];
    this.activeRunCase = null;
    this.loadRuns(); // Refresh stats
  }

  deleteRun(run: TpTestRun): void {
    if (!confirm(`Delete run "${run.name}"?`)) return;
    this.tpService.deleteRun(run.id!).subscribe({ next: () => this.loadRuns() });
  }

  getStatusClass(status: string | undefined): string {
    switch (status?.toUpperCase()) {
      case 'PASSED': return 'text-green-600 bg-green-50 dark:text-green-400 dark:bg-green-900/20';
      case 'FAILED': return 'text-red-600 bg-red-50 dark:text-red-400 dark:bg-red-900/20';
      case 'BLOCKED': return 'text-purple-600 bg-purple-50 dark:text-purple-400 dark:bg-purple-900/20';
      case 'RETEST': return 'text-amber-600 bg-amber-50 dark:text-amber-400 dark:bg-amber-900/20';
      default: return 'text-gray-400 bg-gray-50 dark:text-gray-500 dark:bg-gray-800';
    }
  }

  getRunStats(run: TpTestRun): { total: number; passed: number; failed: number; blocked: number; untested: number } {
    return {
      total: run.totalCases ?? 0,
      passed: run.passedCount ?? 0,
      failed: run.failedCount ?? 0,
      blocked: run.blockedCount ?? 0,
      untested: (run.totalCases ?? 0) - (run.passedCount ?? 0) - (run.failedCount ?? 0) - (run.blockedCount ?? 0)
    };
  }

  getProgressPercent(run: TpTestRun): number {
    const total = run.totalCases ?? 0;
    if (total === 0) return 0;
    const tested = (run.passedCount ?? 0) + (run.failedCount ?? 0) + (run.blockedCount ?? 0);
    return Math.round((tested / total) * 100);
  }

  getStatusDotClass(status: string | undefined): string {
    switch (status?.toUpperCase()) {
      case 'PASSED': return 'bg-green-500';
      case 'FAILED': return 'bg-red-500';
      case 'BLOCKED': return 'bg-purple-500';
      case 'RETEST': return 'bg-amber-500';
      default: return 'bg-gray-300 dark:bg-gray-600';
    }
  }

  isOverdue(run: TpTestRun): boolean {
    if (!run.deadline) return false;
    return new Date(run.deadline) < new Date() && this.getProgressPercent(run) < 100;
  }

  getDaysLeft(run: TpTestRun): number {
    if (!run.deadline) return 999;
    const diff = new Date(run.deadline).getTime() - new Date().getTime();
    return Math.ceil(diff / (1000 * 60 * 60 * 24));
  }

  getDeadlineClass(run: TpTestRun): string {
    if (this.getProgressPercent(run) === 100) return 'text-green-600 bg-green-50 dark:text-green-400 dark:bg-green-900/20';
    const days = this.getDaysLeft(run);
    if (days < 0) return 'text-red-600 bg-red-50 dark:text-red-400 dark:bg-red-900/20';
    if (days <= 2) return 'text-amber-600 bg-amber-50 dark:text-amber-400 dark:bg-amber-900/20';
    return 'text-blue-600 bg-blue-50 dark:text-blue-400 dark:bg-blue-900/20';
  }

  get overdueRuns(): TpTestRun[] {
    return this.runs.filter(r => this.isOverdue(r));
  }

  get upcomingDeadlineRuns(): TpTestRun[] {
    return this.runs.filter(r => {
      if (!r.deadline || this.getProgressPercent(r) === 100) return false;
      const days = this.getDaysLeft(r);
      return days >= 0 && days <= 3;
    });
  }
}
