import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { InventoryService, InventoryBox, BoxAssignment, BoxType } from '../../shared/services/inventory.service';
import { UserManagementService } from '../../shared/services/user-management.service';
import { AuthService, User, UserProfile } from '../../shared/services/auth.service';
import { StbService } from '../../shared/services/stb.service';

@Component({
  selector: 'app-inventory-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './inventory-management.component.html'
})
export class InventoryManagementComponent implements OnInit {
  private inventoryService = inject(InventoryService);
  private userService = inject(UserManagementService);
  private authService = inject(AuthService);
  private stbService = inject(StbService);

  // Data
  boxes: InventoryBox[] = [];
  assignments: BoxAssignment[] = [];
  users: UserProfile[] = [];
  currentUser: User | null = null;
  isAdminUser = false;

  // Filters
  searchQuery = '';
  filterType: BoxType | '' = '';
  filterStatus = '';
  filterAssignee = '';
  activeTab: 'boxes' | 'my-boxes' | 'history' | 'report' = 'boxes';

  // Assign flow (user uploads SS first, then admin assigns)
  showAssignScreenshotModal = false;
  assignScreenshotFile: string | null = null;
  pendingAssignBox: InventoryBox | null = null;

  // Reassign (user re-assigns their box to someone else)
  showReassignModal = false;
  reassignAssignment: BoxAssignment | null = null;
  reassignForm = { assignedTo: '', location: '', notes: '' };
  reassignScreenshotFile: string | null = null;
  reassignCustomLocation = '';
  showReassignCustomLocation = false;
  historyUserFilter = '';

  // Modals
  showAddBoxModal = false;
  showAssignModal = false;
  showScreenshotModal = false;
  selectedBox: InventoryBox | null = null;
  selectedAssignment: BoxAssignment | null = null;
  screenshotPreview: string | null = null;

  // Add box form
  addForm = { uniqueId: '', boxType: '' as BoxType | '', location: '', notes: '' };
  locationOptions = ['Witbe Lab', 'Test Lab', 'Binge Lab'];
  showCustomLocation = false;
  customLocation = '';
  boxTypes: string[] = [];

  // Assign form
  assignForm = { assignedTo: '', location: '', notes: '' };

  // Loading
  isLoading = true;

  // Import Excel
  showImportModal = false;
  importFile: File | null = null;
  importPreview: { uniqueId: string; boxType: string; location: string; notes: string }[] = [];
  importError = '';

  ngOnInit(): void {
    this.isAdminUser = this.authService.isAdmin();
    this.authService.currentUser$.subscribe(user => {
      if (user) {
        this.currentUser = user;
        this.isAdminUser = user.role?.toUpperCase() === 'ADMIN';
        // Users default to my-boxes tab
        if (!this.isAdminUser) {
          this.activeTab = 'my-boxes';
        }
      }
    });
    this.loadData();
  }

  loadData(): void {
    this.isLoading = true;
    this.inventoryService.getBoxes().subscribe({
      next: (b) => { this.boxes = b; this.isLoading = false; }
    });
    this.inventoryService.getAssignments().subscribe({
      next: (a) => this.assignments = a
    });
    this.userService.getAllUsers().subscribe({
      next: (res) => this.users = res.rows || [],
      error: () => {}
    });
    // Load box types (models) from STB API
    this.stbService.getStbBoxes().subscribe({
      next: (stbs) => {
        const models = [...new Set(stbs.map(s => s.model).filter(m => !!m))];
        this.boxTypes = models.length ? models : ['Witbe', 'Binge', 'Test Lab'];
      },
      error: () => {
        this.boxTypes = ['Witbe', 'Binge', 'Test Lab'];
      }
    });
  }

