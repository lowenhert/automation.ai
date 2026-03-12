import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-summary-header',
  imports: [CommonModule],
  template: `
    <div class="bg-white dark:bg-gray-900 rounded-lg shadow-sm p-6 border border-gray-200 dark:border-gray-700">
      <!-- Title Section -->
      <div class="flex items-center gap-4 mb-4">
        <div class="bg-pink-500 text-white px-3 py-1 rounded text-sm font-semibold">
          STB
        </div>
        <h1 class="text-xl lg:text-2xl font-bold text-gray-900 dark:text-white">
          STB Performance Test – Summary Dashboard
        </h1>
      </div>
      <p class="text-gray-600 dark:text-gray-400 mb-6 text-sm">
        Automated Performance Insights powered by Witbe
      </p>
      
      <!-- Status Cards Grid -->
      <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
        <!-- Test Build Card -->
        <div class="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-lg p-4 min-h-[100px] flex flex-col justify-between">
          <div class="flex items-center gap-2 mb-2">
            <div class="w-2 h-2 bg-green-500 rounded-full"></div>
            <span class="text-xs text-gray-600 dark:text-gray-400">Test Build</span>
          </div>
          <p class="font-semibold text-gray-900 dark:text-white text-sm">Phoenix3 - 4.5.0.001</p>
        </div>
        
        <!-- Reference Build Card -->
        <div class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4 min-h-[100px] flex flex-col justify-between">
          <div class="flex items-center gap-2 mb-2">
            <div class="w-2 h-2 bg-blue-500 rounded-full"></div>
            <span class="text-xs text-gray-600 dark:text-gray-400">Reference Build</span>
          </div>
          <p class="font-semibold text-gray-900 dark:text-white text-sm">Phoenix3 - 4.0.3.001</p>
        </div>
        
        <!-- Test Coverage Card -->
        <div class="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-lg p-4 min-h-[100px] flex flex-col justify-between">
          <div class="flex items-center gap-2 mb-2">
            <div class="w-2 h-2 bg-green-500 rounded-full"></div>
            <span class="text-xs text-gray-600 dark:text-gray-400">Test Coverage</span>
          </div>
          <p class="font-semibold text-gray-900 dark:text-white text-sm">5 Test Scenarios</p>
        </div>
        
        <!-- Overall Status Card -->
        <div class="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-lg p-4 min-h-[100px] flex flex-col justify-between">
          <div class="flex items-center gap-2 mb-2">
            <div class="w-2 h-2 bg-green-500 rounded-full"></div>
            <span class="text-xs text-gray-600 dark:text-gray-400">Overall Status</span>
          </div>
          <p class="font-semibold text-gray-900 dark:text-white text-sm">Performance Stable</p>
        </div>
        
        <!-- Critical Issues Card -->
        <div class="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-lg p-4 min-h-[100px] flex flex-col justify-center items-center">
          <div class="flex items-center gap-2 mb-2">
            <div class="w-2 h-2 bg-green-500 rounded-full"></div>
            <span class="text-xs text-gray-600 dark:text-gray-400">Critical Issues</span>
          </div>
          <p class="font-bold text-gray-900 dark:text-white text-2xl">0</p>
        </div>
      </div>
    </div>
  `
})
export class SummaryHeaderComponent {}