import { Component } from '@angular/core';
import { SummaryHeaderComponent } from '../../../shared/components/summary/summary-header/summary-header.component';
import { TestOverviewComponent } from '../../../shared/components/summary/test-overview/test-overview.component';
import { PerformanceSummaryComponent } from '../../../shared/components/summary/performance-summary/performance-summary.component';
import { PerformanceTrendsComponent } from '../../../shared/components/summary/performance-trends/performance-trends.component';
import { KeyObservationsComponent } from '../../../shared/components/summary/key-observations/key-observations.component';
import { PerformanceIssuesComponent } from '../../../shared/components/summary/performance-issues/performance-issues.component';
import { BootupTimeKpiComponent } from '../../../shared/components/summary/bootup-time-kpi/bootup-time-kpi.component';
import { FinalVerdictComponent } from '../../../shared/components/summary/final-verdict/final-verdict.component';

@Component({
  selector: 'app-summary',
  imports: [
    SummaryHeaderComponent,
    TestOverviewComponent,
    PerformanceSummaryComponent,
    PerformanceTrendsComponent,
    KeyObservationsComponent,
    PerformanceIssuesComponent,
    BootupTimeKpiComponent,
    FinalVerdictComponent,
  ],
  templateUrl: './summary.component.html',
})
export class SummaryComponent {}