import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { TpTestService, TpProject, TpTestRun } from '../../../../shared/services/tp-test.service';
import { AuthService } from '../../../../shared/services/auth.service';
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
  ApexGrid,
  ApexFill,
  ApexStroke,
  ApexTooltip
} from 'ng-apexcharts';

@Component({
  selector: 'app-tp-test-dashboard',
  standalone: true,
  imports: [CommonModule, NgApexchartsModule],
  templateUrl: './tp-test-dashboard.component.html'
})
export class TpTestDashboardComponent implements OnInit {
  Math = Math;
  private tpService = inject(TpTestService);
  private authService = inject(AuthService);
  private router = inject(Router);

  projects: TpProject[] = [];
  allRuns: TpTestRun[] = [];
  isLoading = false;
  isAdminUser = false;
  currentUserFullName = '';
  userName = '';

  // Summary stats
  totalProjects = 0;
  totalCases = 0;
  totalRuns = 0;
  overallPassRate = '0';
  totalPassed = 0;
  totalFailed = 0;
  totalBlocked = 0;
  totalUntested = 0;

  // Charts
  donutSeries: ApexNonAxisChartSeries = [];
  donutChart: ApexChart = { type: 'donut', height: 220, fontFamily: 'inherit' };
  donutLabels: string[] = ['Pass', 'Fail', 'Blocked', 'Untested'];
  donutColors: string[] = ['#22c55e', '#ef4444', '#a855f7', '#d1d5db'];
  donutPlotOptions: ApexPlotOptions = { pie: { donut: { size: '60%', labels: { show: true, total: { show: true, label: 'Total', fontSize: '12px', fontWeight: '600' } } } } };
  donutLegend: ApexLegend = { show: false };
  donutDataLabels: ApexDataLabels = { enabled: false };

  barSeries: ApexAxisChartSeries = [];
  barChart: ApexChart = { type: 'bar', height: 220, toolbar: { show: false }, fontFamily: 'inherit' };
  barXAxis: ApexXAxis = { categories: [], labels: { style: { fontSize: '10px' } } };
  barYAxis: ApexYAxis = { labels: { style: { fontSize: '10px' } } };
  barColors: string[] = ['#3b82f6'];
  barPlotOptions: ApexPlotOptions = { bar: { borderRadius: 4, columnWidth: '50%' } };
  barDataLabels: ApexDataLabels = { enabled: false };
  barGrid: ApexGrid = { borderColor: '#f1f1f1', strokeDashArray: 4, xaxis: { lines: { show: false } } };
  barFill: ApexFill = { opacity: 1 };
  barLegend: ApexLegend = { show: false };

  ngOnInit(): void {
    this.isAdminUser = this.authService.isAdmin();
    this.currentUserFullName = this.authService.getCurrentFullName();
    this.userName = this.currentUserFullName || this.authService.getCurrentUsername() || 'User';

    this.authService.currentUser$.subscribe(user => {
      if (user?.role) {
        const wasAdmin = this.isAdminUser;
        this.isAdminUser = user.role.toUpperCase() === 'ADMIN';
        this.currentUserFullName = ((user.firstName || '') + ' ' + (user.lastName || '')).trim();
        this.userName = this.currentUserFullName || user.username || 'User';
        if (wasAdmin !== this.isAdminUser && this.projects.length > 0) {
          this.loadAllRuns();
        }
      }
    });

    this.loadDashboard();
  }

  loadDashboard(): void {
    this.isLoading = true;
    this.tpService.getProjects().subscribe({
      next: (data) => {
        this.projects = data;
        this.totalProjects = data.length;
        this.totalCases = data.reduce((sum, p) => sum + (p.casesCount ?? 0), 0);
        this.totalRuns = data.reduce((sum, p) => sum + (p.runsCount ?? 0), 0);
        this.totalPassed = data.reduce((sum, p) => sum + (p.passedCount ?? 0), 0);
        const totalExecuted = data.reduce((sum, p) => sum + (p.executedCount ?? 0), 0);
        this.totalFailed = totalExecuted - this.totalPassed; // approx
        this.overallPassRate = totalExecuted > 0 ? ((this.totalPassed / totalExecuted) * 100).toFixed(0) : '0';
        this.loadAllRuns();
      },
      error: () => { this.isLoading = false; }
    });
  }

