import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-final-verdict',
  imports: [CommonModule],
  template: `
    <div class="bg-white dark:bg-gray-900 rounded-lg shadow-md p-6 border border-gray-200 dark:border-gray-700">
      <div class="flex items-center gap-3 mb-3">
        <div class="w-6 h-6 bg-green-500 rounded-full flex items-center justify-center">
          <svg class="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
          </svg>
        </div>
        <h2 class="text-lg font-semibold text-gray-900 dark:text-white">Final Verdict</h2>
      </div>
      
      <div class="p-4 bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-lg">
        <p class="text-sm font-medium text-green-800 dark:text-green-400 mb-2">
          <span class="inline-flex px-2 py-1 text-xs font-semibold bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200 rounded-full mr-2">
            Recommended
          </span>
          for next phase / rollout
        </p>
        <p class="text-xs text-green-700 dark:text-green-300">
          from a performance perspective
        </p>
      </div>
    </div>
  `
})
export class FinalVerdictComponent {}