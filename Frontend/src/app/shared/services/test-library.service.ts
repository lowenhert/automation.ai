import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from './screen-history.service';

export type EventAction = 'DPAD_UP' | 'DPAD_DOWN' | 'DPAD_LEFT' | 'DPAD_RIGHT' | 'SELECT' | 'BACK' | 'HOME'
  | 'WAIT' | 'SCREENSHOT' | 'ASSERT_TEXT_VISIBLE' | 'ASSERT_TEXT_NOT_VISIBLE' | 'ASSERT_ANY_TEXT_VISIBLE';
export type ScriptLanguage = 'PYTEST' | 'ROBOT';
export type ScriptStatus = 'DRAFT' | 'APPROVED';
export type ExecutionStatus = 'RUNNING' | 'PASSED' | 'FAILED' | 'ERROR' | 'STOPPED' | 'TIMED_OUT' | 'INTERRUPTED';

export const EVENT_ACTIONS: EventAction[] = ['DPAD_UP', 'DPAD_DOWN', 'DPAD_LEFT', 'DPAD_RIGHT', 'SELECT', 'BACK', 'HOME',
  'WAIT', 'SCREENSHOT', 'ASSERT_TEXT_VISIBLE', 'ASSERT_TEXT_NOT_VISIBLE', 'ASSERT_ANY_TEXT_VISIBLE'];

export interface TestEvent {
  id?: number;
  position?: number;
  action: EventAction;
  target: string | null;
  expectedState: string | null;
  timeoutMs: number;
}

export interface ScriptSummary {
  id: number;
  testCaseId: number | null;
  name: string;
  language: ScriptLanguage;
  origin: 'MANUAL' | 'GENERATED';
  status: ScriptStatus;
  executable: boolean;
  validationValid: boolean | null;
  contentHash: string;
  updatedAt: string;
}

export interface TestCaseSummary {
  id: number;
  name: string;
  description: string | null;
  sourcePlanId: number | null;
  deviceSerial: string | null;
  eventCount: number;
  scriptCount: number;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TestCaseDetail extends Omit<TestCaseSummary, 'eventCount' | 'scriptCount'> {
  events: TestEvent[];
  scripts: ScriptSummary[];
}

export interface DeleteImpact {
  type: 'TEST_CASE' | 'SCRIPT';
  id: number;
  name: string;
  events: number;
  scripts: number;
  executions: number;
  running: boolean;
}

export interface ValidationIssue { line: number; message: string; }
export interface ValidationAction { line: number; action: string; detail: string; }
export interface Validation {
  valid: boolean;
  errors: ValidationIssue[];
  warnings: ValidationIssue[];
  tests: string[];
  actions: ValidationAction[];
}

export interface ScriptDetail {
  id: number;
  testCaseId: number | null;
  name: string;
  language: ScriptLanguage;
  origin: 'MANUAL' | 'GENERATED';
  status: ScriptStatus;
  executable: boolean;
  content: string;
  contentHash: string;
  version: number;
  validation: Validation | null;
  validationCurrent: boolean;
  approvedHash: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ScriptPreview {
  scriptId: number;
  name: string;
  language: ScriptLanguage;
  content: string;
  contentHash: string;
  validation: Validation;
  executable: boolean;
}

export interface TestResult { name: string; status: string; durationSeconds: number | null; message: string | null; }

export interface Execution {
  id: number;
  scriptId: number;
  deviceSerial: string;
  language: ScriptLanguage;
  contentHash: string;
  status: ExecutionStatus;
  timeoutSeconds: number;
  exitCode: number | null;
  testsTotal: number | null;
  testsPassed: number | null;
  testsFailed: number | null;
  testsSkipped: number | null;
  errorMessage: string | null;
  results: TestResult[];
  screenshots: string[];
  startedBy: string | null;
  startedAt: string;
  endedAt: string | null;
}

export interface LogChunk {
  executionId: number;
  offset: number;
  nextOffset: number;
  text: string;
  complete: boolean;
  status: ExecutionStatus;
}

@Injectable({ providedIn: 'root' })
export class TestLibraryService {
  private http = inject(HttpClient);
  private readonly CASES = `${environment.apiUrl}/test-cases`;
  private readonly SCRIPTS = `${environment.apiUrl}/test-scripts`;
  private readonly EXECUTIONS = `${environment.apiUrl}/executions`;

