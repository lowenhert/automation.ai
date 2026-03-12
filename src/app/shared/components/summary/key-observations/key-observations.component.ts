import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-key-observations',
  imports: [CommonModule],
  template: `
    <div class="bg-white dark:bg-gray-900 rounded-lg shadow-md p-6 border border-gray-200 dark:border-gray-700 h-full flex flex-col">
      <h2 class="text-lg font-semibold text-gray-900 dark:text-white mb-4">Key Observations</h2>
      
      <div class="space-y-3 flex-1 overflow-y-auto">
        <div class="flex items-start gap-3">
          <div class="w-2 h-2 bg-green-500 rounded-full mt-2 flex-shrink-0"></div>
          <span class="text-gray-700 dark:text-gray-300">
            Overall performance is stable compared to reference build
          </span>
        </div>
        
        <div class="flex items-start gap-3">
          <div class="w-2 h-2 bg-green-500 rounded-full mt-2 flex-shrink-0"></div>
          <span class="text-gray-700 dark:text-gray-300">
            Improvement observed in boot-up and <strong>zap time</strong>
          </span>
        </div>
        
        <div class="flex items-start gap-3">
          <div class="w-2 h-2 bg-green-500 rounded-full mt-2 flex-shrink-0"></div>
          <span class="text-gray-700 dark:text-gray-300">
            Minor delay noticed in <strong>remote key response</strong>
          </span>
        </div>
      </div>
    </div>
  `
})
export class KeyObservationsComponent {}