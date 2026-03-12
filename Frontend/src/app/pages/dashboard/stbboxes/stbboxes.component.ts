import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StbService } from '../../../shared/services/stb.service';

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
}