  // ── Test cases ──
  listCases(page = 0, size = 50, q = ''): Observable<PageResponse<TestCaseSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q.trim()) params = params.set('q', q.trim());
    return this.http.get<PageResponse<TestCaseSummary>>(this.CASES, { params });
  }
  getCase(id: number): Observable<TestCaseDetail> { return this.http.get<TestCaseDetail>(`${this.CASES}/${id}`); }
  createCase(body: { name: string; description?: string | null; deviceSerial?: string | null }): Observable<TestCaseDetail> {
    return this.http.post<TestCaseDetail>(this.CASES, body);
  }
  updateCase(id: number, body: { name: string; description?: string | null; deviceSerial?: string | null }): Observable<TestCaseDetail> {
    return this.http.put<TestCaseDetail>(`${this.CASES}/${id}`, body);
  }
  renameCase(id: number, name: string): Observable<TestCaseDetail> {
    return this.http.post<TestCaseDetail>(`${this.CASES}/${id}/rename`, { name });
  }
  duplicateCase(id: number): Observable<TestCaseDetail> { return this.http.post<TestCaseDetail>(`${this.CASES}/${id}/duplicate`, {}); }
  caseDeleteImpact(id: number): Observable<DeleteImpact> { return this.http.get<DeleteImpact>(`${this.CASES}/${id}/delete-impact`); }
  deleteCase(id: number): Observable<void> {
    return this.http.delete<void>(`${this.CASES}/${id}`, { params: new HttpParams().set('confirm', true) });
  }
  saveEvents(id: number, events: TestEvent[]): Observable<TestCaseDetail> {
    const body = events.map(e => ({ action: e.action, target: e.target, expectedState: e.expectedState, timeoutMs: e.timeoutMs }));
    return this.http.put<TestCaseDetail>(`${this.CASES}/${id}/events`, body);
  }
  fromPlan(planId: number, name?: string): Observable<TestCaseDetail> {
    return this.http.post<TestCaseDetail>(`${this.CASES}/from-plan`, { planId, name });
  }
  generateScript(caseId: number, language: ScriptLanguage, name?: string): Observable<ScriptDetail> {
    return this.http.post<ScriptDetail>(`${this.CASES}/${caseId}/scripts/generate`, { language, name });
  }

  // ── Scripts ──
  listScripts(page = 0, size = 50, testCaseId?: number | null): Observable<PageResponse<ScriptSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (testCaseId != null) params = params.set('testCaseId', testCaseId);
    return this.http.get<PageResponse<ScriptSummary>>(this.SCRIPTS, { params });
  }
  getScript(id: number): Observable<ScriptDetail> { return this.http.get<ScriptDetail>(`${this.SCRIPTS}/${id}`); }
  createScript(body: { testCaseId?: number | null; name: string; language: ScriptLanguage; content: string }): Observable<ScriptDetail> {
    return this.http.post<ScriptDetail>(this.SCRIPTS, body);
  }
  updateScript(id: number, body: { name?: string; content?: string; expectedVersion?: number }): Observable<ScriptDetail> {
    return this.http.put<ScriptDetail>(`${this.SCRIPTS}/${id}`, body);
  }
  renameScript(id: number, name: string): Observable<ScriptDetail> {
    return this.http.post<ScriptDetail>(`${this.SCRIPTS}/${id}/rename`, { name });
  }
  duplicateScript(id: number): Observable<ScriptDetail> { return this.http.post<ScriptDetail>(`${this.SCRIPTS}/${id}/duplicate`, {}); }
  validateScript(id: number): Observable<ScriptDetail> { return this.http.post<ScriptDetail>(`${this.SCRIPTS}/${id}/validate`, {}); }
  previewScript(id: number): Observable<ScriptPreview> { return this.http.get<ScriptPreview>(`${this.SCRIPTS}/${id}/preview`); }
  approveScript(id: number, contentHash: string): Observable<ScriptDetail> {
    return this.http.post<ScriptDetail>(`${this.SCRIPTS}/${id}/approve`, { contentHash });
  }
  revokeScript(id: number): Observable<ScriptDetail> { return this.http.post<ScriptDetail>(`${this.SCRIPTS}/${id}/revoke`, {}); }
  scriptDeleteImpact(id: number): Observable<DeleteImpact> { return this.http.get<DeleteImpact>(`${this.SCRIPTS}/${id}/delete-impact`); }
  deleteScript(id: number): Observable<void> {
    return this.http.delete<void>(`${this.SCRIPTS}/${id}`, { params: new HttpParams().set('confirm', true) });
  }

  // ── Executions ──
  execute(body: { scriptId: number; deviceId: string; contentHash: string; timeoutSeconds: number; confirm: boolean }): Observable<Execution> {
    return this.http.post<Execution>(this.EXECUTIONS, body);
  }
  listExecutions(scriptId: number | null, page = 0, size = 20): Observable<PageResponse<Execution>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (scriptId != null) params = params.set('scriptId', scriptId);
    return this.http.get<PageResponse<Execution>>(this.EXECUTIONS, { params });
  }
  getExecution(id: number): Observable<Execution> { return this.http.get<Execution>(`${this.EXECUTIONS}/${id}`); }
  stopExecution(id: number): Observable<Execution> { return this.http.post<Execution>(`${this.EXECUTIONS}/${id}/stop`, {}); }
  executionLog(id: number, offset: number): Observable<LogChunk> {
    return this.http.get<LogChunk>(`${this.EXECUTIONS}/${id}/log`, { params: new HttpParams().set('offset', offset) });
  }
  artifact(id: number, name: string): Observable<Blob> {
    return this.http.get(`${this.EXECUTIONS}/${id}/artifacts/${encodeURIComponent(name)}`, { responseType: 'blob' });
  }
  deleteExecution(id: number): Observable<void> {
    return this.http.delete<void>(`${this.EXECUTIONS}/${id}`, { params: new HttpParams().set('confirm', true) });
  }
}
