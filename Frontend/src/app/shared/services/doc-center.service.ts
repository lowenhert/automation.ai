import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';

export interface DocFile {
  id: number;
  name: string;
  type: 'file';
  mimeType: string;
  size: number;
  data?: string; // base64
  parentId: number | null;
  uploadedBy: string;
  uploadedAt: string;
  updatedAt: string;
}

export interface DocFolder {
  id: number;
  name: string;
  type: 'folder';
  parentId: number | null;
  createdBy: string;
  createdAt: string;
}

export type DocItem = DocFile | DocFolder;

@Injectable({
  providedIn: 'root'
})
export class DocCenterService {
  private storageKey = 'doc_center_items';

  getItems(): DocItem[] {
    const stored = localStorage.getItem(this.storageKey);
    return stored ? JSON.parse(stored) : [];
  }

  saveItems(items: DocItem[]): void {
    localStorage.setItem(this.storageKey, JSON.stringify(items));
  }

  getItemsByParent(parentId: number | null): DocItem[] {
    return this.getItems().filter(i => i.parentId === parentId);
  }

  createFolder(name: string, parentId: number | null, createdBy: string): DocFolder {
    const items = this.getItems();
    const folder: DocFolder = {
      id: Date.now(),
      name,
      type: 'folder',
      parentId,
      createdBy,
      createdAt: new Date().toISOString()
    };
    items.push(folder);
    this.saveItems(items);
    return folder;
  }

  uploadFile(file: DocFile): DocFile {
    const items = this.getItems();
    file.id = Date.now() + Math.floor(Math.random() * 1000);
    file.uploadedAt = new Date().toISOString();
    file.updatedAt = new Date().toISOString();
    items.push(file);
    this.saveItems(items);
    return file;
  }

  deleteItem(id: number): void {
    let items = this.getItems();
    // Recursively delete children if folder
    const toDelete = new Set<number>();
    const collectChildren = (parentId: number) => {
      toDelete.add(parentId);
      items.filter(i => i.parentId === parentId).forEach(child => {
        collectChildren(child.id);
      });
    };
    collectChildren(id);
    items = items.filter(i => !toDelete.has(i.id));
    this.saveItems(items);
  }

  renameItem(id: number, newName: string): void {
    const items = this.getItems();
    const item = items.find(i => i.id === id);
    if (item) {
      item.name = newName;
      if (item.type === 'file') {
        (item as DocFile).updatedAt = new Date().toISOString();
      }
      this.saveItems(items);
    }
  }

  moveItem(id: number, newParentId: number | null): void {
    const items = this.getItems();
    const item = items.find(i => i.id === id);
    if (item) {
      item.parentId = newParentId;
      this.saveItems(items);
    }
  }

  searchItems(query: string): DocItem[] {
    const q = query.toLowerCase();
    return this.getItems().filter(i => i.name.toLowerCase().includes(q));
  }

  getBreadcrumb(itemId: number | null): { id: number | null; name: string }[] {
    const items = this.getItems();
    const crumbs: { id: number | null; name: string }[] = [];
    let currentId = itemId;
    while (currentId !== null) {
      const item = items.find(i => i.id === currentId);
      if (item) {
        crumbs.unshift({ id: item.id, name: item.name });
        currentId = item.parentId;
      } else {
        break;
      }
    }
    crumbs.unshift({ id: null, name: 'Root' });
    return crumbs;
  }

  getFolderSize(folderId: number): number {
    const items = this.getItems();
    let size = 0;
    const calc = (parentId: number) => {
      items.filter(i => i.parentId === parentId).forEach(child => {
        if (child.type === 'file') {
          size += (child as DocFile).size;
        } else {
          calc(child.id);
        }
      });
    };
    calc(folderId);
    return size;
  }
}
