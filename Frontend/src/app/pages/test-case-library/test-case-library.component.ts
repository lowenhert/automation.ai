import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { PageBreadcrumbComponent } from '../../shared/components/common/page-breadcrumb/page-breadcrumb.component';
import { ComponentCardComponent } from '../../shared/components/common/component-card/component-card.component';
import { ModalComponent } from '../../shared/components/ui/modal/modal.component';
import { NotificationService } from '../../shared/services/notification.service';
import { AdbDevice, ScreenLearningAdbService } from '../../shared/services/screen-learning-adb.service';
import { AiTestPlannerService, PlanSummary } from '../../shared/services/ai-test-planner.service';
import {
  DeleteImpact, EVENT_ACTIONS, EventAction, ScriptLanguage, ScriptSummary, TestCaseDetail, TestCaseSummary, TestEvent,
  TestLibraryService
} from '../../shared/services/test-library.service';
import { httpErrorMessage } from '../../shared/utils/http-error';

type RenameTarget = { kind: 'case' | 'script'; id: number; name: string };
type DeleteTarget = { kind: 'case' | 'script'; impact: DeleteImpact };

const KEY_ACTIONS: EventAction[] = ['DPAD_UP', 'DPAD_DOWN', 'DPAD_LEFT', 'DPAD_RIGHT', 'SELECT', 'BACK', 'HOME'];

@Component({
  selector: 'app-test-case-library',
  standalone: true,
  imports: [FormsModule, DatePipe, NgClass, RouterLink, PageBreadcrumbComponent, ComponentCardComponent, ModalComponent],
  templateUrl: './test-case-library.component.html'
})
export class TestCaseLibraryComponent implements OnInit {
  private library = inject(TestLibraryService);
  private planner = inject(AiTestPlannerService);
  private adb = inject(ScreenLearningAdbService);
  private notifications = inject(NotificationService);
  private router = inject(Router);

  readonly actions = EVENT_ACTIONS;

  cases = signal<TestCaseSummary[]>([]);
  search = '';
  loadingCases = signal(false);

  selected = signal<TestCaseDetail | null>(null);
  loadingCase = signal(false);
  saving = signal(false);

  // Editable copies of the selected case
  form = { name: '', description: '', deviceSerial: '' };
  events = signal<TestEvent[]>([]);
  eventsDirty = signal(false);

  devices = signal<AdbDevice[]>([]);
  approvedPlans = signal<PlanSummary[]>([]);
  importPlanId: number | null = null;

  createOpen = signal(false);
  newCase = { name: '', description: '' };

  renameTarget = signal<RenameTarget | null>(null);
  renameValue = '';
  deleteTarget = signal<DeleteTarget | null>(null);
  deleting = signal(false);

  ngOnInit(): void {
    this.loadCases();
    this.adb.getDevices().subscribe({ next: d => this.devices.set(d), error: () => this.devices.set([]) });
    this.planner.list(0, 50, 'APPROVED').subscribe({ next: p => this.approvedPlans.set(p.content), error: () => this.approvedPlans.set([]) });
  }

  // ── Cases ──

  loadCases(): void {
    this.loadingCases.set(true);
    this.library.listCases(0, 100, this.search).pipe(finalize(() => this.loadingCases.set(false)))
      .subscribe({ next: page => this.cases.set(page.content), error: err => this.fail('Could not load test cases', err) });
  }

  open(id: number): void {
    if (this.eventsDirty() && !confirm('Discard unsaved event changes?')) return;
    this.loadingCase.set(true);
    this.library.getCase(id).pipe(finalize(() => this.loadingCase.set(false)))
      .subscribe({ next: c => this.show(c), error: err => this.fail('Could not load test case', err) });
  }

  private show(c: TestCaseDetail): void {
    this.selected.set(c);
    this.form = { name: c.name, description: c.description ?? '', deviceSerial: c.deviceSerial ?? '' };
    this.events.set(c.events.map(e => ({ ...e })));
    this.eventsDirty.set(false);
  }

