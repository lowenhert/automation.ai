import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from './screen-history.service';

export type PlanStatus = 'DRAFT' | 'APPROVED' | 'REJECTED' | 'EXECUTING' | 'EXECUTED';
export type PlanAction = 'DPAD_UP' | 'DPAD_DOWN' | 'DPAD_LEFT' | 'DPAD_RIGHT' | 'SELECT' | 'BACK' | 'HOME'
  | 'WAIT' | 'ASSERT_SCREEN' | 'ASSERT_TEXT_VISIBLE' | 'UNSUPPORTED';
export type Evidence = 'OBSERVED' | 'NOT_OBSERVED' | 'UNKNOWN' | 'NOT_APPLICABLE';
export type FlagSeverity = 'ERROR' | 'WARNING' | 'INFO';

export interface PlanStep {
  order: number;
  action: PlanAction;
  requestedAction?: string | null;
  fromScreenId: number | null;
  expectedScreenId: number | null;
  unknownScreenDescription?: string | null;
  target: string | null;
  expectedState: string;
  timeoutMs: number;
  onFailure: 'ABORT' | 'RETRY' | 'SKIP' | 'RECOVER_BACK' | 'RECOVER_HOME';
}

export interface GeneratedPlan {
  title: string;
  summary: string;
  preconditions: { description: string; screenId: number | null }[];
  steps: PlanStep[];
  assertions: { afterStep: number; type: 'SCREEN_IS' | 'TEXT_VISIBLE' | 'TEXT_NOT_VISIBLE'; screenId: number | null; text: string | null; description: string }[];
  failureHandling: { maxRetries: number; recovery: 'NONE' | 'BACK' | 'HOME'; notes: string };
  assumptions: string[];
}

export interface PlanFlag {
  type: string;
  severity: FlagSeverity;
  stepOrder: number | null;
  message: string;
}

export interface PlanSummary {
  id: number;
  status: PlanStatus;
  title: string;
  goal: string;
  deviceSerial: string;
  stepCount: number;
  errorFlags: number;
  warningFlags: number;
  createdAt: string;
}

export interface PlanDetail extends PlanSummary {
  startScreenId?: number;
  maxSteps: number;
  plan: GeneratedPlan;
  flags: PlanFlag[];
  stepEvidence: Record<string, Evidence>;
  knowledge: { totalScreens: number; totalTransitions: number; transitionsProvided: number; screens: { id: number; label: string; risk: string }[] };
  model?: string;
  attempts: number;
  createdBy?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  reviewNotes?: string;
  note: string;
  executionId?: number | null;
}

export interface GeneratePlanRequest {
  goal: string;
  deviceId: string;
  startScreenId?: number | null;
  maxSteps?: number;
}

@Injectable({ providedIn: 'root' })
export class AiTestPlannerService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/ai/test-plans`;

  generate(request: GeneratePlanRequest): Observable<PlanDetail> {
    return this.http.post<PlanDetail>(this.API_URL, request);
  }

  list(page: number, size: number, status?: PlanStatus | null): Observable<PageResponse<PlanSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    return this.http.get<PageResponse<PlanSummary>>(this.API_URL, { params });
  }

  get(id: number): Observable<PlanDetail> {
    return this.http.get<PlanDetail>(`${this.API_URL}/${id}`);
  }

  execute(id: number, request?: { deviceId?: string; timeoutSeconds?: number }): Observable<PlanDetail> {
    return this.http.post<PlanDetail>(`${this.API_URL}/${id}/execute`, request ?? {});
  }
}
