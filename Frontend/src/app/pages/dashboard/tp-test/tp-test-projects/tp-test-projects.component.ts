import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TpTestService, TpProject } from '../../../../shared/services/tp-test.service';
import { AuthService } from '../../../../shared/services/auth.service';

@Component({
  selector: 'app-tp-test-projects',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './tp-test-projects.component.html'
})
export class TpTestProjectsComponent implements OnInit {
  private tpService = inject(TpTestService);
  private authService = inject(AuthService);
  private router = inject(Router);

  projects: TpProject[] = [];
  isLoading = false;
  error: string | null = null;

  // Create modal
  showCreateModal = false;
  newProject: Partial<TpProject> = { name: '', description: '', category: 'MANUAL' };
  creating = false;

  isAdmin = false;

  ngOnInit(): void {
    this.isAdmin = this.authService.isAdmin();
    this.authService.currentUser$.subscribe(user => {
      if (user?.role) {
        this.isAdmin = user.role.toUpperCase() === 'ADMIN';
      }
    });
    this.loadProjects();
  }

  loadProjects(): void {
    this.isLoading = true;
    this.error = null;
    this.tpService.getProjects().subscribe({
      next: (data) => { this.projects = data; this.isLoading = false; },
      error: () => { this.error = 'Failed to load projects.'; this.isLoading = false; }
    });
  }

  openCreate(): void {
    this.newProject = { name: '', description: '', category: 'MANUAL' };
    this.showCreateModal = true;
  }

  createProject(): void {
    if (!this.newProject.name?.trim()) return;
    this.creating = true;
    this.tpService.createProject(this.newProject as TpProject).subscribe({
      next: () => { this.showCreateModal = false; this.creating = false; this.loadProjects(); },
      error: () => { this.creating = false; }
    });
  }

  openProject(project: TpProject): void {
    this.router.navigate(['/tp-test/projects', project.id, 'suites']);
  }

  deleteProject(project: TpProject): void {
    if (!confirm(`Delete project "${project.name}"? This will remove all suites, cases, and runs.`)) return;
    this.tpService.deleteProject(project.id!).subscribe({ next: () => this.loadProjects() });
  }

  getProjectStats(project: TpProject): { suites: number; cases: number; runs: number } {
    return { suites: project.suitesCount ?? 0, cases: project.casesCount ?? 0, runs: project.runsCount ?? 0 };
  }
}
