import { Component, OnInit, OnDestroy, inject, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StbService, STBBox, StbTestCase, StbExecution, StbExecutionResult } from '../../../shared/services/stb.service';
import { AuthService } from '../../../shared/services/auth.service';

export interface TestModule {
  key: string;
  name: string;
  testFile: string;
  icon: string;
  description: string;
  steps: number;
  category: string;
}

export interface GroupedResult {
  module: string;
  results: StbExecutionResult[];
  passed: number;
  failed: number;
  skipped: number;
  expanded: boolean;
}

@Component({
  selector: 'app-stb-executions',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './stb-executions.component.html'
})
export class StbExecutionsComponent implements OnInit, OnDestroy {
  private stbService = inject(StbService);
  private authService = inject(AuthService);
  private pollTimer: any;
  isAdmin = false;

  // ─── Tabs ─────────────────────────────────────────────────────────────
  activeTab: 'run' | 'history' | 'test-cases' | 'scripts' = 'scripts';

  // ─── Test Modules (from Python Long-Run-Automation project) ───────────
  testModules: TestModule[] = [
    { key: 'parental_lock', name: 'Parental Lock Setup', testFile: 'test/test_parental_lock_setup.py', icon: '🔒', description: 'Navigate to Channel Lock, enter PIN (0000), lock/unlock channels. Validates parental controls workflow.', steps: 10, category: 'Security' },
    { key: 'favourite_channels', name: 'Favourite Channels', testFile: 'test/test_favourite_channels_setup.py', icon: '⭐', description: 'Navigate to User Preferences, select/deselect favourite channels until snackbar limit.', steps: 8, category: 'User Settings' },
    { key: 'remote_pairing', name: 'Remote Pairing Check', testFile: 'test/test_remote_pairing.py', icon: '📡', description: 'Navigate to Android TV Settings → Remotes & Accessories, verify connected remotes.', steps: 6, category: 'Connectivity' },
    { key: 'audio_change', name: 'Audio Change', testFile: 'test/test_audio_change.py', icon: '🔊', description: 'Switch between audio languages (English ↔ Malayalam) and verify apply button.', steps: 7, category: 'Audio / Video' },
    { key: 'display_resolution', name: 'Display Resolution', testFile: 'test/test_display_resolution_setup.py', icon: '📺', description: 'Change display resolution to 1080p50, handle device reboot, verify new setting.', steps: 8, category: 'Display' },
    { key: 'banner_configuration', name: 'Banner Configuration', testFile: 'test/test_banner_configuration.py', icon: '🏷️', description: 'Configure banner and notification settings in System Settings preferences.', steps: 7, category: 'System' },
    { key: 'sound_configuration', name: 'Sound Configuration', testFile: 'test/test_sound_configuration.py', icon: '🎵', description: 'Configure sound output — Speaker, HDMI, or SPDIF audio routing.', steps: 7, category: 'Audio / Video' },
    { key: 'picture_resolution', name: 'Picture Resolution', testFile: 'test/test_picture_resolution.py', icon: '🖼️', description: 'Set picture resolution and quality settings in display preferences.', steps: 6, category: 'Display' },
    { key: 'set_reminder', name: 'Set Reminder', testFile: 'test/test_set_reminder.py', icon: '⏰', description: 'Configure channel reminders and verify reminder notification functionality.', steps: 7, category: 'Features' },
    { key: 'lnb_settings', name: 'LNB Settings', testFile: 'test/test_lnb_settings.py', icon: '📡', description: 'Configure satellite LNB (type, frequency, polarization, symbol rate), verify signal strength.', steps: 10, category: 'Satellite' },
  ];
  selectedModules = new Set<string>();

  // ─── STB Boxes ────────────────────────────────────────────────────────
  stbBoxes: STBBox[] = [];

  // ─── Run / Create Execution ───────────────────────────────────────────
  execName = '';
  execStbNo: number | null = null;
  execBoxName = '';
  execDeviceId = '';
  execNotes = '';
  creating = false;
  createError: string | null = null;
  createSuccess: string | null = null;

