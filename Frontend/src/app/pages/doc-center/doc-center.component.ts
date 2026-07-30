import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DocCenterService, DocItem, DocFile, DocFolder } from '../../shared/services/doc-center.service';
import { AuthService, User } from '../../shared/services/auth.service';

@Component({
  selector: 'app-doc-center',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './doc-center.component.html'
})
export class DocCenterComponent implements OnInit {
  private docService = inject(DocCenterService);
  private authService = inject(AuthService);

  currentUser: User | null = null;
  isAdminUser = false;

  // Navigation
  currentFolderId: number | null = null;
  breadcrumb: { id: number | null; name: string }[] = [{ id: null, name: 'Root' }];
  items: DocItem[] = [];

  // View
  viewMode: 'grid' | 'list' = 'grid';
  searchQuery = '';
  searchResults: DocItem[] = [];
  isSearching = false;

  // Modals
  showNewFolderModal = false;
  showUploadModal = false;
  showRenameModal = false;
  showMoveModal = false;
  showPreviewModal = false;

  // Forms
  newFolderName = '';
  renameValue = '';
  selectedItem: DocItem | null = null;
  previewFile: DocFile | null = null;

  // Upload
  uploadFiles: { name: string; size: number; mimeType: string; data: string }[] = [];

  // Move
  moveTarget: number | null = null;
  moveFolders: DocFolder[] = [];

  ngOnInit(): void {
    this.authService.currentUser$.subscribe(user => {
      if (user) {
        this.currentUser = user;
        this.isAdminUser = user.role?.toUpperCase() === 'ADMIN';
      }
    });
    this.loadItems();
  }

  loadItems(): void {
    this.items = this.docService.getItemsByParent(this.currentFolderId);
    this.breadcrumb = this.docService.getBreadcrumb(this.currentFolderId);
    this.sortItems();
  }

  private sortItems(): void {
    // Folders first, then files, alphabetical
    this.items.sort((a, b) => {
      if (a.type !== b.type) return a.type === 'folder' ? -1 : 1;
      return a.name.localeCompare(b.name);
    });
  }

  // ─── Navigation ─────────────────────────────────────────────────────
  openFolder(folder: DocFolder): void {
    this.currentFolderId = folder.id;
    this.isSearching = false;
    this.searchQuery = '';
    this.loadItems();
  }

  navigateTo(crumb: { id: number | null; name: string }): void {
    this.currentFolderId = crumb.id;
    this.isSearching = false;
    this.searchQuery = '';
    this.loadItems();
  }

  goUp(): void {
    if (this.breadcrumb.length > 1) {
      const parent = this.breadcrumb[this.breadcrumb.length - 2];
      this.currentFolderId = parent.id;
      this.loadItems();
    }
  }

  // ─── Search ─────────────────────────────────────────────────────────
  onSearch(): void {
    if (this.searchQuery.trim()) {
      this.isSearching = true;
      this.searchResults = this.docService.searchItems(this.searchQuery);
    } else {
      this.isSearching = false;
      this.searchResults = [];
    }
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.isSearching = false;
    this.searchResults = [];
  }

  // ─── Create Folder ──────────────────────────────────────────────────
  openNewFolder(): void {
    this.newFolderName = '';
    this.showNewFolderModal = true;
  }

  confirmNewFolder(): void {
    if (!this.newFolderName.trim()) return;
    this.docService.createFolder(
      this.newFolderName.trim(),
      this.currentFolderId,
      this.currentUser?.username || ''
    );
    this.showNewFolderModal = false;
    this.loadItems();
  }

  // ─── Upload Files ───────────────────────────────────────────────────
  openUpload(): void {
    this.uploadFiles = [];
    this.showUploadModal = true;
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;

    for (let i = 0; i < input.files.length; i++) {
      const file = input.files[i];
      if (file.size > 50 * 1024 * 1024) {
        alert(`File "${file.name}" exceeds 50MB limit.`);
        continue;
      }
      const reader = new FileReader();
      reader.onload = () => {
        this.uploadFiles.push({
          name: file.name,
          size: file.size,
          mimeType: file.type || 'application/octet-stream',
          data: reader.result as string
        });
      };
      reader.readAsDataURL(file);
    }
  }

