import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  NgApexchartsModule,
  ApexAxisChartSeries,
  ApexNonAxisChartSeries,
  ApexChart,
  ApexXAxis,
  ApexYAxis,
  ApexStroke,
  ApexFill,
  ApexLegend,
  ApexGrid,
  ApexDataLabels,
  ApexTooltip,
  ApexPlotOptions,
  ApexResponsive
} from 'ng-apexcharts';
import { Subscription, combineLatest } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { OttService, OttAutomationTestResult } from '../../../shared/services/ott.service';
import { SidebarService } from '../../../shared/services/sidebar.service';
import { DatePickerComponent } from '../../../shared/components/form/date-picker/date-picker.component';
import * as XLSX from 'xlsx';

@Component({
  selector: 'app-ott-report',
  standalone: true,
  imports: [CommonModule, FormsModule, NgApexchartsModule, DatePickerComponent],
  templateUrl: './ott-report.component.html'
})
export class OttReportComponent implements OnInit, OnDestroy {

  private subs = new Subscription();

  // ─── State ────────────────────────────────────────────────────────────────
  allResults: OttAutomationTestResult[] = [];
  filteredResults: OttAutomationTestResult[] = [];
  isLoading = false;
  error: string | null = null;

  // ─── Summary Stats ────────────────────────────────────────────────────────
  totalStreams = 0;
  successRate = '0%';
  failedStreams = 0;
  avgPlaytime = '0.0s';

  // ─── Filter Fields ────────────────────────────────────────────────────────
  filterApp = '';
  filterStatus = '';
  filterContentName = '';
  filterErrorType = '';
  filterDeviceId = '';
  filterDate = '';

  appOptions: string[] = [];
  errorTypeOptions: string[] = [];

  // ─── Chart Date Range Filter ──────────────────────────────────────────
  chartDaysFilter: number = 15;
  chartDaysOptions = [
    { label: 'Last 7 Days', value: 7 },
    { label: 'Last 15 Days', value: 15 },
    { label: 'Last 30 Days', value: 30 },
    { label: 'Last 60 Days', value: 60 },
    { label: 'All Days', value: 0 }
  ];