  // ─── Execution History ────────────────────────────────────────────────
  executions: StbExecution[] = [];
  filteredExecutions: StbExecution[] = [];
  executionsLoading = false;
  executionSearch = '';
  filterStatus = '';
  filterStbNo: number | null = null;

  // ─── Execution Detail ─────────────────────────────────────────────────
  showDetail = false;
  selectedExecution: StbExecution | null = null;
  executionResults: StbExecutionResult[] = [];
  detailLoading = false;
  groupedResults: GroupedResult[] = [];

  // ─── Active Executions (running / pending) ────────────────────────────
  activeExecutions: StbExecution[] = [];

  // ─── Test Cases ───────────────────────────────────────────────────────
  testCases: StbTestCase[] = [];
  testCasesLoading = false;
  showAddTestCase = false;
  newTcModule = '';
  newTcName = '';
  newTcDesc = '';
  newTcCategory = '';
  newTcSteps: number | null = null;

  // ─── Log Viewer ───────────────────────────────────────────────────────
  @ViewChild('logContainer') logContainer!: ElementRef;
  logLines: string[] = [];
  logEventSource: EventSource | null = null;
  showLogs = false;
  logsAutoScroll = true;

  // ─── Script Management ────────────────────────────────────────────────
  scripts: any[] = [];
  scriptsLoading = false;
  showScriptEditor = false;
  editingScript: { name: string; content: string } | null = null;
  newScriptName = '';
  newScriptContent = '';
  scriptEditorMode: 'create' | 'edit' = 'create';

  // ─── Lifecycle ────────────────────────────────────────────────────────

  ngOnInit(): void {
    const profile = this.authService.getUserProfile();
    this.isAdmin = profile?.role === 'ADMIN';
    if (!profile) {
      this.authService.fetchUserProfile().subscribe(p => this.isAdmin = p.role === 'ADMIN');
    }
    this.loadStbBoxes();
    this.loadExecutions();
    this.loadTestCases();
    this.loadScripts();
    this.pollTimer = setInterval(() => {
      if (this.activeExecutions.length > 0) {
        this.refreshActiveExecutions();
      }
    }, 5000);
  }

  ngOnDestroy(): void {
    if (this.pollTimer) clearInterval(this.pollTimer);
    this.closeLogs();
  }

  // ─── Data Loading ─────────────────────────────────────────────────────

  loadStbBoxes(): void {
    this.stbService.getStbBoxes().subscribe({
      next: (data) => this.stbBoxes = data,
      error: () => {}
    });
  }

  loadExecutions(): void {
    this.executionsLoading = true;
    const obs = this.filterStbNo
      ? this.stbService.getExecutionsByStb(this.filterStbNo)
      : this.stbService.getExecutions();
    obs.subscribe({
      next: (data) => {
        this.executions = data;
        this.activeExecutions = data.filter(e => e.status === 'RUNNING' || e.status === 'PENDING');
        this.applyFilters();
        this.executionsLoading = false;
      },
      error: () => {
        this.executions = [];
        this.filteredExecutions = [];
        this.executionsLoading = false;
      }
    });
  }

  loadTestCases(): void {
    this.testCasesLoading = true;
    this.stbService.getTestCases().subscribe({
      next: (data) => {
        this.testCases = data;
        this.testCasesLoading = false;
      },
      error: () => { this.testCasesLoading = false; }
    });
  }

  refreshActiveExecutions(): void {
    this.stbService.getExecutions().subscribe({
      next: (data) => {
        const prevActive = this.activeExecutions.length;
        this.activeExecutions = data.filter(e => e.status === 'RUNNING' || e.status === 'PENDING');
        // Refresh detail view if open and the execution just finished
        if (this.showDetail && this.selectedExecution) {
          const updated = data.find(e => e.id === this.selectedExecution!.id);
          if (updated) {
            this.selectedExecution = { ...this.selectedExecution!, ...updated };
            if (updated.status === 'RUNNING') {
              this.loadExecutionResults(updated.id!);
            }
          }
        }
        // If active count changed, refresh history
        if (prevActive !== this.activeExecutions.length) {
          this.executions = data;
          this.applyFilters();
        }
      }
    });
  }

