import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TpTestService, TpProject, TpTestRun, TpRunCase } from '../../../../shared/services/tp-test.service';
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
  ApexFill
} from 'ng-apexcharts';

@Component({
  selector: 'app-tp-test-history',
  standalone: true,
  imports: [CommonModule, FormsModule, NgApexchartsModule],
  templateUrl: './tp-test-history.component.html'
})
export class TpTestHistoryComponent implements OnInit {
  private tpService = inject(TpTestService);
  private authService = inject(AuthService);
  private router = inject(Router);

  // State
  projects: TpProject[] = [];
  selectedProjectId: number | null = null;
  runs: TpTestRun[] = [];
  isLoading = false;
  isAdminUser = false;

  // Active tab
  activeTab: 'allRuns' | 'comparison' | 'userAnalysis' | 'caseHistory' = 'allRuns';

  // All Runs table
  runsSearch = '';
  runsStatusFilter = '';
  runsAssigneeFilter = '';
  runsSortBy: 'name' | 'date' | 'progress' | 'passRate' = 'date';
  runsSortDir: 'asc' | 'desc' = 'desc';

  // Comparison
  comparisonRunIds: number[] = [];
  comparisonData: { runName: string; runId: number; cases: TpRunCase[] }[] = [];
  comparisonLoading = false;

  // User Analysis
  userAnalysisLoading = false;
  userAnalysisData: { user: string; totalAssigned: number; passed: number; failed: number; blocked: number; untested: number; failedCases: { title: string; runName: string; status: string }[] }[] = [];
  userAnalysisExpandedUser: string | null = null;

  // Case History
  caseHistoryLoading = false;
  caseHistoryData: { caseTitle: string; caseId: number; results: { runName: string; runId: number; status: string; assignedTo: string }[] }[] = [];
  caseHistorySearch = '';
  caseHistoryStatusFilter = '';
  caseHistorySortBy: 'name' | 'passRate' | 'runs' = 'name';

  // Charts
  trendChart: ApexChart = { type: 'line', height: 280, toolbar: { show: false } };
  trendSeries: ApexAxisChartSeries = [];
  trendXAxis: ApexXAxis = { categories: [] };
  trendColors = ['#22c55e', '#ef4444', '#a855f7', '#9ca3af'];

  userBarChart: ApexChart = { type: 'bar', height: 300, stacked: true, toolbar: { show: false } };
  userBarSeries: ApexAxisChartSeries = [];
  userBarXAxis: ApexXAxis = { categories: [] };
  userBarColors = ['#22c55e', '#ef4444', '#a855f7', '#9ca3af'];
  userBarPlotOptions: ApexPlotOptions = { bar: { horizontal: true, barHeight: '60%', borderRadius: 4 } };
  userBarLegend: ApexLegend = { position: 'top', fontSize: '11px' };
  userBarDataLabels: ApexDataLabels = { enabled: false };
  userBarGrid: ApexGrid = { borderColor: '#f1f1f1' };