  loadAllRuns(): void {
    if (this.isAdminUser) {
      this.tpService.getAllRuns().subscribe({
        next: (runs) => { this.allRuns = runs; this.computeRunStats(); this.buildCharts(); this.isLoading = false; },
        error: () => { this.isLoading = false; }
      });
    } else if (this.currentUserFullName) {
      // Load runs assigned to user at run-level
      this.tpService.getRunsByAssignedTo(this.currentUserFullName).subscribe({
        next: (assignedRuns) => {
          // Also load all runs to find ones where user has cases assigned
          this.tpService.getAllRuns().subscribe({
            next: (allRuns) => {
              const assignedRunIds = new Set(assignedRuns.map(r => r.id));
              const otherRuns = allRuns.filter(r => !assignedRunIds.has(r.id));
              let pending = otherRuns.length;
              if (pending === 0) {
                this.allRuns = assignedRuns;
                this.computeRunStats(); this.buildCharts(); this.isLoading = false;
                return;
              }
              const additionalRuns: typeof assignedRuns = [];
              for (const run of otherRuns) {
                this.tpService.getRunCases(run.id!).subscribe({
                  next: (cases) => {
                    if (cases.some(c => c.assignedTo === this.currentUserFullName)) {
                      additionalRuns.push(run);
                    }
                    pending--;
                    if (pending === 0) {
                      this.allRuns = [...assignedRuns, ...additionalRuns];
                      this.computeRunStats(); this.buildCharts(); this.isLoading = false;
                    }
                  },
                  error: () => { pending--; if (pending === 0) { this.allRuns = [...assignedRuns, ...additionalRuns]; this.computeRunStats(); this.buildCharts(); this.isLoading = false; } }
                });
              }
            },
            error: () => { this.allRuns = assignedRuns; this.computeRunStats(); this.buildCharts(); this.isLoading = false; }
          });
        },
        error: () => { this.isLoading = false; }
      });
    } else {
      this.isLoading = false;
    }
  }

  computeRunStats(): void {
    this.totalPassed = this.allRuns.reduce((s, r) => s + (r.passedCount ?? 0), 0);
    this.totalFailed = this.allRuns.reduce((s, r) => s + (r.failedCount ?? 0), 0);
    this.totalBlocked = this.allRuns.reduce((s, r) => s + (r.blockedCount ?? 0), 0);
    const totalInRuns = this.allRuns.reduce((s, r) => s + (r.totalCases ?? 0), 0);
    this.totalUntested = totalInRuns - this.totalPassed - this.totalFailed - this.totalBlocked;
    const executed = this.totalPassed + this.totalFailed + this.totalBlocked;
    this.overallPassRate = executed > 0 ? ((this.totalPassed / executed) * 100).toFixed(0) : '0';
  }

  buildCharts(): void {
    // Donut
    this.donutSeries = [this.totalPassed, this.totalFailed, this.totalBlocked, Math.max(0, this.totalUntested)];

    // Bar chart: tests per project
    const names = this.projects.slice(0, 8).map(p => p.name.length > 12 ? p.name.substring(0, 12) + '…' : p.name);
    const counts = this.projects.slice(0, 8).map(p => p.casesCount ?? 0);
    this.barSeries = [{ name: 'Tests', data: counts }];
    this.barXAxis = { ...this.barXAxis, categories: names };
  }

  getProgressPercent(run: TpTestRun): number {
    const total = run.totalCases ?? 0;
    if (total === 0) return 0;
    const tested = (run.passedCount ?? 0) + (run.failedCount ?? 0) + (run.blockedCount ?? 0);
    return Math.round((tested / total) * 100);
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

  getRunStatusLabel(run: TpTestRun): string {
    const p = this.getProgressPercent(run);
    if (p === 100) return 'Completed';
    if (this.isOverdue(run)) return 'Overdue';
    if (p > 0) return 'In Progress';
    return 'Not Started';
  }

  getRunStatusClass(run: TpTestRun): string {
    const p = this.getProgressPercent(run);
    if (p === 100) return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400';
    if (this.isOverdue(run)) return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400';
    if (p > 0) return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400';
    return 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400';
  }

  get overdueRuns(): TpTestRun[] {
    return this.allRuns.filter(r => this.isOverdue(r));
  }

  get assignedRuns(): TpTestRun[] {
    return this.allRuns.filter(r => r.assignedTo && this.getProgressPercent(r) < 100);
  }

  get activeRuns(): TpTestRun[] {
    return this.allRuns.filter(r => this.getProgressPercent(r) < 100).slice(0, 6);
  }

  get myAssignedTasks(): TpTestRun[] {
    if (this.isAdminUser) return this.assignedRuns.slice(0, 8);
    return this.allRuns.filter(r => this.getProgressPercent(r) < 100).slice(0, 8);
  }

  get upcomingDeadlineRuns(): TpTestRun[] {
    return this.allRuns.filter(r => {
      if (!r.deadline || this.getProgressPercent(r) === 100) return false;
      const days = this.getDaysLeft(r);
      return days >= 0 && days <= 5;
    }).sort((a, b) => this.getDaysLeft(a) - this.getDaysLeft(b));
  }

  getProjectName(projectId: number): string {
    return this.projects.find(p => p.id === projectId)?.name ?? 'Unknown';
  }

  getProjectProgress(project: TpProject): number {
    const executed = project.executedCount ?? 0;
    const total = project.casesCount ?? 0;
    return total > 0 ? Math.round((executed / total) * 100) : 0;
  }

  getPriorityIcon(run: TpTestRun): string {
    if (this.isOverdue(run)) return 'H';
    if (this.getDaysLeft(run) <= 2) return 'M';
    return 'L';
  }

  getPriorityClass(run: TpTestRun): string {
    if (this.isOverdue(run)) return 'bg-red-500 text-white';
    if (run.deadline && this.getDaysLeft(run) <= 2) return 'bg-amber-500 text-white';
    return 'bg-blue-500 text-white';
  }

  goToProjects(): void {
    this.router.navigate(['/tp-test/projects']);
  }

  goToRuns(): void {
    this.router.navigate(['/tp-test/runs']);
  }
}