  // ─── Filters ────────────────────────────────────────────────────────
  get filteredBoxes(): InventoryBox[] {
    let items = this.boxes;
    if (this.activeTab === 'my-boxes') {
      items = items.filter(b => b.assignedTo === this.currentUser?.username);
    }
    if (this.searchQuery) {
      const q = this.searchQuery.toLowerCase();
      items = items.filter(b =>
        b.uniqueId.toLowerCase().includes(q) ||
        b.location.toLowerCase().includes(q) ||
        b.assignedTo.toLowerCase().includes(q)
      );
    }
    if (this.filterType) {
      items = items.filter(b => b.boxType === this.filterType);
    }
    if (this.filterStatus) {
      items = items.filter(b => b.status === this.filterStatus);
    }
    if (this.filterAssignee) {
      items = items.filter(b => b.assignedTo === this.filterAssignee);
    }
    return items;
  }

  get myActiveAssignments(): BoxAssignment[] {
    return this.assignments.filter(a => a.assignedTo === this.currentUser?.username && a.status === 'ACTIVE');
  }

  get allAssignments(): BoxAssignment[] {
    let items = this.assignments;
    // Non-admin users only see their own history
    if (!this.isAdminUser) {
      items = items.filter(a => a.assignedTo === this.currentUser?.username || a.assignedBy === this.currentUser?.username);
    }
    if (this.filterType) items = items.filter(a => a.boxType === this.filterType);
    if (this.filterAssignee) items = items.filter(a => a.assignedTo === this.filterAssignee);
    if (this.historyUserFilter) items = items.filter(a => a.assignedTo === this.historyUserFilter);
    return items.sort((a, b) => new Date(b.assignedDate).getTime() - new Date(a.assignedDate).getTime());
  }

  get allHistoryUsers(): string[] {
    return [...new Set(this.assignments.map(a => a.assignedTo).filter(u => !!u))];
  }

  get allActiveAssignments(): BoxAssignment[] {
    let items = this.assignments.filter(a => a.status === 'ACTIVE');
    if (this.searchQuery) {
      const q = this.searchQuery.toLowerCase();
      items = items.filter(a =>
        a.boxUniqueId.toLowerCase().includes(q) ||
        a.assignedTo.toLowerCase().includes(q) ||
        a.location.toLowerCase().includes(q)
      );
    }
    if (this.filterType) items = items.filter(a => a.boxType === this.filterType);
    if (this.filterAssignee) items = items.filter(a => a.assignedTo === this.filterAssignee);
    return items.sort((a, b) => new Date(b.assignedDate).getTime() - new Date(a.assignedDate).getTime());
  }

  get assignedUsers(): string[] {
    return [...new Set(this.boxes.filter(b => b.assignedTo).map(b => b.assignedTo))];
  }

  get stats() {
    const src = this.isAdminUser ? this.boxes : this.boxes.filter(b => b.assignedTo === this.currentUser?.username);
    return {
      total: src.length,
      available: src.filter(b => b.status === 'AVAILABLE').length,
      assigned: src.filter(b => b.status === 'ASSIGNED').length
    };
  }

  get typeStats(): { name: string; count: number }[] {
    return this.boxTypes.map(t => ({
      name: t,
      count: this.boxes.filter(b => b.boxType === t).length
    }));
  }

  // ─── Add Box (Admin) ────────────────────────────────────────────────
  openAddBox(): void {
    this.addForm = { uniqueId: '', boxType: '', location: '', notes: '' };
    this.showAddBoxModal = true;
  }

  confirmAddBox(): void {
    if (!this.addForm.uniqueId.trim() || !this.addForm.boxType) return;
    const location = this.addForm.location === 'OTHER' ? this.customLocation.trim() : this.addForm.location.trim();
    const box: InventoryBox = {
      uniqueId: this.addForm.uniqueId.trim(),
      boxType: this.addForm.boxType as BoxType,
      assignedTo: '',
      location: location,
      status: 'AVAILABLE',
      notes: this.addForm.notes.trim()
    };
    this.inventoryService.addBox(box).subscribe({
      next: (added) => {
        this.boxes.push(added);
        this.showAddBoxModal = false;
        this.showCustomLocation = false;
        this.customLocation = '';
      }
    });
  }

