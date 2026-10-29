import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ScriptLanguage } from './test-library.service';

export interface AgenticRequest {
  deviceId: string;
  goal: string;
  maxSteps?: number;
  timeoutSeconds?: number;
  language?: ScriptLanguage;
}

export interface AgenticResult {
  planId: number | null;
  testCaseId: number | null;
  scriptId: number | null;
  executionId: number | null;
  status: string;
}

export interface AgenticMetrics {
  cpuPercent: number;
  memMb: number;
  timestamp: string;
}

export interface AgenticReExecuteRequest {
  scriptId: number;
  deviceId: string;
  content?: string;
  timeoutSeconds?: number;
}

@Injectable({ providedIn: 'root' })
export class AgenticService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/agentic`;

  run(req: AgenticRequest): Observable<AgenticResult> {
    return this.http.post<AgenticResult>(`${this.API_URL}/run`, req);
  }

  metrics(executionId: number): Observable<AgenticMetrics> {
    return this.http.get<AgenticMetrics>(`${this.API_URL}/${executionId}/metrics`);
  }

  reExecute(req: AgenticReExecuteRequest): Observable<AgenticResult> {
    return this.http.post<AgenticResult>(`${this.API_URL}/re-execute`, req);
  }
}
