import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-performance-issues',
  imports: [CommonModule],
  template: `
    <div class="bg-white dark:bg-gray-900 rounded-lg shadow-md p-6 border border-gray-200 dark:border-gray-700">
      <h2 class="text-lg font-semibold text-gray-900 dark:text-white mb-4">Performance Issues</h2>
      
      <div class="overflow-x-auto">
        <table class="min-w-full">
          <thead class="bg-gray-100 dark:bg-gray-800">
            <tr>
              <th class="px-4 py-2 text-left text-sm font-medium text-gray-600 dark:text-gray-300">ID</th>
              <th class="px-4 py-2 text-left text-sm font-medium text-gray-600 dark:text-gray-300">Description</th>
              <th class="px-4 py-2 text-left text-sm font-medium text-gray-600 dark:text-gray-300">Severity</th>
              <th class="px-4 py-2 text-left text-sm font-medium text-gray-600 dark:text-gray-300">Status</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200 dark:divide-gray-700">
            <tr>
              <td class="px-4 py-3 text-sm text-gray-900 dark:text-white">PERF-01</td>
              <td class="px-4 py-3 text-sm text-gray-600 dark:text-gray-400">Minor key response delay</td>
              <td class="px-4 py-3">
                <span class="inline-flex px-2 py-1 text-xs font-semibold bg-yellow-100 text-yellow-800 rounded-full">
                  Low
                </span>
              </td>
              <td class="px-4 py-3 text-sm text-gray-600 dark:text-gray-400">Open</td>
            </tr>
          </tbody>
        </table>
      </div>
      
      <div class="mt-4 p-3 bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-md">
        <p class="text-sm text-green-700 dark:text-green-400 text-center">
          No critical or high severity performance issues observed.
        </p>
      </div>
    </div>
  `
})
export class PerformanceIssuesComponent {}