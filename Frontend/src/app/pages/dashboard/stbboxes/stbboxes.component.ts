import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StbService, STBAutomationReport } from '../../../shared/services/stb.service';
import * as XLSX from 'xlsx';

interface STBBox {
  vendor: string;
  model: string;
  resolution: string;
  stbNo: number;
}

@Component({
  selector: 'app-stbboxes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './stbboxes.component.html',
  styleUrls: ['./stbboxes.component.css']
})
export class StbboxesComponent implements OnInit {
  private stbService = inject(StbService);

  stbBoxes: STBBox[] = [];
  filteredStbBoxes: STBBox[] = [];
  searchTerm = '';
  selectedVendor = '';
  selectedResolution = '';
  isLoading = false;
  error: string | null = null;

  vendors: string[] = [];
  resolutions: string[] = [];

  // Report Modal
  showReportModal = false;
  selectedBox: STBBox | null = null;
  boxReports: STBAutomationReport[] = [];
  filteredReports: STBAutomationReport[] = [];
  reportsLoading = false;
  reportsError: string | null = null;
  uploadingReport = false;
  reportSearchTerm = '';
  viewingReport: STBAutomationReport | null = null;

  // Excel Viewer
  showExcelViewer = false;
  excelData: any[][] = [];
  excelSheetNames: string[] = [];
  activeSheet = '';
  excelFileName = '';

  ngOnInit() {
    this.loadStbBoxes();
  }

  loadStbBoxes() {
    this.isLoading = true;
    this.error = null;

    this.stbService.getStbBoxes().subscribe({
      next: (data) => {
        this.stbBoxes = data;
        this.filteredStbBoxes = [...data];
        this.extractFilterOptions();
        this.isLoading = false;
      },
      error: (err) => {
        this.error = 'Failed to load STB boxes. Please try again.';
        this.isLoading = false;
        console.error('Error loading STB boxes:', err);
      }
    });
  }

  extractFilterOptions() {
    this.vendors = [...new Set(this.stbBoxes.map(stb => stb.vendor))].sort();
    this.resolutions = [...new Set(this.stbBoxes.map(stb => stb.resolution))].sort();
  }

  applyFilters() {
    this.filteredStbBoxes = this.stbBoxes.filter(stb => {
      const matchesSearch = this.searchTerm === '' ||
        stb.model.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        stb.vendor.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        stb.stbNo.toString().includes(this.searchTerm);

      const matchesVendor = this.selectedVendor === '' || stb.vendor === this.selectedVendor;
      const matchesResolution = this.selectedResolution === '' || stb.resolution === this.selectedResolution;

      return matchesSearch && matchesVendor && matchesResolution;
    });
  }

  onSearchChange() {
    this.applyFilters();
  }

  onVendorChange() {
    this.applyFilters();
  }

  onResolutionChange() {
    this.applyFilters();
  }

  getStbImage(resolution: string): string {
    switch (resolution.toUpperCase()) {
      case 'HD':
        return '/images/boxes/tata-play-HD-Box.png';
      case 'SD':
        return '/images/boxes/tata-play-SD-Box.png';
      case '4K':
      case 'ANDROID':
        return '/images/boxes/tata-play-binge-plus.png';
      default:
        return '/images/boxes/Android-Box.jpg';
    }
  }

  clearFilters() {
    this.searchTerm = '';
    this.selectedVendor = '';
    this.selectedResolution = '';
    this.applyFilters();
  }

  onImgError(event: Event) {
    (event.target as HTMLImageElement).src = '/images/boxes/Android-Box.jpg';
  }

  // ─── Automation Report Methods ────────────────────────────────────────────
  openReportModal(stb: STBBox) {
    this.selectedBox = stb;
    this.showReportModal = true;
    this.showExcelViewer = false;
    this.excelData = [];
    this.loadReportsForBox(stb.stbNo);
  }