  loadExecutionResults(executionId: number): void {
    this.stbService.getExecutionResults(executionId).subscribe({
      next: (results) => {
        this.executionResults = results;
        this.groupResultsByModule();
      }
    });
  }

  // ─── Module Selection ─────────────────────────────────────────────────

  toggleModule(key: string): void {
    if (this.selectedModules.has(key)) {
      this.selectedModules.delete(key);
    } else {
      this.selectedModules.add(key);
    }
  }

  selectAllModules(): void {
    if (this.selectedModules.size === this.testModules.length) {
      this.selectedModules.clear();
    } else {
      this.testModules.forEach(m => this.selectedModules.add(m.key));
    }
  }

  // ─── Create & Trigger Execution ───────────────────────────────────────

  onBoxChange(): void {
    const box = this.stbBoxes.find(b => b.stbNo === this.execStbNo);
    this.execBoxName = box ? box.model : '';
    if (this.execStbNo) {
      const saved = localStorage.getItem(`stb_slot_${this.execStbNo}_ip`);
      if (saved) this.execDeviceId = saved;
    }
  }

  createAndTrigger(): void {
    this.createError = null;
    this.createSuccess = null;
    if (!this.execName.trim()) { this.createError = 'Enter an execution name.'; return; }
    if (!this.execStbNo) { this.createError = 'Select a target STB box.'; return; }
    if (!this.execDeviceId.trim()) { this.createError = 'Enter the ADB device ID (IP:port).'; return; }
    if (this.selectedModules.size === 0) { this.createError = 'Select at least one test module.'; return; }

    this.creating = true;
    const moduleNames = [...this.selectedModules].map(k => this.testModules.find(m => m.key === k)?.name || k);
    const matchingTcIds = this.testCases
      .filter(tc => tc.enabled && moduleNames.some(mn =>
        tc.moduleName.toLowerCase().replace(/\s+/g, '').includes(mn.toLowerCase().replace(/\s+(setup|check)/gi, '').replace(/\s+/g, '')) ||
        mn.toLowerCase().includes(tc.moduleName.toLowerCase())
      ))
      .map(tc => tc.id!);

    this.stbService.createExecution({
      executionName: this.execName.trim(),
      stbNo: this.execStbNo,
      boxName: this.execBoxName,
      testCaseIds: matchingTcIds.length > 0 ? matchingTcIds : [],
      triggeredBy: 'Dashboard',
      notes: `Modules: ${moduleNames.join(', ')}\nDevice: ${this.execDeviceId.trim()}${this.execNotes.trim() ? '\n' + this.execNotes.trim() : ''}`
    }).subscribe({
      next: (exec) => {
        this.stbService.triggerExecution(exec.id!, this.execDeviceId.trim(), [...this.selectedModules]).subscribe({
          next: () => {
            this.creating = false;
            this.createSuccess = `Execution "${this.execName}" triggered! Track it in the History tab.`;
            this.execName = '';
            this.execNotes = '';
            this.selectedModules.clear();
            this.loadExecutions();
          },
          error: () => {
            this.creating = false;
            this.createSuccess = `Execution created (ID: ${exec.id}). Trigger manually if needed.`;
            this.loadExecutions();
          }
        });
      },
      error: () => {
        this.creating = false;
        this.createError = 'Failed to create execution.';
      }
    });
  }

  // ─── Execution Filters ────────────────────────────────────────────────

  applyFilters(): void {
    let list = [...this.executions];
    if (this.executionSearch.trim()) {
      const term = this.executionSearch.toLowerCase();
      list = list.filter(e =>
        e.executionName?.toLowerCase().includes(term) ||
        e.boxName?.toLowerCase().includes(term) ||
        e.triggeredBy?.toLowerCase().includes(term) ||
        e.notes?.toLowerCase().includes(term)
      );
    }
    if (this.filterStatus) list = list.filter(e => e.status === this.filterStatus);
    this.filteredExecutions = list;
  }

  onSlotFilterChange(): void { this.loadExecutions(); }

  // ─── Execution Detail ─────────────────────────────────────────────────