  deleteBox(box: InventoryBox): void {
    if (!confirm(`Delete box "${box.uniqueId}"?`)) return;
    this.inventoryService.deleteBox(box.id!).subscribe({
      next: () => { this.boxes = this.boxes.filter(b => b.id !== box.id); }
    });
  }

  // ─── Assign Box (Admin) ─────────────────────────────────────────────
  openAssignModal(box: InventoryBox): void {
    this.selectedBox = box;
    this.assignForm = { assignedTo: '', location: box.location, notes: '' };
    this.assignScreenshotFile = null;
    this.showAssignModal = true;
  }

  onAssignScreenshot(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];
    if (file.size > 5 * 1024 * 1024) {
      alert('File size must be under 5MB');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      this.assignScreenshotFile = reader.result as string;
    };
    reader.readAsDataURL(file);
  }

  confirmAssign(): void {
    if (!this.selectedBox || !this.assignForm.assignedTo) return;
    if (!this.assignScreenshotFile) {
      alert('Please upload a screenshot before assigning.');
      return;
    }
    const location = this.assignForm.location === 'OTHER' ? this.customLocation.trim() : (this.assignForm.location || this.selectedBox.location);

    const assignment: BoxAssignment = {
      boxId: this.selectedBox.id!,
      boxUniqueId: this.selectedBox.uniqueId,
      boxType: this.selectedBox.boxType,
      assignedTo: this.assignForm.assignedTo,
      assignedBy: this.currentUser?.username || '',
      assignedDate: new Date().toISOString(),
      location: location,
      status: 'ACTIVE',
      screenshot: this.assignScreenshotFile,
      notes: this.assignForm.notes
    };

    this.inventoryService.assignBox(assignment).subscribe({
      next: (a) => {
        this.assignments.push(a);
        this.selectedBox!.assignedTo = this.assignForm.assignedTo;
        this.selectedBox!.status = 'ASSIGNED';
        this.selectedBox!.location = location;
        this.inventoryService.updateBox(this.selectedBox!).subscribe();
        const idx = this.boxes.findIndex(b => b.id === this.selectedBox!.id);
        if (idx >= 0) this.boxes[idx] = { ...this.selectedBox! };
        this.showAssignModal = false;
        this.showCustomLocation = false;
        this.customLocation = '';
        this.assignScreenshotFile = null;
      }
    });
  }

  // ─── Return Box ─────────────────────────────────────────────────────
  returnBox(assignment: BoxAssignment): void {
    if (!confirm(`Return box "${assignment.boxUniqueId}" from ${assignment.assignedTo}?`)) return;
    this.inventoryService.returnBox(assignment.id!).subscribe({
      next: () => {
        const aIdx = this.assignments.findIndex(a => a.id === assignment.id);
        if (aIdx >= 0) {
          this.assignments[aIdx].status = 'RETURNED';
          this.assignments[aIdx].returnDate = new Date().toISOString();
        }
        const box = this.boxes.find(b => b.id === assignment.boxId);
        if (box) {
          box.assignedTo = '';
          box.status = 'AVAILABLE';
          this.inventoryService.updateBox(box).subscribe();
        }
      }
    });
  }

  returnBoxDirect(box: InventoryBox): void {
    const assignment = this.assignments.find(a => a.boxId === box.id && a.status === 'ACTIVE');
    if (assignment) {
      this.returnBox(assignment);
    } else {
      box.assignedTo = '';
      box.status = 'AVAILABLE';
      this.inventoryService.updateBox(box).subscribe();
      const idx = this.boxes.findIndex(b => b.id === box.id);
      if (idx >= 0) this.boxes[idx] = { ...box };
    }
  }

  // ─── Screenshot Upload (User) ───────────────────────────────────────
  openScreenshotUpload(assignment: BoxAssignment): void {
    this.selectedAssignment = assignment;
    this.screenshotPreview = assignment.screenshot || null;
    this.showScreenshotModal = true;
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];
    if (file.size > 5 * 1024 * 1024) {
      alert('File size must be under 5MB');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      this.screenshotPreview = reader.result as string;
    };
    reader.readAsDataURL(file);
  }

  confirmScreenshot(): void {
    if (!this.selectedAssignment || !this.screenshotPreview) return;
    this.inventoryService.uploadScreenshot(this.selectedAssignment.id!, this.screenshotPreview).subscribe({
      next: () => {
        const idx = this.assignments.findIndex(a => a.id === this.selectedAssignment!.id);
        if (idx >= 0) this.assignments[idx].screenshot = this.screenshotPreview!;
        this.showScreenshotModal = false;
      }
    });
  }

  // ─── Excel Export ───────────────────────────────────────────────────
  exportBoxes(): void {
    const headers = ['Box Unique ID', 'Box Type', 'Status', 'Assigned To', 'Location', 'Notes', 'Created'];
    const rows = this.filteredBoxes.map(b => [
      b.uniqueId, this.getTypeLabel(b.boxType), b.status,
      b.assignedTo || '—', b.location, b.notes,
      b.createdAt ? new Date(b.createdAt).toLocaleDateString() : '—'
    ]);
    this.downloadCSV('inventory_boxes.csv', headers, rows);
  }

  exportAssignments(): void {
    const headers = ['Box ID', 'Box Type', 'Assigned To', 'Assigned By', 'Date', 'Location', 'Status', 'Return Date', 'Notes'];
    const rows = this.allAssignments.map(a => [
      a.boxUniqueId, this.getTypeLabel(a.boxType), a.assignedTo, a.assignedBy,
      new Date(a.assignedDate).toLocaleDateString(), a.location, a.status,
      a.returnDate ? new Date(a.returnDate).toLocaleDateString() : '—', a.notes
    ]);
    this.downloadCSV('assignment_history.csv', headers, rows);
  }

  exportMyBoxes(): void {
    const headers = ['Box ID', 'Box Type', 'Assigned Date', 'Location', 'Status', 'Has Screenshot', 'Notes'];
    const rows = this.myActiveAssignments.map(a => [
      a.boxUniqueId, this.getTypeLabel(a.boxType),
      new Date(a.assignedDate).toLocaleDateString(), a.location, a.status,
      a.screenshot ? 'Yes' : 'No', a.notes
    ]);
    this.downloadCSV(`my_boxes_${this.currentUser?.username}.csv`, headers, rows);
  }

  private downloadCSV(filename: string, headers: string[], rows: any[][]): void {
    const escapeCsv = (val: any) => {
      const s = String(val ?? '');
      return s.includes(',') || s.includes('"') || s.includes('\n') ? `"${s.replace(/"/g, '""')}"` : s;
    };
    const csv = [headers.map(escapeCsv).join(','), ...rows.map(r => r.map(escapeCsv).join(','))].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  // ─── Import from Excel/CSV ───────────────────────────────────────────
  downloadImportTemplate(): void {
    const csv = 'Box ID,Type,Location,Notes\nSTB-001,Witbe,Witbe Lab,Sample box\nSTB-002,Binge,Binge Lab,\nSTB-003,Test Lab,Test Lab,';
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'box_import_template.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  onImportFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    this.importFile = input.files[0];
    this.importError = '';
    this.importPreview = [];

    const reader = new FileReader();
    reader.onload = () => {
      const text = reader.result as string;
      this.parseCSV(text);
    };
    reader.readAsText(this.importFile);
  }

  private parseCSV(text: string): void {
    const lines = text.split(/\r?\n/).filter(l => l.trim());
    if (lines.length < 2) {
      this.importError = 'File must have a header row and at least one data row.';
      return;
    }

    const headerLine = lines[0].toLowerCase();
    const headers = this.splitCSVLine(lines[0]);
    const headerMap = headers.map(h => h.toLowerCase().trim());

    // Find column indices
    const idIdx = headerMap.findIndex(h => h.includes('box') && h.includes('id') || h === 'id' || h === 'uniqueid' || h === 'box id');
    const typeIdx = headerMap.findIndex(h => h.includes('type'));
    const locIdx = headerMap.findIndex(h => h.includes('location') || h.includes('loc'));
    const notesIdx = headerMap.findIndex(h => h.includes('note') || h.includes('remark'));

    if (idIdx === -1) {
      this.importError = 'Could not find "Box ID" column. Please use the template.';
      return;
    }
    if (typeIdx === -1) {
      this.importError = 'Could not find "Type" column. Please use the template.';
      return;
    }

    const preview: { uniqueId: string; boxType: string; location: string; notes: string }[] = [];
    for (let i = 1; i < lines.length; i++) {
      const cols = this.splitCSVLine(lines[i]);
      const uniqueId = (cols[idIdx] || '').trim();
      const boxType = (cols[typeIdx] || '').trim();
      if (!uniqueId) continue; // skip empty rows

      preview.push({
        uniqueId,
        boxType: boxType || 'Unknown',
        location: locIdx >= 0 ? (cols[locIdx] || '').trim() : '',
        notes: notesIdx >= 0 ? (cols[notesIdx] || '').trim() : ''
      });
    }

    if (preview.length === 0) {
      this.importError = 'No valid rows found in the file.';
      return;
    }

    // Check for duplicates with existing boxes
    const existingIds = new Set(this.boxes.map(b => b.uniqueId.toLowerCase()));
    const dupes = preview.filter(p => existingIds.has(p.uniqueId.toLowerCase()));
    if (dupes.length > 0) {
      this.importError = `${dupes.length} duplicate(s) found: ${dupes.slice(0, 3).map(d => d.uniqueId).join(', ')}${dupes.length > 3 ? '...' : ''}. They will be skipped.`;
      this.importPreview = preview.filter(p => !existingIds.has(p.uniqueId.toLowerCase()));
    } else {
      this.importPreview = preview;
    }
  }

  private splitCSVLine(line: string): string[] {
    const result: string[] = [];
    let current = '';
    let inQuotes = false;
    for (let i = 0; i < line.length; i++) {
      const ch = line[i];
      if (ch === '"') {
        inQuotes = !inQuotes;
      } else if (ch === ',' && !inQuotes) {
        result.push(current);
        current = '';
      } else {
        current += ch;
      }
    }
    result.push(current);
    return result;
  }

  confirmImport(): void {
    if (this.importPreview.length === 0) return;

    let added = 0;
    for (const row of this.importPreview) {
      const box: InventoryBox = {
        uniqueId: row.uniqueId,
        boxType: row.boxType as BoxType,
        assignedTo: '',
        location: row.location,
        status: 'AVAILABLE',
        notes: row.notes
      };
      this.inventoryService.addBox(box).subscribe({
        next: (b) => {
          this.boxes.push(b);
          added++;
          if (added === this.importPreview.length) {
            this.showImportModal = false;
            this.importPreview = [];
            this.importFile = null;
            this.importError = '';
          }
        }
      });
    }
  }

  // ─── Helpers ────────────────────────────────────────────────────────
  getTypeLabel(type: BoxType): string {
    return type || '—';
  }

  getTypeClass(type: BoxType): string {
    const t = (type || '').toLowerCase();
    if (t.includes('witbe')) return 'bg-indigo-50 text-indigo-700 dark:bg-indigo-900/20 dark:text-indigo-400';
    if (t.includes('binge')) return 'bg-pink-50 text-pink-700 dark:bg-pink-900/20 dark:text-pink-400';
    if (t.includes('test')) return 'bg-teal-50 text-teal-700 dark:bg-teal-900/20 dark:text-teal-400';
    return 'bg-gray-50 text-gray-700 dark:bg-gray-800 dark:text-gray-400';
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'AVAILABLE': return 'bg-green-50 text-green-700 dark:bg-green-900/20 dark:text-green-400';
      case 'ASSIGNED': return 'bg-blue-50 text-blue-700 dark:bg-blue-900/20 dark:text-blue-400';
      case 'RETURNED': return 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400';
      case 'ACTIVE': return 'bg-blue-50 text-blue-700 dark:bg-blue-900/20 dark:text-blue-400';
      default: return 'bg-gray-50 text-gray-600';
    }
  }

  // ─── Reassign Box (User re-assigns to another user) ─────────────────
  openReassignModal(assignment: BoxAssignment): void {
    this.reassignAssignment = assignment;
    this.reassignForm = { assignedTo: '', location: assignment.location, notes: '' };
    this.reassignScreenshotFile = null;
    this.reassignCustomLocation = '';
    this.showReassignCustomLocation = false;
    this.showReassignModal = true;
  }

  onReassignScreenshot(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];
    if (file.size > 5 * 1024 * 1024) {
      alert('File size must be under 5MB');
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      this.reassignScreenshotFile = reader.result as string;
    };
    reader.readAsDataURL(file);
  }

  confirmReassign(): void {
    if (!this.reassignAssignment || !this.reassignForm.assignedTo) return;
    if (!this.reassignScreenshotFile) {
      alert('Please upload a screenshot before reassigning.');
      return;
    }
    const location = this.reassignForm.location === 'OTHER' ? this.reassignCustomLocation.trim() : (this.reassignForm.location || this.reassignAssignment.location);

    // Mark old assignment as returned
    const oldIdx = this.assignments.findIndex(a => a.id === this.reassignAssignment!.id);
    if (oldIdx >= 0) {
      this.assignments[oldIdx].status = 'RETURNED';
      this.assignments[oldIdx].returnDate = new Date().toISOString();
    }
    this.inventoryService.saveAssignments(this.assignments);

    // Create new assignment
    const box = this.boxes.find(b => b.id === this.reassignAssignment!.boxId);
    const newAssignment: BoxAssignment = {
      boxId: this.reassignAssignment.boxId,
      boxUniqueId: this.reassignAssignment.boxUniqueId,
      boxType: this.reassignAssignment.boxType,
      assignedTo: this.reassignForm.assignedTo,
      assignedBy: this.currentUser?.username || '',
      assignedDate: new Date().toISOString(),
      location: location,
      status: 'ACTIVE',
      screenshot: this.reassignScreenshotFile,
      notes: this.reassignForm.notes
    };

    this.inventoryService.assignBox(newAssignment).subscribe({
      next: (a) => {
        this.assignments.push(a);
        if (box) {
          box.assignedTo = this.reassignForm.assignedTo;
          box.location = location;
          this.inventoryService.updateBox(box).subscribe();
          const bIdx = this.boxes.findIndex(b => b.id === box.id);
          if (bIdx >= 0) this.boxes[bIdx] = { ...box };
        }
        this.showReassignModal = false;
        this.reassignScreenshotFile = null;
      }
    });
  }

  getBoxAssignedTime(box: InventoryBox): string {
    if (box.status !== 'ASSIGNED') return '—';
    const assignment = this.assignments.find(a => a.boxId === box.id && a.status === 'ACTIVE');
    if (assignment) {
      return new Date(assignment.assignedDate).toLocaleString();
    }
    return '—';
  }

  viewBoxScreenshot(box: InventoryBox): void {
    const assignment = this.assignments.find(a => a.boxId === box.id && a.status === 'ACTIVE');
    if (assignment) {
      this.openScreenshotUpload(assignment);
    }
  }
}
