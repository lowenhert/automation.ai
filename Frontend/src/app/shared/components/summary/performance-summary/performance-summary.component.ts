import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-performance-summary',
  imports: [CommonModule],
  template: `
    <div class="bg-white dark:bg-gray-900 rounded-lg shadow-sm p-6 border border-gray-200 dark:border-gray-700">
      <h2 class="text-lg font-semibold text-gray-900 dark:text-white mb-6">Performance Summary</h2>
      
      <div class="overflow-hidden rounded-lg border border-gray-200 dark:border-gray-700">
        <table class="min-w-full divide-y divide-gray-200 dark:divide-gray-700">
          <thead class="bg-gray-50 dark:bg-gray-800">
            <tr>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-600 dark:text-gray-300 uppercase tracking-wider">Test Area</th>
              <th class="px-4 py-3 text-left text-xs font-semibold text-gray-600 dark:text-gray-300 uppercase tracking-wider">KPI</th>
              <th class="px-4 py-3 text-center text-xs font-semibold text-gray-600 dark:text-gray-300 uppercase tracking-wider">Ref Build</th>
              <th class="px-4 py-3 text-center text-xs font-semibold text-gray-600 dark:text-gray-300 uppercase tracking-wider">Test Build</th>
              <th class="px-4 py-3 text-center text-xs font-semibold text-gray-600 dark:text-gray-300 uppercase tracking-wider">Status</th>
            </tr>
          </thead>
          <tbody class="bg-white dark:bg-gray-900 divide-y divide-gray-200 dark:divide-gray-700">
            <tr class="hover:bg-gray-50 dark:hover:bg-gray-800">
              <td class="px-4 py-4 text-sm font-medium text-gray-900 dark:text-white">Boot-up Time</td>
              <td class="px-4 py-4 text-sm text-gray-600 dark:text-gray-400">Cold Boot (sec)</td>
              <td class="px-4 py-4 text-sm text-gray-900 dark:text-white text-center font-semibold">42</td>
              <td class="px-4 py-4 text-sm text-gray-900 dark:text-white text-center font-semibold">38</td>
              <td class="px-4 py-4 text-center">
                <span class="inline-flex px-3 py-1 text-xs font-semibold bg-green-100 text-green-800 rounded-full">
                  Improved
                </span>
              </td>
            </tr>
            <tr class="hover:bg-gray-50 dark:hover:bg-gray-800">
              <td class="px-4 py-4 text-sm font-medium text-gray-900 dark:text-white">Zap Time</td>
              <td class="px-4 py-4 text-sm text-gray-600 dark:text-gray-400">Avg Zap (ms)</td>
              <td class="px-4 py-4 text-sm text-gray-900 dark:text-white text-center font-semibold">1200</td>
              <td class="px-4 py-4 text-sm text-gray-900 dark:text-white text-center font-semibold">1150</td>
              <td class="px-4 py-4 text-center">
                <span class="inline-flex px-3 py-1 text-xs font-semibold bg-green-100 text-green-800 rounded-full">
                  4.90ms
                </span>
              </td>
            </tr>
            <tr class="hover:bg-gray-50 dark:hover:bg-gray-800">
              <td class="px-4 py-4 text-sm font-medium text-gray-900 dark:text-white">Key Response</td>
              <td class="px-4 py-4 text-sm text-gray-600 dark:text-gray-400">Remote Input (ms)</td>
              <td class="px-4 py-4 text-sm text-gray-900 dark:text-white text-center font-semibold">300</td>
              <td class="px-4 py-4 text-sm text-gray-900 dark:text-white text-center font-semibold">310</td>
              <td class="px-4 py-4 text-center">
                <span class="inline-flex px-3 py-1 text-xs font-semibold bg-yellow-100 text-yellow-800 rounded-full">
                  4.90ms
                </span>
              </td>
            </tr>
            <tr class="hover:bg-gray-50 dark:hover:bg-gray-800">
              <td class="px-4 py-4 text-sm font-medium text-gray-900 dark:text-white">Stand In-Out</td>
              <td class="px-4 py-4 text-sm text-gray-600 dark:text-gray-400">Channel Switch</td>
              <td class="px-4 py-4 text-sm text-gray-900 dark:text-white text-center font-semibold">Stable</td>
              <td class="px-4 py-4 text-sm text-gray-900 dark:text-white text-center font-semibold">Stable</td>
              <td class="px-4 py-4 text-center">
                <span class="inline-flex px-3 py-1 text-xs font-semibold bg-green-100 text-green-800 rounded-full">
                  Improved
                </span>
              </td>
            </tr>
            <tr class="hover:bg-gray-50 dark:hover:bg-gray-800">
              <td class="px-4 py-4 text-sm font-medium text-gray-900 dark:text-white">Linear Search</td>
              <td class="px-4 py-4 text-sm text-gray-600 dark:text-gray-400">Result Load (ms)</td>
              <td class="px-4 py-4 text-sm text-gray-900 dark:text-white text-center font-semibold">900</td>
              <td class="px-4 py-4 text-sm text-gray-900 dark:text-white text-center font-semibold">870</td>
              <td class="px-4 py-4 text-center">
                <span class="inline-flex px-3 py-1 text-xs font-semibold bg-green-100 text-green-800 rounded-full">
                  Improved
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `
})
export class PerformanceSummaryComponent {}