  viewExecution(exec: StbExecution): void {
    this.detailLoading = true;
    this.selectedExecution = exec;
    this.showDetail = true;
    this.stbService.getExecutionDetail(exec.id!).subscribe({
      next: (detail) => {
        this.selectedExecution = detail;
        this.executionResults = detail.results || [];
        this.groupResultsByModule();
        this.detailLoading = false;
      },
      error: () => {
        this.executionResults = [];
        this.groupedResults = [];
        this.detailLoading = false;
      }
    });
  }

  closeDetail(): void {
    this.showDetail = false;
    this.selectedExecution = null;
    this.executionResults = [];
    this.groupedResults = [];
    this.closeLogs();
  }

  deleteExecution(exec: StbExecution): void {
    if (!confirm(`Delete execution "${exec.executionName}"?`)) return;
    this.stbService.deleteExecution(exec.id!).subscribe({ next: () => this.loadExecutions() });
  }

  groupResultsByModule(): void {
    const map: { [module: string]: StbExecutionResult[] } = {};
    for (const r of this.executionResults) {
      const k = r.moduleName || 'Unknown';
      if (!map[k]) map[k] = [];
      map[k].push(r);
    }
    this.groupedResults = Object.entries(map).map(([module, results]) => ({
      module,
      results: results.sort((a, b) => (a.stepNumber || 0) - (b.stepNumber || 0)),
      passed: results.filter(r => r.status === 'PASSED').length,
      failed: results.filter(r => r.status === 'FAILED').length,
      skipped: results.filter(r => r.status === 'SKIPPED').length,
      expanded: false
    }));
  }

  toggleGroupExpand(g: GroupedResult): void { g.expanded = !g.expanded; }

  // ─── Test Case Management ─────────────────────────────────────────────

  addTestCase(): void {
    if (!this.newTcModule.trim() || !this.newTcName.trim()) return;
    this.stbService.createTestCase({
      moduleName: this.newTcModule.trim(),
      testName: this.newTcName.trim(),
      description: this.newTcDesc.trim() || undefined,
      category: this.newTcCategory.trim() || undefined,
      totalSteps: this.newTcSteps || undefined
    }).subscribe({
      next: () => {
        this.loadTestCases();
        this.showAddTestCase = false;
        this.newTcModule = ''; this.newTcName = ''; this.newTcDesc = '';
        this.newTcCategory = ''; this.newTcSteps = null;
      }
    });
  }

  toggleTc(tc: StbTestCase): void {
    this.stbService.toggleTestCase(tc.id!).subscribe({ next: () => this.loadTestCases() });
  }

  deleteTc(tc: StbTestCase): void {
    if (!confirm(`Delete test case "${tc.testName}"?`)) return;
    this.stbService.deleteTestCase(tc.id!).subscribe({ next: () => this.loadTestCases() });
  }

  // ─── Helpers ──────────────────────────────────────────────────────────

  statusColor(status: string): string {
    switch (status) {
      case 'PASSED': case 'COMPLETED': return 'text-green-600 bg-green-50 dark:text-green-400 dark:bg-green-900/30';
      case 'FAILED': return 'text-red-600 bg-red-50 dark:text-red-400 dark:bg-red-900/30';
      case 'RUNNING': return 'text-blue-600 bg-blue-50 dark:text-blue-400 dark:bg-blue-900/30';
      case 'PENDING': return 'text-amber-600 bg-amber-50 dark:text-amber-400 dark:bg-amber-900/30';
      case 'SKIPPED': return 'text-gray-600 bg-gray-100 dark:text-gray-400 dark:bg-gray-800';
      case 'CANCELLED': return 'text-gray-500 bg-gray-100 dark:text-gray-500 dark:bg-gray-800';
      default: return 'text-gray-600 bg-gray-100';
    }
  }

  statusDotColor(status: string): string {
    switch (status) {
      case 'PASSED': case 'COMPLETED': return 'bg-green-500';
      case 'FAILED': return 'bg-red-500';
      case 'RUNNING': return 'bg-blue-500 animate-pulse';
      case 'PENDING': return 'bg-amber-500';
      default: return 'bg-gray-400';
    }
  }

  formatDuration(seconds: number | undefined): string {
    if (!seconds) return '—';
    if (seconds < 60) return seconds + 's';
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    if (m < 60) return m + 'm ' + s + 's';
    const h = Math.floor(m / 60);
    return h + 'h ' + (m % 60) + 'm';
  }

