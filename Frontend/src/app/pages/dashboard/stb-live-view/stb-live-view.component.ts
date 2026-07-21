import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { SafeResourcePipe } from '../../../shared/pipe/safe-resource.pipe';

@Component({
  selector: 'app-stb-live-view',
  standalone: true,
  imports: [CommonModule, SafeResourcePipe],
  templateUrl: './stb-live-view.component.html',
  styleUrl: './stb-live-view.component.css'
})
export class StbLiveViewComponent {
  channels = Array.from({ length: 8 }, (_, i) => ({
    slot: i + 1,
    embedUrl: `http://172.18.1.87:3000/embed/ch${i + 1}`
  }));

  constructor(private router: Router) {}

  openSlotDetail(slot: number) {
    this.router.navigate(['/stb-live-view', slot]);
  }
}
