import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from './screen-history.service';

export type RemoteKey = 'DPAD_UP' | 'DPAD_DOWN' | 'DPAD_LEFT' | 'DPAD_RIGHT' | 'SELECT' | 'BACK' | 'HOME';
export type ExplorationMode = 'MANUAL' | 'AUTO';
export type SessionStatus = 'ACTIVE' | 'RUNNING' | 'STOPPED' | 'COMPLETED' | 'LIMIT_REACHED' | 'STALLED' | 'FAILED' | 'INTERRUPTED';
export type RiskLevel = 'SAFE' | 'CAUTION' | 'DESTRUCTIVE';
export type InterpretationInput = 'NONE' | 'TEXT' | 'IMAGE' | 'IMAGE_UNVERIFIED';

export interface NodeRef {
  id: number;
  label: string | null;
}

export interface ExplorationSession {
  id: number;
  deviceSerial: string;
  mode: ExplorationMode;
  status: SessionStatus;
  maxSteps: number;
  maxDurationSeconds: number;
  settleMs: number;
  allowSelect: boolean;
  allowHome: boolean;
  stepsTaken: number;
  newScreens: number;
  currentNode?: NodeRef;
  currentCaptureId?: string;
  stopRequested: boolean;
  stopReason?: string;
  startedAt: string;
  endedAt?: string;
}

export interface ExplorationStep {
  id: number;
  stepIndex: number;
  action: RemoteKey;
  status: 'OK' | 'BLOCKED' | 'FAILED';
  from?: NodeRef;
  to?: NodeRef;
  fromCaptureId?: string;
  toCaptureId?: string;
  newScreen: boolean;
  sameScreen: boolean;
  autoTriggered: boolean;
  durationMs?: number;
  message?: string;
  createdAt: string;
}

export interface ScreenGraphNode {
  id: number;
  label: string;
  screenType?: string;
  description?: string;
  riskLevel: RiskLevel;
  labelSource: 'OCR' | 'LLM';
  interpretationInput: InterpretationInput;
  interpretationModel?: string;
  interpretationError?: string;
  visitCount: number;
  representativeCaptureId: string;
  imageUrl: string;
  firstSeenAt: string;
  lastSeenAt: string;
}

export interface ScreenGraphEdge {
  id: number;
  from: number;
  to: number;
  action: RemoteKey;
  count: number;
  lastSeenAt: string;
}

export interface ScreenGraph {
  deviceSerial: string;
  nodes: ScreenGraphNode[];
  edges: ScreenGraphEdge[];
}

export interface StartSessionRequest {
  deviceId: string;
  mode: ExplorationMode;
  maxSteps?: number;
  maxDurationSeconds?: number;
  settleMs?: number;
  allowSelect?: boolean;
  allowHome?: boolean;
}

export function isOpen(status: SessionStatus | undefined): boolean {
  return status === 'ACTIVE' || status === 'RUNNING';
}

@Injectable({ providedIn: 'root' })
export class ScreenExplorationService {
  private http = inject(HttpClient);
  private readonly API_URL = `${environment.apiUrl}/exploration`;

  start(request: StartSessionRequest): Observable<ExplorationSession> {
    return this.http.post<ExplorationSession>(`${this.API_URL}/sessions`, request);
  }

  sessions(page: number, size: number): Observable<PageResponse<ExplorationSession>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<ExplorationSession>>(`${this.API_URL}/sessions`, { params });
  }

  session(id: number): Observable<ExplorationSession> {
    return this.http.get<ExplorationSession>(`${this.API_URL}/sessions/${id}`);
  }

  act(id: number, action: RemoteKey): Observable<ExplorationStep> {
    return this.http.post<ExplorationStep>(`${this.API_URL}/sessions/${id}/actions`, { action });
  }

  stop(id: number): Observable<ExplorationSession> {
    return this.http.post<ExplorationSession>(`${this.API_URL}/sessions/${id}/stop`, {});
  }

  steps(id: number, page: number, size: number): Observable<PageResponse<ExplorationStep>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<ExplorationStep>>(`${this.API_URL}/sessions/${id}/steps`, { params });
  }

  graph(deviceId: string): Observable<ScreenGraph> {
    return this.http.get<ScreenGraph>(`${this.API_URL}/graph`, { params: new HttpParams().set('deviceId', deviceId) });
  }
}