  // ─── Table / Pagination ───────────────────────────────────────────────────
  pageSize = 10;
  currentPage = 1;
  selectedIds = new Set<number>();
  selectAll = false;

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredResults.length / this.pageSize));
  }

  get pagedResults(): OttAutomationTestResult[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredResults.slice(start, start + this.pageSize);
  }

  get pageNumbers(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i + 1);
  }

  // ─── Today's Date ─────────────────────────────────────────────────────────
  todayFormatted = new Date().toLocaleDateString('en-US', {
    month: 'long', day: 'numeric', year: 'numeric'
  }).toUpperCase();

  // ─── Chart: Daily Stream Attempts (Area) ──────────────────────────────────
  streamSeries: ApexAxisChartSeries = [];
  streamChart: ApexChart = {
    type: 'area', height: 240, width: '100%', toolbar: { show: false },
    fontFamily: 'Outfit, sans-serif', parentHeightOffset: 0
  };
  streamXaxis: ApexXAxis = {
    categories: [],
    labels: { style: { fontSize: '11px', colors: '#9ca3af' } },
    axisBorder: { show: false }, axisTicks: { show: false }
  };
  streamYaxis: ApexYAxis = {
    min: 0,
    labels: { style: { colors: '#9ca3af', fontSize: '11px' }, formatter: (v) => Math.floor(v).toString() }
  };
  streamStroke: ApexStroke = { curve: 'smooth', width: [2, 2] };
  streamFill: ApexFill = { type: 'gradient', gradient: { opacityFrom: 0.35, opacityTo: 0.03 } };
  streamColors = ['#465FFF', '#F04438'];
  streamLegend: ApexLegend = { show: false };
  streamGrid: ApexGrid = { strokeDashArray: 4, borderColor: '#e5e7eb', yaxis: { lines: { show: true } }, xaxis: { lines: { show: false } } };
  streamDataLabels: ApexDataLabels = { enabled: false };
  streamTooltip: ApexTooltip = { theme: 'light' };

  // ─── Chart: Status Breakdown (Donut) ──────────────────────────────────────
  donutSeries: ApexNonAxisChartSeries = [];
  donutChart: ApexChart = {
    type: 'donut', height: 240, width: '100%',
    fontFamily: 'Outfit, sans-serif', parentHeightOffset: 0
  };
  donutLabels: string[] = [];
  donutColors: string[] = [];
  donutLegend: ApexLegend = { position: 'bottom', fontSize: '12px', offsetY: 4 };
  donutResponsive: ApexResponsive[] = [{ breakpoint: 480, options: { chart: { height: 180 } } }];
  donutPlotOptions: ApexPlotOptions = {
    pie: { donut: { size: '65%', labels: { show: true, total: { show: true, fontSize: '13px', fontWeight: 600 } } } }
  };

  // ─── Chart: Top Errors by Type (Bar) ──────────────────────────────────────
  errorBarSeries: ApexAxisChartSeries = [{ name: 'Count', data: [] }];
  errorBarChart: ApexChart = {
    type: 'bar', height: 240, width: '100%', toolbar: { show: false },
    fontFamily: 'Outfit, sans-serif', parentHeightOffset: 0
  };
  errorBarXaxis: ApexXAxis = {
    categories: [],
    labels: { style: { fontSize: '11px', colors: '#9ca3af' } },
    axisBorder: { show: false }, axisTicks: { show: false }
  };
  errorBarYaxis: ApexYAxis = {
    min: 0,
    labels: { style: { colors: '#9ca3af', fontSize: '11px' }, formatter: (v) => Math.floor(v).toString() }
  };
  errorBarColors = ['#465FFF', '#F04438', '#F79009', '#A855F7', '#06B6D4', '#9CB9FF'];
  errorBarPlotOptions: ApexPlotOptions = {
    bar: { distributed: true, columnWidth: '50%', borderRadius: 4 }
  };
  errorBarDataLabels: ApexDataLabels = { enabled: false };
  errorBarLegend: ApexLegend = { show: false };
  errorBarGrid: ApexGrid = { strokeDashArray: 4, borderColor: '#e5e7eb', yaxis: { lines: { show: true } }, xaxis: { lines: { show: false } } };

  // ─── Chart: Avg Playback Duration (Line) ──────────────────────────────────
  avgDurSeries: ApexAxisChartSeries = [];
  avgDurChart: ApexChart = {
    type: 'area', height: 240, width: '100%', toolbar: { show: false },
    fontFamily: 'Outfit, sans-serif', parentHeightOffset: 0
  };
  avgDurXaxis: ApexXAxis = {
    categories: [],
    labels: { style: { fontSize: '11px', colors: '#9ca3af' } },
    axisBorder: { show: false }, axisTicks: { show: false }
  };
  avgDurYaxis: ApexYAxis = {
    min: 0,
    labels: { style: { colors: '#9ca3af', fontSize: '11px' }, formatter: (v: number) => v.toFixed(0) + 's' }
  };
  avgDurStroke: ApexStroke = { curve: 'smooth', width: 2 };
  avgDurFill: ApexFill = { type: 'gradient', gradient: { opacityFrom: 0.3, opacityTo: 0.03 } };
  avgDurColors = ['#465FFF'];
  avgDurGrid: ApexGrid = { strokeDashArray: 4, borderColor: '#e5e7eb', yaxis: { lines: { show: true } }, xaxis: { lines: { show: false } } };
  avgDurDataLabels: ApexDataLabels = { enabled: false };
  avgDurMarkers = { size: 4, colors: ['#fff'], strokeColors: '#465FFF', strokeWidth: 2, hover: { size: 6 } };

  constructor(
    private ottService: OttService,
    private sidebarService: SidebarService
  ) {}

  ngOnInit(): void {
    this.loadData();
    // Trigger ApexCharts resize after sidebar animation ends
    this.subs.add(
      combineLatest([
        this.sidebarService.isExpanded$,
        this.sidebarService.isHovered$,
        this.sidebarService.isMobileOpen$
      ]).pipe(debounceTime(320)).subscribe(() => {
        window.dispatchEvent(new Event('resize'));
      })
    );
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  // ─── Data Loading ─────────────────────────────────────────────────────────
  loadData(): void {
    this.isLoading = true;
    this.error = null;
    this.ottService.getAll().subscribe({
      next: (data) => {
        this.allResults = data.sort((a, b) =>
          (b.timestamp ?? '').localeCompare(a.timestamp ?? '')
        );
        this.filteredResults = [...this.allResults];
        this.extractFilterOptions();
        this.computeSummary();
        this.buildCharts();
        this.isLoading = false;
      },
      error: () => {
        this.error = 'Failed to load OTT results. Please try again.';
        this.isLoading = false;
      }
    });
  }

  // ─── Filter Options ───────────────────────────────────────────────────────
  extractFilterOptions(): void {
    this.appOptions = [...new Set(this.allResults.map(r => r.ottAppName))].sort();
    this.errorTypeOptions = [...new Set(
      this.allResults.map(r => r.errorType ?? '').filter(Boolean)
    )].sort();
  }

  // ─── Summary ──────────────────────────────────────────────────────────────
  computeSummary(): void {
    const src = this.filteredResults;
    this.totalStreams = src.length;
    const passedCount = src.filter(r => r.status?.toUpperCase() === 'PASSED').length;
    this.failedStreams = src.filter(r => r.status?.toUpperCase() === 'FAILED').length;
    this.successRate = src.length
      ? ((passedCount / src.length) * 100).toFixed(1) + '%'
      : '0%';
    const avgSecs = src.length
      ? src.reduce((s, r) => s + (r.playbackTimeSeconds ?? 0), 0) / src.length
      : 0;
    this.avgPlaytime = avgSecs.toFixed(1) + 's';
  }

  // ─── Chart Days Filter Handler ────────────────────────────────────────
  onChartDaysFilterChange(): void {
    this.buildCharts();
  }

  private getChartFilteredResults(): OttAutomationTestResult[] {
    if (this.chartDaysFilter === 0) return this.filteredResults;
    const cutoff = new Date();
    cutoff.setDate(cutoff.getDate() - this.chartDaysFilter);
    const cutoffStr = cutoff.toISOString().substring(0, 10);
    return this.filteredResults.filter(r =>
      r.timestamp && r.timestamp.substring(0, 10) >= cutoffStr
    );
  }

  // ─── Charts ───────────────────────────────────────────────────────────────
  buildCharts(): void {
    this.buildStreamAttemptsChart();
    this.buildStatusDonutChart();
    this.buildErrorBarChart();
    this.buildAvgDurationChart();
  }

  private groupByDate(results: OttAutomationTestResult[]): Map<string, OttAutomationTestResult[]> {
    const map = new Map<string, OttAutomationTestResult[]>();
    results.forEach(r => {
      const dateKey = r.timestamp ? r.timestamp.substring(0, 10) : 'Unknown';
      if (!map.has(dateKey)) map.set(dateKey, []);
      map.get(dateKey)!.push(r);
    });
    return new Map([...map.entries()].sort());
  }

  private dateLabel(key: string): string {
    const p = key.split('-');
    if (p.length === 3) {
      const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
      return (months[parseInt(p[1]) - 1] ?? p[1]) + ' ' + p[2].replace(/^0/, '');
    }
    return key;
  }

  private buildStreamAttemptsChart(): void {
    const chartData = this.getChartFilteredResults();
    const grouped = this.groupByDate(chartData);
    const dates = [...grouped.keys()].map(d => this.dateLabel(d));
    const passedData = [...grouped.values()].map(arr =>
      arr.filter(r => r.status?.toUpperCase() === 'PASSED').length
    );
    const failedData = [...grouped.values()].map(arr =>
      arr.filter(r => r.status?.toUpperCase() === 'FAILED').length
    );
    this.streamSeries = [
      { name: 'Passed', data: passedData },
      { name: 'Failed', data: failedData }
    ];
    this.streamXaxis = {
      ...this.streamXaxis,
      categories: dates,
      labels: {
        ...this.streamXaxis.labels,
        rotate: dates.length > 15 ? -45 : 0,
        rotateAlways: dates.length > 15
      }
    };
  }

  private buildStatusDonutChart(): void {
    const chartData = this.getChartFilteredResults();
    const colorMap: Record<string, string> = {
      PASSED: '#12B76A', FAILED: '#F04438', BUFFERING: '#F79009', ERROR_MESSAGE: '#A855F7'
    };
    const statusMap = new Map<string, number>();
    chartData.forEach(r => {
      const s = (r.status ?? 'UNKNOWN').toUpperCase();
      statusMap.set(s, (statusMap.get(s) ?? 0) + 1);
    });
    const labels = [...statusMap.keys()];
    this.donutLabels = labels;
    this.donutSeries = [...statusMap.values()];
    this.donutColors = labels.map(l => colorMap[l] ?? '#9CB9FF');
  }

  private buildErrorBarChart(): void {
    const chartData = this.getChartFilteredResults();
    const errorMap = new Map<string, number>();
    chartData.forEach(r => {
      if (r.errorType) {
        errorMap.set(r.errorType, (errorMap.get(r.errorType) ?? 0) + 1);
      }
    });
    const sorted = [...errorMap.entries()].sort((a, b) => b[1] - a[1]).slice(0, 6);
    this.errorBarXaxis = { ...this.errorBarXaxis, categories: sorted.map(e => e[0]) };
    this.errorBarSeries = [{ name: 'Count', data: sorted.map(e => e[1]) }];
  }

  private buildAvgDurationChart(): void {
    const chartData = this.getChartFilteredResults();
    const grouped = this.groupByDate(chartData);
    const dates = [...grouped.keys()].map(d => this.dateLabel(d));
    const avgData = [...grouped.values()].map(arr => {
      const total = arr.reduce((s, r) => s + (r.playbackTimeSeconds ?? 0), 0);
      return arr.length ? Math.round(total / arr.length) : 0;
    });
    this.avgDurSeries = [{ name: 'Avg Duration (s)', data: avgData }];
    this.avgDurXaxis = {
      ...this.avgDurXaxis,
      categories: dates,
      labels: {
        ...this.avgDurXaxis.labels,
        rotate: dates.length > 15 ? -45 : 0,
        rotateAlways: dates.length > 15
      }
    };
  }

  // ─── Filtering ────────────────────────────────────────────────────────────
  applyFilters(): void {
    this.filteredResults = this.allResults.filter(r => {
      const matchApp = !this.filterApp || r.ottAppName === this.filterApp;
      const matchStatus = !this.filterStatus || r.status?.toUpperCase() === this.filterStatus.toUpperCase();
      const matchContent = !this.filterContentName ||
        r.contentName?.toLowerCase().includes(this.filterContentName.toLowerCase());
      const matchError = !this.filterErrorType ||
        (r.errorType ?? '').toLowerCase().includes(this.filterErrorType.toLowerCase());
      const matchDevice = !this.filterDeviceId ||
        r.deviceId?.toLowerCase().includes(this.filterDeviceId.toLowerCase());
      const matchDate = !this.filterDate || (r.timestamp && r.timestamp.startsWith(this.filterDate));
      return matchApp && matchStatus && matchContent && matchError && matchDevice && matchDate;
    });
    this.currentPage = 1;
    this.selectedIds.clear();
    this.selectAll = false;
    this.computeSummary();
    this.buildCharts();
  }

  resetFilters(): void {
    this.filterApp = '';
    this.filterStatus = '';
    this.filterContentName = '';
    this.filterErrorType = '';
    this.filterDeviceId = '';
    this.filterDate = '';
    this.filteredResults = [...this.allResults];
    this.currentPage = 1;
    this.selectedIds.clear();
    this.selectAll = false;
    this.computeSummary();
    this.buildCharts();
  }

  // ─── Selection ────────────────────────────────────────────────────────────
  toggleSelectAll(): void {
    if (this.selectAll) {
      this.pagedResults.forEach(r => { if (r.id != null) this.selectedIds.add(r.id); });
    } else {
      this.pagedResults.forEach(r => { if (r.id != null) this.selectedIds.delete(r.id); });
    }
  }

  toggleRowSelect(id: number | undefined): void {
    if (id == null) return;
    if (this.selectedIds.has(id)) {
      this.selectedIds.delete(id);
    } else {
      this.selectedIds.add(id);
    }
    this.selectAll = this.pagedResults.every(r => r.id != null && this.selectedIds.has(r.id));
  }

  isRowSelected(id: number | undefined): boolean {
    return id != null && this.selectedIds.has(id);
  }

  // ─── Bulk Delete ──────────────────────────────────────────────────────────
  deleteSelected(): void {
    if (this.selectedIds.size === 0) return;
    const ids = [...this.selectedIds];
    let completed = 0;
    ids.forEach(id => {
      this.ottService.delete(id).subscribe({
        next: () => {
          completed++;
          if (completed === ids.length) {
            this.selectedIds.clear();
            this.selectAll = false;
            this.loadData();
          }
        },
        error: () => { completed++; }
      });
    });
  }

  // ─── Single Delete ────────────────────────────────────────────────────────
  deleteRow(id: number | undefined): void {
    if (id == null) return;
    this.ottService.delete(id).subscribe({ next: () => this.loadData() });
  }

  // ─── Pagination ───────────────────────────────────────────────────────────
  goToPage(page: number): void {
    if (page < 1 || page > this.totalPages) return;
    this.currentPage = page;
    this.selectAll = false;
  }

  // ─── Helpers ─────────────────────────────────────────────────────────────
  minVal(a: number, b: number): number {
    return Math.min(a, b);
  }

  // ─── Date Picker Handler ──────────────────────────────────────────────────
  onDateChange(event: { selectedDates: Date[]; dateStr: string }): void {
    this.filterDate = event.dateStr || '';
  }

  // ─── Excel Download ───────────────────────────────────────────────────────
  downloadExcel(): void {
    const wb = XLSX.utils.book_new();

    const autoWidth = (ws: XLSX.WorkSheet): void => {
      const range = XLSX.utils.decode_range(ws['!ref'] || 'A1');
      const colWidths: number[] = [];
      for (let c = range.s.c; c <= range.e.c; c++) {
        let max = 10;
        for (let r = range.s.r; r <= range.e.r; r++) {
          const addr = XLSX.utils.encode_cell({ r, c });
          if (ws[addr]) {
            const len = String(ws[addr].v ?? '').length + 3;
            if (len > max) max = len;
          }
        }
        colWidths.push(Math.min(max, 45));
      }
      ws['!cols'] = colWidths.map(w => ({ wch: w }));
    };

    // --- Data Sheet ---
    const rows = this.filteredResults.map(r => ({
      'OTT App Name': r.ottAppName,
      'Content Name': r.contentName,
      'Playtime (s)': r.playbackTimeSeconds,
      'Timestamp': r.timestamp,
      'Device ID': r.deviceId,
      'Status': r.status,
      'Error Type': r.errorType ?? '',
      'Error Message': r.errorMessage ?? '',
      'Failed Step': r.failedStep ?? 0
    }));
    const wsData = XLSX.utils.json_to_sheet(rows);
    autoWidth(wsData);
    XLSX.utils.book_append_sheet(wb, wsData, 'OTT Results');

    // --- Summary Sheet ---
    const summaryRows = [
      { Metric: 'Total Streams', Value: this.totalStreams },
      { Metric: 'Success Rate', Value: this.successRate },
      { Metric: 'Failed Streams', Value: this.failedStreams },
      { Metric: 'Avg Playtime', Value: this.avgPlaytime }
    ];
    const wsSummary = XLSX.utils.json_to_sheet(summaryRows);
    wsSummary['!cols'] = [{ wch: 20 }, { wch: 18 }];
    XLSX.utils.book_append_sheet(wb, wsSummary, 'Summary');

    // --- Daily Stream Attempts Sheet ---
    const grouped = this.groupByDatePublic(this.filteredResults);
    const chartRows = [...grouped.entries()].map(([dateKey, items]) => ({
      'Date': this.dateLabel(dateKey),
      'Passed': items.filter(r => r.status?.toUpperCase() === 'PASSED').length,
      'Failed': items.filter(r => r.status?.toUpperCase() === 'FAILED').length,
      'Total': items.length
    }));
    const wsChart = XLSX.utils.json_to_sheet(chartRows);
    autoWidth(wsChart);
    XLSX.utils.book_append_sheet(wb, wsChart, 'Daily Stream Attempts');

    // --- Status Breakdown Sheet ---
    const statusMap = new Map<string, number>();
    this.filteredResults.forEach(r => {
      const s = (r.status ?? 'UNKNOWN').toUpperCase();
      statusMap.set(s, (statusMap.get(s) ?? 0) + 1);
    });
    const statusRows = [...statusMap.entries()].map(([status, count]) => ({
      'Status': status, 'Count': count
    }));
    const wsStatus = XLSX.utils.json_to_sheet(statusRows);
    autoWidth(wsStatus);
    XLSX.utils.book_append_sheet(wb, wsStatus, 'Status Breakdown');

    // --- Error Breakdown Sheet ---
    const errorMap = new Map<string, number>();
    this.filteredResults.forEach(r => {
      if (r.errorType) {
        errorMap.set(r.errorType, (errorMap.get(r.errorType) ?? 0) + 1);
      }
    });
    const errorRows = [...errorMap.entries()]
      .sort((a, b) => b[1] - a[1])
      .map(([errorType, count]) => ({ 'Error Type': errorType, 'Count': count }));
    const wsError = XLSX.utils.json_to_sheet(errorRows);
    autoWidth(wsError);
    XLSX.utils.book_append_sheet(wb, wsError, 'Top Errors');

    // --- Avg Duration Sheet ---
    const avgRows = [...grouped.entries()].map(([dateKey, items]) => {
      const total = items.reduce((s, r) => s + (r.playbackTimeSeconds ?? 0), 0);
      return {
        'Date': this.dateLabel(dateKey),
        'Avg Duration (s)': items.length ? Math.round(total / items.length) : 0
      };
    });
    const wsAvg = XLSX.utils.json_to_sheet(avgRows);
    autoWidth(wsAvg);
    XLSX.utils.book_append_sheet(wb, wsAvg, 'Avg Playback Duration');

    XLSX.writeFile(wb, `OTT_Report_${new Date().toISOString().substring(0, 10)}.xlsx`);
  }

  groupByDatePublic(results: OttAutomationTestResult[]): Map<string, OttAutomationTestResult[]> {
    return this.groupByDate(results);
  }

  // ─── Status Badge ─────────────────────────────────────────────────────────
  statusBadgeClass(status: string | undefined): string {
    switch (status?.toUpperCase()) {
      case 'PASSED':        return 'text-green-500';
      case 'FAILED':        return 'text-red-500';
      case 'BUFFERING':     return 'text-amber-500';
      case 'ERROR_MESSAGE': return 'text-purple-500';
      default:              return 'text-gray-400';
    }
  }
}
