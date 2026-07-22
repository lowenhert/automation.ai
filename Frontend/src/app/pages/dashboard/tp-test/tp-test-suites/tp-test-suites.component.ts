import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TpTestService, TpProject, TpSuite, TpSection, TpTestCase } from '../../../../shared/services/tp-test.service';
import { UserManagementService } from '../../../../shared/services/user-management.service';
import { AuthService, UserProfile } from '../../../../shared/services/auth.service';

@Component({
  selector: 'app-tp-test-suites',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './tp-test-suites.component.html'
})
export class TpTestSuitesComponent implements OnInit {
  private tpService = inject(TpTestService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private userService = inject(UserManagementService);
  private authService = inject(AuthService);

  isAdminUser = false;
  projectId!: number;
  project: TpProject | null = null;
  suites: TpSuite[] = [];
  isLoading = false;
  error: string | null = null;
  users: UserProfile[] = [];

  // Active suite & sections
  activeSuite: TpSuite | null = null;
  sections: TpSection[] = [];
  cases: TpTestCase[] = [];
  sectionsLoading = false;

  // Create suite modal
  showCreateSuiteModal = false;
  newSuite: Partial<TpSuite> = { name: '', description: '' };

  // Create section modal
  showCreateSectionModal = false;
  newSection: Partial<TpSection> = { name: '' };
  parentSectionId: number | null = null;

  // Create case modal
  showCreateCaseModal = false;
  newCase: Partial<TpTestCase> = { title: '', preconditions: '', steps: '', expectedResult: '', actualResult: '', remark: '', assignedTo: '', priority: 'MEDIUM', type: 'FUNCTIONAL' };
  targetSectionId: number | null = null;

  // Expanded sections in tree
  expandedSections = new Set<number>();

  ngOnInit(): void {
    this.isAdminUser = this.authService.isAdmin();
    this.authService.currentUser$.subscribe(user => {
      if (user?.role) {
        this.isAdminUser = user.role.toUpperCase() === 'ADMIN';
      }
    });
    this.projectId = +this.route.snapshot.paramMap.get('projectId')!;
    this.loadProject();
    this.loadSuites();
    this.loadUsers();
  }

  loadUsers(): void {
    this.userService.getAllUsers().subscribe({
      next: (res) => this.users = res.rows || [],
      error: () => {}
    });
  }

  loadProject(): void {
    this.tpService.getProject(this.projectId).subscribe({
      next: (p) => this.project = p
    });
  }

  loadSuites(): void {
    this.isLoading = true;
    this.tpService.getSuites(this.projectId).subscribe({
      next: (data) => { this.suites = data; this.isLoading = false; if (data.length > 0 && !this.activeSuite) this.selectSuite(data[0]); },
      error: () => { this.error = 'Failed to load suites.'; this.isLoading = false; }
    });
  }

  selectSuite(suite: TpSuite): void {
    this.activeSuite = suite;
    this.sectionsLoading = true;
    this.tpService.getSections(suite.id!).subscribe({
      next: (data) => { this.sections = data; this.sectionsLoading = false; },
      error: () => { this.sectionsLoading = false; }
    });
    this.tpService.getCasesBySuite(suite.id!).subscribe({
      next: (data) => this.cases = data
    });
  }

  toggleSection(sectionId: number): void {
    if (this.expandedSections.has(sectionId)) {
      this.expandedSections.delete(sectionId);
    } else {
      this.expandedSections.add(sectionId);
    }
  }

  getRootSections(): TpSection[] {
    return this.sections.filter(s => !s.parentSectionId);
  }

  getChildSections(parentId: number): TpSection[] {
    return this.sections.filter(s => s.parentSectionId === parentId);
  }

  getCasesForSection(sectionId: number): TpTestCase[] {
    return this.cases.filter(c => c.sectionId === sectionId);
  }

  // ─── Create Suite ─────────────────────────────────────────────────────
  createSuite(): void {
    if (!this.newSuite.name?.trim()) return;
    this.tpService.createSuite({ ...this.newSuite, projectId: this.projectId } as TpSuite).subscribe({
      next: () => { this.showCreateSuiteModal = false; this.loadSuites(); }
    });
  }

  // ─── Create Section ───────────────────────────────────────────────────
  openCreateSection(parentId: number | null = null): void {
    this.parentSectionId = parentId;
    this.newSection = { name: '' };
    this.showCreateSectionModal = true;
  }

  createSection(): void {
    if (!this.newSection.name?.trim() || !this.activeSuite) return;
    const payload: any = { ...this.newSection, suiteId: this.activeSuite.id };
    if (this.parentSectionId) payload.parentSectionId = this.parentSectionId;
    this.tpService.createSection(payload).subscribe({
      next: () => { this.showCreateSectionModal = false; this.selectSuite(this.activeSuite!); }
    });
  }

  // ─── Create Case ──────────────────────────────────────────────────────
  openCreateCase(sectionId: number): void {
    this.targetSectionId = sectionId;
    this.newCase = { title: '', preconditions: '', steps: '', expectedResult: '', actualResult: '', remark: '', assignedTo: '', priority: 'MEDIUM', type: 'FUNCTIONAL' };
    this.showCreateCaseModal = true;
  }

  createCase(): void {
    if (!this.newCase.title?.trim() || !this.targetSectionId) return;
    const payload: any = { ...this.newCase, sectionId: this.targetSectionId };
    this.tpService.createCase(payload).subscribe({
      next: () => { this.showCreateCaseModal = false; this.selectSuite(this.activeSuite!); }
    });
  }

  deleteCase(tc: TpTestCase): void {
    if (!confirm(`Delete case "${tc.title}"?`)) return;
    this.tpService.deleteCase(tc.id!).subscribe({ next: () => this.selectSuite(this.activeSuite!) });
  }

  deleteSuite(suite: TpSuite): void {
    if (!confirm(`Delete suite "${suite.name}" and all its contents?`)) return;
    this.tpService.deleteSuite(suite.id!).subscribe({ next: () => { this.activeSuite = null; this.loadSuites(); } });
  }

  goBack(): void {
    this.router.navigate(['/tp-test/projects']);
  }

  getPriorityClass(priority: string): string {
    switch (priority?.toUpperCase()) {
      case 'HIGH': case 'CRITICAL': return 'text-red-600 bg-red-50 dark:text-red-400 dark:bg-red-900/20';
      case 'MEDIUM': return 'text-amber-600 bg-amber-50 dark:text-amber-400 dark:bg-amber-900/20';
      case 'LOW': return 'text-green-600 bg-green-50 dark:text-green-400 dark:bg-green-900/20';
      default: return 'text-gray-500 bg-gray-50 dark:text-gray-400 dark:bg-gray-800';
    }
  }
}