  closeReportModal() {
    this.showReportModal = false;
    this.selectedBox = null;
    this.boxReports = [];
    this.filteredReports = [];
    this.showExcelViewer = false;
    this.excelData = [];
    this.reportSearchTerm = '';
    this.viewingReport = null;
  }

  loadReportsForBox(stbNo: number) {
    this.reportsLoading = true;
    this.reportsError = null;
    this.stbService.getReportsByBox(stbNo).subscribe({
      next: (reports) => {
        this.boxReports = reports;
        this.filteredReports = [...reports];
        this.reportsLoading = false;
      },
      error: () => {
        this.reportsError = 'Failed to load reports.';
        this.boxReports = [];
        this.filteredReports = [];
        this.reportsLoading = false;
      }
    });
  }

  filterReports() {
    const term = this.reportSearchTerm.toLowerCase().trim();
    if (!term) {
      this.filteredReports = [...this.boxReports];
      return;
    }
    this.filteredReports = this.boxReports.filter(r =>
      r.fileName.toLowerCase().includes(term) ||
      r.reportDate?.toLowerCase().includes(term) ||
      r.boxName?.toLowerCase().includes(term)
    );
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length || !this.selectedBox) return;
    const file = input.files[0];
    if (!file.name.endsWith('.xlsx') && !file.name.endsWith('.xls')) {
      this.reportsError = 'Please select an Excel file (.xlsx or .xls)';
      return;
    }
    this.uploadingReport = true;
    this.reportsError = null;
    this.stbService.uploadReport(this.selectedBox.stbNo, this.selectedBox.model, file).subscribe({
      next: () => {
        this.uploadingReport = false;
        this.loadReportsForBox(this.selectedBox!.stbNo);
      },
      error: () => {
        this.reportsError = 'Failed to upload report.';
        this.uploadingReport = false;
      }
    });
    input.value = '';
  }

  viewReport(report: STBAutomationReport) {
    this.reportsLoading = true;
    this.viewingReport = report;
    this.stbService.downloadReport(report.id!).subscribe({
      next: (blob) => {
        const reader = new FileReader();
        reader.onload = (e: any) => {
          const data = new Uint8Array(e.target.result);
          const workbook = XLSX.read(data, { type: 'array' });
          this.excelSheetNames = workbook.SheetNames;
          this.activeSheet = workbook.SheetNames[0];
          this.excelData = XLSX.utils.sheet_to_json(workbook.Sheets[this.activeSheet], { header: 1 });
          this.excelFileName = report.fileName;
          this.showExcelViewer = true;
          this.reportsLoading = false;
        };
        reader.readAsArrayBuffer(blob);
      },
      error: () => {
        this.reportsError = 'Failed to load report file.';
        this.reportsLoading = false;
      }
    });
  }

  switchSheet(sheetName: string, report: STBAutomationReport) {
    this.stbService.downloadReport(report.id!).subscribe({
      next: (blob) => {
        const reader = new FileReader();
        reader.onload = (e: any) => {
          const data = new Uint8Array(e.target.result);
          const workbook = XLSX.read(data, { type: 'array' });
          this.activeSheet = sheetName;
          this.excelData = XLSX.utils.sheet_to_json(workbook.Sheets[sheetName], { header: 1 });
        };
        reader.readAsArrayBuffer(blob);
      }
    });
  }

  downloadReportFile(report: STBAutomationReport) {
    this.stbService.downloadReport(report.id!).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = report.fileName;
        a.click();
        URL.revokeObjectURL(url);
      }
    });
  }

  deleteReport(report: STBAutomationReport) {
    if (!confirm('Are you sure you want to delete this report?')) return;
    this.stbService.deleteReport(report.id!).subscribe({
      next: () => {
        this.loadReportsForBox(this.selectedBox!.stbNo);
        this.showExcelViewer = false;
      }
    });
  }

  closeExcelViewer() {
    this.showExcelViewer = false;
    this.excelData = [];
  }

  hasFailedStatus(row: any[]): boolean {
    return row.some(cell =>
      typeof cell === 'string' && cell.toUpperCase() === 'FAILED'
    );
  }
}