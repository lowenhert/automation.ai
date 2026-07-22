import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

// ─── Interfaces ─────────────────────────────────────────────────────────────

export interface TpProject {
  id?: number;
  name: string;
  description?: string;
  category?: string;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
  // Computed stats (from backend)
  suitesCount?: number;
  casesCount?: number;
  runsCount?: number;
  passedCount?: number;
  executedCount?: number;
}

export interface TpSuite {
  id?: number;
  projectId: number;
  name: string;
  description?: string;
  createdAt?: string;
}

export interface TpSection {
  id?: number;
  suiteId: number;
  parentSectionId?: number | null;
  name: string;
  createdAt?: string;
}

export interface TpTestCase {
  id?: number;
  sectionId: number;
  title: string;
  preconditions?: string;
  steps?: string;
  expectedResult?: string;
  actualResult?: string;
  remark?: string;
  assignedTo?: string;
  priority?: string; // LOW, MEDIUM, HIGH, CRITICAL
  type?: string; // FUNCTIONAL, REGRESSION, SMOKE, PERFORMANCE, OTHER
  automationStatus?: string; // MANUAL, AUTOMATED, NEEDS_AUTOMATION
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TpTestRun {
  id?: number;
  projectId: number;
  suiteId: number;
  planId?: number | null;
  milestoneId?: number | null;
  name: string;
  status?: string; // ACTIVE, COMPLETED, CLOSED
  assignedTo?: string;
  deadline?: string;
  testType?: string;
  stbBoxName?: string;
  createdBy?: string;
  createdAt?: string;
  // Computed stats
  totalCases?: number;
  passedCount?: number;
  failedCount?: number;
  blockedCount?: number;
}

export interface TpRunCase {
  id?: number;
  runId: number;
  caseId: number;
  testCaseTitle?: string;
  preconditions?: string;
  steps?: string;
  expectedResult?: string;
  actualResult?: string;
  remark?: string;
  assignedTo?: string;
  latestStatus?: string; // PASSED, FAILED, BLOCKED, RETEST
}

export interface TpResult {
  id?: number;
  runCaseId: number;
  status: string;
  comment?: string;
  executedBy?: string;
  executedAt?: string;
  defectLink?: string;
}

// ─── Service ────────────────────────────────────────────────────────────────

@Injectable({
  providedIn: 'root'
})
export class TpTestService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/tp-test`;

  // ─── Projects ─────────────────────────────────────────────────────────
  getProjects(): Observable<TpProject[]> {
    return this.http.get<TpProject[]>(`${this.baseUrl}/projects`);
  }

  getProject(id: number): Observable<TpProject> {
    return this.http.get<TpProject>(`${this.baseUrl}/projects/${id}`);
  }

  createProject(project: TpProject): Observable<TpProject> {
    return this.http.post<TpProject>(`${this.baseUrl}/projects`, project);
  }

  deleteProject(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/projects/${id}`);
  }

  // ─── Suites ───────────────────────────────────────────────────────────
  getSuites(projectId: number): Observable<TpSuite[]> {
    return this.http.get<TpSuite[]>(`${this.baseUrl}/projects/${projectId}/suites`);
  }

  createSuite(suite: TpSuite): Observable<TpSuite> {
    return this.http.post<TpSuite>(`${this.baseUrl}/suites`, suite);
  }

  deleteSuite(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/suites/${id}`);
  }

  // ─── Sections ─────────────────────────────────────────────────────────
  getSections(suiteId: number): Observable<TpSection[]> {
    return this.http.get<TpSection[]>(`${this.baseUrl}/suites/${suiteId}/sections`);
  }

  createSection(section: TpSection): Observable<TpSection> {
    return this.http.post<TpSection>(`${this.baseUrl}/sections`, section);
  }

  deleteSection(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/sections/${id}`);
  }

  // ─── Test Cases ───────────────────────────────────────────────────────
  getCasesBySuite(suiteId: number): Observable<TpTestCase[]> {
    return this.http.get<TpTestCase[]>(`${this.baseUrl}/suites/${suiteId}/cases`);
  }

  getCasesBySection(sectionId: number): Observable<TpTestCase[]> {
    return this.http.get<TpTestCase[]>(`${this.baseUrl}/sections/${sectionId}/cases`);
  }

  getCase(id: number): Observable<TpTestCase> {
    return this.http.get<TpTestCase>(`${this.baseUrl}/cases/${id}`);
  }

  createCase(testCase: TpTestCase): Observable<TpTestCase> {
    return this.http.post<TpTestCase>(`${this.baseUrl}/cases`, testCase);
  }

  updateCase(id: number, testCase: Partial<TpTestCase>): Observable<TpTestCase> {
    return this.http.put<TpTestCase>(`${this.baseUrl}/cases/${id}`, testCase);
  }

  deleteCase(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/cases/${id}`);
  }

  // ─── Test Runs ────────────────────────────────────────────────────────
  getRunsByProject(projectId: number): Observable<TpTestRun[]> {
    return this.http.get<TpTestRun[]>(`${this.baseUrl}/projects/${projectId}/runs`);
  }

  getAllRuns(): Observable<TpTestRun[]> {
    return this.http.get<TpTestRun[]>(`${this.baseUrl}/runs/all`);
  }

  getRunsByAssignedTo(assignedTo: string): Observable<TpTestRun[]> {
    return this.http.get<TpTestRun[]>(`${this.baseUrl}/runs/assigned/${encodeURIComponent(assignedTo)}`);
  }

  getRun(id: number): Observable<TpTestRun> {
    return this.http.get<TpTestRun>(`${this.baseUrl}/runs/${id}`);
  }

  createRun(run: TpTestRun): Observable<TpTestRun> {
    return this.http.post<TpTestRun>(`${this.baseUrl}/runs`, run);
  }

  deleteRun(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/runs/${id}`);
  }

  // ─── Run Cases ────────────────────────────────────────────────────────
  getRunCases(runId: number): Observable<TpRunCase[]> {
    return this.http.get<TpRunCase[]>(`${this.baseUrl}/runs/${runId}/cases`);
  }

  // ─── Results ──────────────────────────────────────────────────────────
  addResult(runCaseId: number, result: { status: string; comment?: string }): Observable<TpResult> {
    return this.http.post<TpResult>(`${this.baseUrl}/run-cases/${runCaseId}/results`, result);
  }

  getResults(runCaseId: number): Observable<TpResult[]> {
    return this.http.get<TpResult[]>(`${this.baseUrl}/run-cases/${runCaseId}/results`);
  }
}