  getBoxLabel(stbNo: number): string {
    const box = this.stbBoxes.find(b => b.stbNo === stbNo);
    return box ? `${box.model} (#${stbNo})` : `STB #${stbNo}`;
  }

  getPassRate(): string {
    const done = this.executions.filter(e => e.status === 'COMPLETED' || e.status === 'FAILED');
    if (done.length === 0) return '—';
    const tp = done.reduce((s, e) => s + e.passed, 0);
    const tt = done.reduce((s, e) => s + e.totalTests, 0);
    return tt === 0 ? '—' : Math.round((tp / tt) * 100) + '%';
  }

  get moduleCategories(): string[] {
    return [...new Set(this.testModules.map(m => m.category))];
  }

  getCategoryModules(cat: string): TestModule[] {
    return this.testModules.filter(m => m.category === cat);
  }

  getProgressPercent(exec: StbExecution): number {
    if (!exec.totalTests || exec.totalTests === 0) return 0;
    return Math.round(((exec.passed + exec.failed + exec.skipped) / exec.totalTests) * 100);
  }

  // ─── Real-time Log Viewer ─────────────────────────────────────────────

  openLogs(executionId: number): void {
    this.closeLogs();
    this.logLines = [];
    this.showLogs = true;
    this.logEventSource = this.stbService.streamExecutionLogs(executionId);
    this.logEventSource.onmessage = (event) => {
      this.logLines.push(event.data);
      if (this.logsAutoScroll) {
        setTimeout(() => this.scrollLogsToBottom(), 50);
      }
    };
    this.logEventSource.onerror = () => {
      // Stream ended (execution finished or error)
      if (this.logEventSource) {
        this.logEventSource.close();
        this.logEventSource = null;
      }
    };
  }

  closeLogs(): void {
    if (this.logEventSource) {
      this.logEventSource.close();
      this.logEventSource = null;
    }
    this.showLogs = false;
    this.logLines = [];
  }

  scrollLogsToBottom(): void {
    if (this.logContainer?.nativeElement) {
      const el = this.logContainer.nativeElement;
      el.scrollTop = el.scrollHeight;
    }
  }

  // ─── Script File Management ───────────────────────────────────────────

  loadScripts(): void {
    this.scriptsLoading = true;
    this.stbService.listScripts().subscribe({
      next: (data) => { this.scripts = data; this.scriptsLoading = false; },
      error: () => { this.scripts = []; this.scriptsLoading = false; }
    });
  }

  openCreateScript(): void {
    this.scriptEditorMode = 'create';
    this.newScriptName = '';
    this.newScriptContent = `import pytest\nimport time\n\n\nclass TestNewModule:\n    """Test module description"""\n\n    def test_step_1(self, device):\n        """Step 1 description"""\n        pass\n`;
    this.editingScript = null;
    this.showScriptEditor = true;
  }

  openEditScript(fileName: string): void {
    this.stbService.getScript(fileName).subscribe({
      next: (data) => {
        this.scriptEditorMode = 'edit';
        this.editingScript = data;
        this.newScriptName = data.name;
        this.newScriptContent = data.content;
        this.showScriptEditor = true;
      }
    });
  }

  saveScript(): void {
    if (this.scriptEditorMode === 'create') {
      if (!this.newScriptName.trim()) return;
      let name = this.newScriptName.trim();
      if (!name.endsWith('.py')) name += '.py';
      this.stbService.createScript(name, this.newScriptContent).subscribe({
        next: () => { this.showScriptEditor = false; this.loadScripts(); }
      });
    } else {
      this.stbService.updateScript(this.editingScript!.name, this.newScriptContent).subscribe({
        next: () => { this.showScriptEditor = false; this.loadScripts(); }
      });
    }
  }

  deleteScript(fileName: string): void {
    if (!confirm(`Delete script "${fileName}"?`)) return;
    this.stbService.deleteScript(fileName).subscribe({
      next: () => this.loadScripts()
    });
  }

  closeScriptEditor(): void {
    this.showScriptEditor = false;
    this.editingScript = null;
  }
}