  create(): void {
    if (!this.newCase.name.trim()) return;
    this.saving.set(true);
    this.library.createCase({ name: this.newCase.name.trim(), description: this.newCase.description.trim() || null })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: c => {
          this.createOpen.set(false);
          this.newCase = { name: '', description: '' };
          this.show(c);
          this.loadCases();
          this.notifications.showSuccess('Test case created', c.name);
        },
        error: err => this.fail('Could not create test case', err)
      });
  }

  saveDetails(): void {
    const c = this.selected();
    if (!c) return;
    this.saving.set(true);
    this.library.updateCase(c.id, {
      name: this.form.name.trim(), description: this.form.description.trim() || null, deviceSerial: this.form.deviceSerial || null
    }).pipe(finalize(() => this.saving.set(false))).subscribe({
      next: updated => { this.selected.set(updated); this.loadCases(); this.notifications.showSuccess('Saved', updated.name); },
      error: err => this.fail('Could not save test case', err)
    });
  }

  duplicate(): void {
    const c = this.selected();
    if (!c) return;
    this.library.duplicateCase(c.id).subscribe({
      next: copy => { this.show(copy); this.loadCases(); this.notifications.showSuccess('Duplicated', `${copy.name} (scripts copied as drafts)`); },
      error: err => this.fail('Could not duplicate', err)
    });
  }

  importPlan(): void {
    if (this.importPlanId == null) return;
    this.saving.set(true);
    this.library.fromPlan(this.importPlanId).pipe(finalize(() => this.saving.set(false))).subscribe({
      next: c => {
        this.importPlanId = null;
        this.show(c);
        this.loadCases();
        this.notifications.showSuccess('Test case created from plan', `${c.events.length} events`);
      },
      error: err => this.fail('Could not import plan', err)
    });
  }

  // ── Events ──

  addEvent(): void {
    this.events.update(list => [...list, { action: 'DPAD_DOWN', target: null, expectedState: null, timeoutMs: 10000 }]);
    this.eventsDirty.set(true);
  }

  removeEvent(index: number): void {
    this.events.update(list => list.filter((_, i) => i !== index));
    this.eventsDirty.set(true);
  }

  moveEvent(index: number, delta: number): void {
    const target = index + delta;
    this.events.update(list => {
      if (target < 0 || target >= list.length) return list;
      const copy = [...list];
      [copy[index], copy[target]] = [copy[target], copy[index]];
      return copy;
    });
    this.eventsDirty.set(true);
  }

  duplicateEvent(index: number): void {
    this.events.update(list => [...list.slice(0, index + 1), { ...list[index], id: undefined }, ...list.slice(index + 1)]);
    this.eventsDirty.set(true);
  }

  markDirty(): void {
    this.eventsDirty.set(true);
  }

  onActionChange(event: TestEvent, action: EventAction): void {
    event.action = action;
    if (this.isKey(action)) event.target = null;
    if (action === 'WAIT' && event.timeoutMs > 30000) event.timeoutMs = 2000;
    this.markDirty();
  }

  isKey(action: EventAction): boolean {
    return KEY_ACTIONS.includes(action);
  }

  needsTarget(action: EventAction): boolean {
    return action.startsWith('ASSERT_');
  }

  targetPlaceholder(action: EventAction): string {
    switch (action) {
      case 'ASSERT_ANY_TEXT_VISIBLE': return 'Text A | Text B';
      case 'SCREENSHOT': return 'Screenshot name (optional)';
      default: return this.needsTarget(action) ? 'Text on screen' : '';
    }
  }

  saveEvents(): void {
    const c = this.selected();
    if (!c) return;
    this.saving.set(true);
    this.library.saveEvents(c.id, this.events()).pipe(finalize(() => this.saving.set(false))).subscribe({
      next: updated => { this.show(updated); this.loadCases(); this.notifications.showSuccess('Events saved', `${updated.events.length} events`); },
      error: err => this.fail('Events not saved', err)
    });
  }

  resetEvents(): void {
    const c = this.selected();
    if (c) this.show(c);
  }

  // ── Scripts ──

  generate(language: ScriptLanguage): void {
    const c = this.selected();
    if (!c) return;
    if (this.eventsDirty()) {
      this.notifications.showWarning('Unsaved events', 'Save the events before generating code');
      return;
    }
    this.saving.set(true);
    this.library.generateScript(c.id, language).pipe(finalize(() => this.saving.set(false))).subscribe({
      next: script => {
        this.notifications.showSuccess('Draft script generated', 'Review, validate and approve it before execution');
        this.router.navigate(['/code-editor'], { queryParams: { scriptId: script.id } });
      },
      error: err => this.fail('Could not generate script', err)
    });
  }

  newScript(language: ScriptLanguage): void {
    const c = this.selected();
    if (!c) return;
    const content = language === 'PYTEST'
      ? 'from automation_ai import Device\n\n\ndef test_example(device: Device):\n    device.press("HOME")\n    device.sleep(1.0)\n    device.screenshot("home")\n'
      : '*** Settings ***\nLibrary    AutomationAiLibrary\n\n*** Test Cases ***\nExample\n    Press Key    HOME\n    Wait Seconds    1.0\n    Take Screenshot    home\n';
    this.library.createScript({ testCaseId: c.id, name: `${c.name} (${language === 'PYTEST' ? 'pytest' : 'robot'} manual)`.slice(0, 150), language, content })
      .subscribe({
        next: s => this.router.navigate(['/code-editor'], { queryParams: { scriptId: s.id } }),
        error: err => this.fail('Could not create script', err)
      });
  }

  duplicateScript(script: ScriptSummary): void {
    this.library.duplicateScript(script.id).subscribe({
      next: () => { this.refreshSelected(); this.notifications.showSuccess('Script duplicated', 'The copy is a draft'); },
      error: err => this.fail('Could not duplicate script', err)
    });
  }

  // ── Rename / delete modals ──

  startRename(kind: 'case' | 'script', id: number, name: string): void {
    this.renameTarget.set({ kind, id, name });
    this.renameValue = name;
  }

  confirmRename(): void {
    const target = this.renameTarget();
    const name = this.renameValue.trim();
    if (!target || !name) return;
    const request = target.kind === 'case' ? this.library.renameCase(target.id, name) : this.library.renameScript(target.id, name);
    request.subscribe({
      next: () => { this.renameTarget.set(null); this.refreshSelected(); this.loadCases(); },
      error: err => this.fail('Rename failed', err)
    });
  }

  startDelete(kind: 'case' | 'script', id: number): void {
    const impact = kind === 'case' ? this.library.caseDeleteImpact(id) : this.library.scriptDeleteImpact(id);
    impact.subscribe({
      next: i => this.deleteTarget.set({ kind, impact: i }),
      error: err => this.fail('Could not check what will be deleted', err)
    });
  }

  confirmDelete(): void {
    const target = this.deleteTarget();
    if (!target || target.impact.running) return;
    this.deleting.set(true);
    const request = target.kind === 'case' ? this.library.deleteCase(target.impact.id) : this.library.deleteScript(target.impact.id);
    request.pipe(finalize(() => this.deleting.set(false))).subscribe({
      next: () => {
        this.deleteTarget.set(null);
        this.notifications.showSuccess('Deleted', target.impact.name);
        if (target.kind === 'case') {
          this.selected.set(null);
          this.eventsDirty.set(false);
        } else {
          this.refreshSelected();
        }
        this.loadCases();
      },
      error: err => this.fail('Delete failed', err)
    });
  }

  private refreshSelected(): void {
    const c = this.selected();
    if (!c) return;
    this.library.getCase(c.id).subscribe({
      next: updated => {
        // Keep unsaved event edits; refresh everything else
        const events = this.eventsDirty() ? this.events() : null;
        this.show(updated);
        if (events) { this.events.set(events); this.eventsDirty.set(true); }
      }
    });
  }

  scriptStatusClass(s: ScriptSummary): string {
    if (s.executable) return 'bg-success-50 text-success-600 dark:bg-success-500/15 dark:text-success-500';
    if (s.validationValid === false) return 'bg-error-50 text-error-600 dark:bg-error-500/15 dark:text-error-500';
    return 'bg-gray-100 text-gray-600 dark:bg-white/5 dark:text-gray-400';
  }

  scriptStatusLabel(s: ScriptSummary): string {
    if (s.executable) return 'Approved';
    if (s.validationValid === false) return 'Invalid';
    if (s.validationValid === true) return 'Validated';
    return 'Draft';
  }

  private fail(title: string, err: unknown): void {
    this.notifications.showError(title, httpErrorMessage(err));
  }
}
