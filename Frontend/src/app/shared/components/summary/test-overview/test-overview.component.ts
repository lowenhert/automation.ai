import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-test-overview',
  imports: [CommonModule],
  template: `
    <div class="bg-white dark:bg-gray-900 rounded-lg shadow-sm p-6 border border-gray-200 dark:border-gray-700 h-full">
      <h2 class="text-lg font-semibold text-gray-900 dark:text-white mb-6">Test Overview</h2>
      
      <div class="space-y-4">
        <div class="flex justify-between items-start border-b border-gray-100 dark:border-gray-700 pb-3">
          <span class="text-sm text-gray-600 dark:text-gray-400 font-medium">Product</span>
          <span class="text-sm font-semibold text-gray-900 dark:text-white text-right">Tata Play - STB</span>
        </div>
        
        <div class="flex justify-between items-start border-b border-gray-100 dark:border-gray-700 pb-3">
          <span class="text-sm text-gray-600 dark:text-gray-400 font-medium">Test Type</span>
          <span class="text-sm font-semibold text-gray-900 dark:text-white text-right">Witbe Automation</span>
        </div>
        
        <div class="flex justify-between items-center border-b border-gray-100 dark:border-gray-700 pb-3">
          <span class="text-sm text-gray-600 dark:text-gray-400 font-medium">Environment</span>
          <span class="text-sm font-semibold text-gray-900 dark:text-white">Tataplay-TestLab</span>
        </div>
        
        <div class="flex justify-between items-start border-b border-gray-100 dark:border-gray-700 pb-3">
          <span class="text-sm text-gray-600 dark:text-gray-400 font-medium">Tool Used</span>
          <span class="text-sm font-semibold text-gray-900 dark:text-white text-right">Witbe Smartgate</span>
        </div>
        
        <div class="flex justify-between items-start border-b border-gray-100 dark:border-gray-700 pb-3">
          <span class="text-sm text-gray-600 dark:text-gray-400 font-medium">Executed By</span>
          <span class="text-sm font-semibold text-gray-900 dark:text-white text-right">Mayank</span>
        </div>
        
        <div class="flex justify-between items-center pt-1">
          <span class="text-sm text-gray-600 dark:text-gray-400 font-medium">Test Completion Date</span>
          <span class="text-sm font-semibold text-gray-900 dark:text-white">20/01/2026</span>
        </div>
      </div>
    </div>
  `
})
export class TestOverviewComponent {}