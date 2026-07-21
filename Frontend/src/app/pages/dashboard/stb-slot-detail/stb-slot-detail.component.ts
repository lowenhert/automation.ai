import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { SafeResourcePipe } from '../../../shared/pipe/safe-resource.pipe';
import { AdbService } from '../../../shared/services/adb.service';

@Component({
  selector: 'app-stb-slot-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, SafeResourcePipe],
  templateUrl: './stb-slot-detail.component.html',
  styleUrl: './stb-slot-detail.component.css'
})
export class StbSlotDetailComponent implements OnInit {
  slot: number = 1;
  embedUrl: string = '';
  adbIp: string = '';
  connected: boolean = false;
  connecting: boolean = false;
  statusMessage: string = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private adbService: AdbService
  ) {}

  ngOnInit() {
    this.slot = Number(this.route.snapshot.paramMap.get('slot')) || 1;
    this.embedUrl = `http://172.18.1.87:3000/embed/ch${this.slot}`;

    // Load saved IP for this slot from localStorage
    const savedIp = localStorage.getItem(`stb_slot_${this.slot}_ip`);
    if (savedIp) {
      this.adbIp = savedIp;
    }
  }

  goBack() {
    this.router.navigate(['/stb-live-view']);
  }

  connectAdb() {
    if (!this.adbIp.trim()) {
      this.statusMessage = 'Please enter an IP address';
      return;
    }
    this.connecting = true;
    this.statusMessage = '';

    // Save IP for this slot
    localStorage.setItem(`stb_slot_${this.slot}_ip`, this.adbIp.trim());

    this.adbService.connect(this.adbIp.trim()).subscribe({
      next: (res) => {
        this.connected = res.success;
        this.statusMessage = res.success ? 'Connected successfully' : (res.error || 'Connection failed');
        this.connecting = false;
      },
      error: (err) => {
        this.statusMessage = 'Connection failed: ' + (err.error?.error || err.message);
        this.connecting = false;
      }
    });
  }

  disconnectAdb() {
    this.adbService.disconnect(this.adbIp.trim()).subscribe({
      next: () => {
        this.connected = false;
        this.statusMessage = 'Disconnected';
      },
      error: () => {
        this.connected = false;
        this.statusMessage = 'Disconnected';
      }
    });
  }

  sendKey(keycode: string) {
    if (!this.connected) return;
    this.adbService.sendKeyEvent(this.adbIp.trim(), keycode).subscribe({
      next: (res) => {
        if (!res.success) this.statusMessage = res.error || 'Key send failed';
      },
      error: (err) => {
        this.statusMessage = 'Error: ' + (err.error?.error || err.message);
      }
    });
  }

  // Remote control button mappings
  remoteButtons = {
    power: 'KEYCODE_POWER',
    home: 'KEYCODE_HOME',
    back: 'KEYCODE_BACK',
    menu: 'KEYCODE_MENU',
    up: 'KEYCODE_DPAD_UP',
    down: 'KEYCODE_DPAD_DOWN',
    left: 'KEYCODE_DPAD_LEFT',
    right: 'KEYCODE_DPAD_RIGHT',
    ok: 'KEYCODE_DPAD_CENTER',
    volUp: 'KEYCODE_VOLUME_UP',
    volDown: 'KEYCODE_VOLUME_DOWN',
    mute: 'KEYCODE_VOLUME_MUTE',
    chUp: 'KEYCODE_CHANNEL_UP',
    chDown: 'KEYCODE_CHANNEL_DOWN',
    num0: 'KEYCODE_0',
    num1: 'KEYCODE_1',
    num2: 'KEYCODE_2',
    num3: 'KEYCODE_3',
    num4: 'KEYCODE_4',
    num5: 'KEYCODE_5',
    num6: 'KEYCODE_6',
    num7: 'KEYCODE_7',
    num8: 'KEYCODE_8',
    num9: 'KEYCODE_9',
    play: 'KEYCODE_MEDIA_PLAY',
    pause: 'KEYCODE_MEDIA_PAUSE',
    stop: 'KEYCODE_MEDIA_STOP',
    rewind: 'KEYCODE_MEDIA_REWIND',
    forward: 'KEYCODE_MEDIA_FAST_FORWARD',
    info: 'KEYCODE_INFO',
    guide: 'KEYCODE_GUIDE',
    red: 'KEYCODE_PROG_RED',
    green: 'KEYCODE_PROG_GREEN',
    yellow: 'KEYCODE_PROG_YELLOW',
    blue: 'KEYCODE_PROG_BLUE',
  };

  getNumKey(n: number): string {
    return (this.remoteButtons as Record<string, string>)['num' + n] || '';
  }
}
