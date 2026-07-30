import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';

export type BoxType = string;

export interface InventoryBox {
  id?: number;
  uniqueId: string;
  boxType: BoxType;
  assignedTo: string;
  location: string;
  status: 'AVAILABLE' | 'ASSIGNED' | 'RETURNED';
  notes: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface BoxAssignment {
  id?: number;
  boxId: number;
  boxUniqueId: string;
  boxType: BoxType;
  assignedTo: string;
  assignedBy: string;
  assignedDate: string;
  returnDate?: string;
  location: string;
  status: 'ACTIVE' | 'RETURNED';
  screenshot?: string;
  notes: string;
}

@Injectable({
  providedIn: 'root'
})
export class InventoryService {
  private storageKey = 'stb_inventory_boxes';
  private assignmentKey = 'stb_inventory_assignments';

  getBoxes(): Observable<InventoryBox[]> {
    const stored = localStorage.getItem(this.storageKey);
    return of(stored ? JSON.parse(stored) : []);
  }

  saveBoxes(boxes: InventoryBox[]): void {
    localStorage.setItem(this.storageKey, JSON.stringify(boxes));
  }

  getAssignments(): Observable<BoxAssignment[]> {
    const stored = localStorage.getItem(this.assignmentKey);
    return of(stored ? JSON.parse(stored) : []);
  }

  saveAssignments(assignments: BoxAssignment[]): void {
    localStorage.setItem(this.assignmentKey, JSON.stringify(assignments));
  }

  addBox(box: InventoryBox): Observable<InventoryBox> {
    return this.getBoxes().pipe(
      map(boxes => {
        box.id = Date.now();
        box.createdAt = new Date().toISOString();
        box.updatedAt = new Date().toISOString();
        boxes.push(box);
        this.saveBoxes(boxes);
        return box;
      })
    );
  }

  updateBox(box: InventoryBox): Observable<InventoryBox> {
    return this.getBoxes().pipe(
      map(boxes => {
        const idx = boxes.findIndex(b => b.id === box.id);
        if (idx >= 0) {
          box.updatedAt = new Date().toISOString();
          boxes[idx] = box;
          this.saveBoxes(boxes);
        }
        return box;
      })
    );
  }

  deleteBox(id: number): Observable<void> {
    return this.getBoxes().pipe(
      map(boxes => {
        this.saveBoxes(boxes.filter(b => b.id !== id));
      })
    );
  }

  assignBox(assignment: BoxAssignment): Observable<BoxAssignment> {
    return this.getAssignments().pipe(
      map(assignments => {
        assignment.id = Date.now();
        assignments.push(assignment);
        this.saveAssignments(assignments);
        return assignment;
      })
    );
  }

  returnBox(assignmentId: number): Observable<void> {
    return this.getAssignments().pipe(
      map(assignments => {
        const idx = assignments.findIndex(a => a.id === assignmentId);
        if (idx >= 0) {
          assignments[idx].status = 'RETURNED';
          assignments[idx].returnDate = new Date().toISOString();
          this.saveAssignments(assignments);
        }
      })
    );
  }

  uploadScreenshot(assignmentId: number, screenshot: string): Observable<void> {
    return this.getAssignments().pipe(
      map(assignments => {
        const idx = assignments.findIndex(a => a.id === assignmentId);
        if (idx >= 0) {
          assignments[idx].screenshot = screenshot;
          this.saveAssignments(assignments);
        }
      })
    );
  }
}