  removeUploadFile(index: number): void {
    this.uploadFiles.splice(index, 1);
  }

  confirmUpload(): void {
    if (this.uploadFiles.length === 0) return;
    for (const f of this.uploadFiles) {
      const doc: DocFile = {
        id: 0,
        name: f.name,
        type: 'file',
        mimeType: f.mimeType,
        size: f.size,
        data: f.data,
        parentId: this.currentFolderId,
        uploadedBy: this.currentUser?.username || '',
        uploadedAt: '',
        updatedAt: ''
      };
      this.docService.uploadFile(doc);
    }
    this.showUploadModal = false;
    this.uploadFiles = [];
    this.loadItems();
  }

  // ─── Download ───────────────────────────────────────────────────────
  downloadFile(file: DocFile): void {
    if (!file.data) {
      alert('File data not available.');
      return;
    }
    const a = document.createElement('a');
    a.href = file.data;
    a.download = file.name;
    a.click();
  }

  // ─── Preview ────────────────────────────────────────────────────────
  openPreview(file: DocFile): void {
    this.previewFile = file;
    this.showPreviewModal = true;
  }

  isPreviewable(file: DocFile): boolean {
    return file.mimeType.startsWith('image/') || file.mimeType === 'application/pdf' || file.mimeType.startsWith('text/');
  }

  // ─── Rename ─────────────────────────────────────────────────────────
  openRename(item: DocItem): void {
    this.selectedItem = item;
    this.renameValue = item.name;
    this.showRenameModal = true;
  }

  confirmRename(): void {
    if (!this.selectedItem || !this.renameValue.trim()) return;
    this.docService.renameItem(this.selectedItem.id, this.renameValue.trim());
    this.showRenameModal = false;
    this.loadItems();
  }

  // ─── Delete ─────────────────────────────────────────────────────────
  deleteItem(item: DocItem): void {
    const msg = item.type === 'folder'
      ? `Delete folder "${item.name}" and all its contents?`
      : `Delete file "${item.name}"?`;
    if (!confirm(msg)) return;
    this.docService.deleteItem(item.id);
    this.loadItems();
  }

  // ─── Move ──────────────────────────────────────────────────────────
  openMove(item: DocItem): void {
    this.selectedItem = item;
    this.moveTarget = this.currentFolderId;
    // Get all folders except the item itself and its children
    const allItems = this.docService.getItems();
    this.moveFolders = allItems.filter(i => i.type === 'folder' && i.id !== item.id) as DocFolder[];
    this.showMoveModal = true;
  }

  confirmMove(): void {
    if (!this.selectedItem) return;
    this.docService.moveItem(this.selectedItem.id, this.moveTarget);
    this.showMoveModal = false;
    this.loadItems();
  }

  // ─── Helpers ────────────────────────────────────────────────────────
  getFileIcon(mimeType: string): string {
    if (mimeType.startsWith('image/')) return '🖼️';
    if (mimeType === 'application/pdf') return '📄';
    if (mimeType.includes('spreadsheet') || mimeType.includes('excel') || mimeType.includes('csv')) return '📊';
    if (mimeType.includes('document') || mimeType.includes('word')) return '📝';
    if (mimeType.includes('presentation') || mimeType.includes('powerpoint')) return '📽️';
    if (mimeType.startsWith('video/')) return '🎬';
    if (mimeType.startsWith('audio/')) return '🎵';
    if (mimeType.includes('zip') || mimeType.includes('rar') || mimeType.includes('tar')) return '📦';
    if (mimeType.startsWith('text/')) return '📃';
    return '📎';
  }

  formatSize(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }

  getItemCount(folder: DocFolder): number {
    return this.docService.getItemsByParent(folder.id).length;
  }
}