  ngOnInit(): void {
    this.isAdminUser = this.authService.isAdmin();
    this.authService.currentUser$.subscribe(user => {
      if (user?.role) {
        this.isAdminUser = user.role.toUpperCase() === 'ADMIN';
      }
    });
    this.loadProjects();
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
        this.runs = data;
        this.isLoading = false;
        this.buildTrendChart();
      },
      error: () => { this.isLoading = false; }
    });
  }

  onProjectChange(): void {
    this.loadRuns();
    this.comparisonRunIds = [];
    this.comparisonData = [];
    this.userAnalysisData = [];
    this.caseHistoryData = [];
  }

  // ─── All Runs ─────────────────────────────────────────────────────
  get filteredRuns(): TpTestRun[] {
    let data = [...this.runs];
    if (this.runsSearch) {
      const q = this.runsSearch.toLowerCase();
      data = data.filter(r => r.name.toLowerCase().includes(q) || r.assignedTo?.toLowerCase().includes(q) || r.stbBoxName?.toLowerCase().includes(q));
    }
    if (this.runsStatusFilter) {
      data = data.filter(r => {
        const p = this.getProgressPercent(r);
        if (this.runsStatusFilter === 'COMPLETED') return p === 100;
        if (this.runsStatusFilter === 'IN_PROGRESS') return p > 0 && p < 100;
        if (this.runsStatusFilter === 'NOT_STARTED') return p === 0;
        if (this.runsStatusFilter === 'OVERDUE') return this.isOverdue(r);
        return true;
      });
    }
    if (this.runsAssigneeFilter) {
      data = data.filter(r => r.assignedTo === this.runsAssigneeFilter);
    }
    // Sort
    data.sort((a, b) => {
      let cmp = 0;
      if (this.runsSortBy === 'name') cmp = a.name.localeCompare(b.name);
      else if (this.runsSortBy === 'date') cmp = (a.createdAt || '').localeCompare(b.createdAt || '');
      else if (this.runsSortBy === 'progress') cmp = this.getProgressPercent(a) - this.getProgressPercent(b);
      else if (this.runsSortBy === 'passRate') cmp = this.getPassRate(a) - this.getPassRate(b);
      return this.runsSortDir === 'asc' ? cmp : -cmp;
    });
    return data;
  }

  get uniqueAssignees(): string[] {
    return [...new Set(this.runs.map(r => r.assignedTo).filter(Boolean))] as string[];
  }

  toggleSort(col: 'name' | 'date' | 'progress' | 'passRate'): void {
    if (this.runsSortBy === col) {
      this.runsSortDir = this.runsSortDir === 'asc' ? 'desc' : 'asc';
    } else {
      this.runsSortBy = col;
      this.runsSortDir = 'desc';
    }
  }

  // ─── Comparison ───────────────────────────────────────────────────
  toggleComparisonRun(runId: number): void {
    const idx = this.comparisonRunIds.indexOf(runId);
    if (idx >= 0) {
      this.comparisonRunIds.splice(idx, 1);
    } else {
      if (this.comparisonRunIds.length >= 5) return;
      this.comparisonRunIds.push(runId);
    }
    this.loadComparisonData();
  }

  loadComparisonData(): void {
    if (this.comparisonRunIds.length < 2) {
      this.comparisonData = [];
      return;
    }
    this.comparisonLoading = true;
    this.comparisonData = [];
    let loaded = 0;
    for (const runId of this.comparisonRunIds) {
      const run = this.runs.find(r => r.id === runId);
      if (!run) { loaded++; continue; }
      this.tpService.getRunCases(runId).subscribe({
        next: (cases) => {
          this.comparisonData.push({ runName: run.name, runId, cases });
          loaded++;
          if (loaded === this.comparisonRunIds.length) this.comparisonLoading = false;
        },
        error: () => { loaded++; if (loaded === this.comparisonRunIds.length) this.comparisonLoading = false; }
      });
    }
  }

  getComparisonCaseTitles(): string[] {
    const titles = new Set<string>();
    for (const d of this.comparisonData) {
      for (const c of d.cases) {
        if (c.testCaseTitle) titles.add(c.testCaseTitle);
      }
    }
    return Array.from(titles);
  }

  getComparisonStatus(runData: { cases: TpRunCase[] }, caseTitle: string): string {
    const c = runData.cases.find(rc => rc.testCaseTitle === caseTitle);
    return c?.latestStatus || 'UNTESTED';
  }

  getComparisonDiff(caseTitle: string): 'improved' | 'regressed' | 'same' | 'na' {
    if (this.comparisonData.length < 2) return 'na';
    const statuses = this.comparisonData.map(d => this.getComparisonStatus(d, caseTitle));
    const first = statuses[0];
    const last = statuses[statuses.length - 1];
    if (first === last) return 'same';
    if (last === 'PASSED' && first !== 'PASSED') return 'improved';
    if (first === 'PASSED' && last !== 'PASSED') return 'regressed';
    return 'same';
  }

  // ─── User Analysis ────────────────────────────────────────────────
  loadUserAnalysis(): void {
    if (this.userAnalysisData.length > 0) return;
    this.userAnalysisLoading = true;
    const userMap: Record<string, { totalAssigned: number; passed: number; failed: number; blocked: number; untested: number; failedCases: { title: string; runName: string; status: string }[] }> = {};
    let loaded = 0;

    for (const run of this.runs) {
      this.tpService.getRunCases(run.id!).subscribe({
        next: (cases) => {
          for (const c of cases) {
            const user = c.assignedTo || run.assignedTo || 'Unassigned';
            if (!userMap[user]) userMap[user] = { totalAssigned: 0, passed: 0, failed: 0, blocked: 0, untested: 0, failedCases: [] };
            userMap[user].totalAssigned++;
            if (c.latestStatus === 'PASSED') userMap[user].passed++;
            else if (c.latestStatus === 'FAILED') { userMap[user].failed++; userMap[user].failedCases.push({ title: c.testCaseTitle || 'Untitled', runName: run.name, status: 'FAILED' }); }
            else if (c.latestStatus === 'BLOCKED') { userMap[user].blocked++; userMap[user].failedCases.push({ title: c.testCaseTitle || 'Untitled', runName: run.name, status: 'BLOCKED' }); }
            else userMap[user].untested++;
          }
          loaded++;
          if (loaded === this.runs.length) {
            this.userAnalysisData = Object.entries(userMap)
              .map(([user, stats]) => ({ user, ...stats }))
              .sort((a, b) => b.failed - a.failed);
            this.userAnalysisLoading = false;
            this.buildUserChart();
          }
        },
        error: () => { loaded++; if (loaded === this.runs.length) this.userAnalysisLoading = false; }
      });
    }
  }

  toggleUserExpand(user: string): void {
    this.userAnalysisExpandedUser = this.userAnalysisExpandedUser === user ? null : user;
  }

  getUserPassRate(u: { passed: number; totalAssigned: number }): number {
    return u.totalAssigned ? Math.round((u.passed / u.totalAssigned) * 100) : 0;
  }

  // ─── Case History ─────────────────────────────────────────────────
  loadCaseHistory(): void {
    if (this.caseHistoryData.length > 0) return;
    this.caseHistoryLoading = true;
    const historyMap: Record<string, { caseId: number; results: { runName: string; runId: number; status: string; assignedTo: string }[] }> = {};
    let loaded = 0;

    for (const run of this.runs) {
      this.tpService.getRunCases(run.id!).subscribe({
        next: (cases) => {
          for (const c of cases) {
            const title = c.testCaseTitle || 'Untitled';
            if (!historyMap[title]) historyMap[title] = { caseId: c.caseId, results: [] };
            historyMap[title].results.push({
              runName: run.name,
              runId: run.id!,
              status: c.latestStatus || 'UNTESTED',
              assignedTo: c.assignedTo || run.assignedTo || '—'
            });
          }
          loaded++;
          if (loaded === this.runs.length) {
            this.caseHistoryData = Object.entries(historyMap)
              .map(([caseTitle, data]) => ({ caseTitle, ...data }))
              .sort((a, b) => a.caseTitle.localeCompare(b.caseTitle));
            this.caseHistoryLoading = false;
          }
        },
        error: () => { loaded++; if (loaded === this.runs.length) this.caseHistoryLoading = false; }
      });
    }
  }

  get filteredCaseHistory() {
    let data = this.caseHistoryData;
    if (this.caseHistorySearch) {
      const q = this.caseHistorySearch.toLowerCase();
      data = data.filter(d => d.caseTitle.toLowerCase().includes(q));
    }
    if (this.caseHistoryStatusFilter) {
      data = data.filter(d => d.results.some(r => r.status === this.caseHistoryStatusFilter));
    }
    if (this.caseHistorySortBy === 'passRate') {
      data = [...data].sort((a, b) => this.getCasePassRate(a) - this.getCasePassRate(b));
    } else if (this.caseHistorySortBy === 'runs') {
      data = [...data].sort((a, b) => b.results.length - a.results.length);
    }
    return data;
  }

  getCasePassRate(item: { results: { status: string }[] }): number {
    if (!item.results.length) return 0;
    const passed = item.results.filter(r => r.status === 'PASSED').length;
    return Math.round((passed / item.results.length) * 100);
  }

  getCaseStability(item: { results: { status: string }[] }): 'stable' | 'flaky' | 'failing' {
    const rate = this.getCasePassRate(item);
    if (rate >= 80) return 'stable';
    if (rate >= 40) return 'flaky';
    return 'failing';
  }

  // ─── Charts ───────────────────────────────────────────────────────
  buildTrendChart(): void {
    const runNames = this.runs.map(r => r.name.length > 12 ? r.name.substring(0, 12) + '…' : r.name);
    this.trendSeries = [
      { name: 'Passed', data: this.runs.map(r => r.passedCount ?? 0) },
      { name: 'Failed', data: this.runs.map(r => r.failedCount ?? 0) },
      { name: 'Blocked', data: this.runs.map(r => r.blockedCount ?? 0) },
      { name: 'Untested', data: this.runs.map(r => (r.totalCases ?? 0) - (r.passedCount ?? 0) - (r.failedCount ?? 0) - (r.blockedCount ?? 0)) }
    ];
    this.trendXAxis = { categories: runNames, labels: { style: { fontSize: '10px' } } };
  }

  buildUserChart(): void {
    const users = this.userAnalysisData.slice(0, 10);
    this.userBarXAxis = { categories: users.map(u => u.user.length > 15 ? u.user.substring(0, 15) + '…' : u.user) };
    this.userBarSeries = [
      { name: 'Passed', data: users.map(u => u.passed) },
      { name: 'Failed', data: users.map(u => u.failed) },
      { name: 'Blocked', data: users.map(u => u.blocked) },
      { name: 'Untested', data: users.map(u => u.untested) }
    ];
  }

  // ─── Helpers ──────────────────────────────────────────────────────
  getProgressPercent(run: TpTestRun): number {
    const total = run.totalCases ?? 0;
    if (total === 0) return 0;
    const tested = (run.passedCount ?? 0) + (run.failedCount ?? 0) + (run.blockedCount ?? 0);
    return Math.round((tested / total) * 100);
  }

  getPassRate(run: TpTestRun): number {
    const executed = (run.passedCount ?? 0) + (run.failedCount ?? 0) + (run.blockedCount ?? 0);
    return executed === 0 ? 0 : Math.round(((run.passedCount ?? 0) / executed) * 100);
  }

  isOverdue(run: TpTestRun): boolean {
    if (!run.deadline) return false;
    return new Date(run.deadline) < new Date() && this.getProgressPercent(run) < 100;
  }

  getStatusLabel(run: TpTestRun): string {
    const p = this.getProgressPercent(run);
    if (this.isOverdue(run)) return 'OVERDUE';
    if (p === 100) return 'COMPLETED';
    if (p > 0) return 'IN PROGRESS';
    return 'NOT STARTED';
  }

  getStatusBadgeClass(run: TpTestRun): string {
    const label = this.getStatusLabel(run);
    switch (label) {
      case 'COMPLETED': return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400';
      case 'IN PROGRESS': return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400';
      case 'OVERDUE': return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400';
      default: return 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400';
    }
  }

  getStatusDotClass(status: string): string {
    switch (status?.toUpperCase()) {
      case 'PASSED': return 'bg-green-500';
      case 'FAILED': return 'bg-red-500';
      case 'BLOCKED': return 'bg-purple-500';
      case 'RETEST': return 'bg-amber-500';
      default: return 'bg-gray-300';
    }
  }

  getStatusClass(status: string): string {
    switch (status?.toUpperCase()) {
      case 'PASSED': return 'text-green-600 bg-green-50 dark:text-green-400 dark:bg-green-900/20';
      case 'FAILED': return 'text-red-600 bg-red-50 dark:text-red-400 dark:bg-red-900/20';
      case 'BLOCKED': return 'text-purple-600 bg-purple-50 dark:text-purple-400 dark:bg-purple-900/20';
      case 'RETEST': return 'text-amber-600 bg-amber-50 dark:text-amber-400 dark:bg-amber-900/20';
      default: return 'text-gray-400 bg-gray-50 dark:text-gray-500 dark:bg-gray-800';
    }
  }

  countByStatus(cases: { status: string }[], status: string): number {
    return cases.filter(c => c.status === status).length;
  }

  onTabChange(tab: 'allRuns' | 'comparison' | 'userAnalysis' | 'caseHistory'): void {
    this.activeTab = tab;
    if (tab === 'userAnalysis') this.loadUserAnalysis();
    if (tab === 'caseHistory') this.loadCaseHistory();
  }

  goBack(): void {
    this.router.navigate(['/tp-test/runs']);
  }
}
