import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgApexchartsModule, ApexNonAxisChartSeries, ApexChart, ApexLegend, ApexPlotOptions, ApexStates } from 'ng-apexcharts';

@Component({
  selector: 'app-bootup-time-kpi',
  imports: [CommonModule, NgApexchartsModule],
  template: `
    <div class="bg-white dark:bg-gray-900 rounded-lg shadow-md p-4 border border-gray-200 dark:border-gray-700 h-full flex flex-col">
      <h2 class="text-lg font-semibold text-gray-900 dark:text-white mb-3">Boot-up Time KPI</h2>
      
      <!-- Donut Chart -->
      <div class="flex-1 flex justify-center items-center">
        <div class="relative w-56 h-56">
          <apx-chart
            [series]="donutSeries"
            [chart]="donutChart"
            [labels]="donutLabels"
            [colors]="donutColors"
            [legend]="donutLegend"
            [plotOptions]="donutPlotOptions"
            [states]="donutStates">
          </apx-chart>
        </div>
      </div>
      
      <!-- Stats -->
      <div class="mt-2 grid grid-cols-3 gap-2 text-center">
        <div>
          <div class="text-xs font-medium text-gray-900 dark:text-white">10ms</div>
          <div class="text-xs text-gray-500 dark:text-gray-400">Optimization</div>
        </div>
        <div>
          <div class="text-xs font-medium text-gray-900 dark:text-white">50ms</div>
          <div class="text-xs text-gray-500 dark:text-gray-400">Startup</div>
        </div>
        <div>
          <div class="text-xs font-medium text-gray-900 dark:text-white">-9.5%</div>
          <div class="text-xs text-gray-500 dark:text-gray-400">Improvement</div>
        </div>
      </div>
    </div>
  `
})
export class BootupTimeKpiComponent {
  public donutSeries: ApexNonAxisChartSeries = [52.6, 47.4];
  
  public donutChart: ApexChart = {
    fontFamily: 'Inter, sans-serif',
    type: 'donut',
    height: 220,
    width: 220,
    redrawOnParentResize: true,
    redrawOnWindowResize: true,
    selection: {
      enabled: false
    },
    events: {
      mounted: (chart) => {
        setTimeout(() => {
          chart.windowResizeHandler();
        }, 100);
      }
    }
  };
  
  public donutLabels: string[] = ['Phoenix3 Ref', 'Test Build'];
  
  public donutColors: string[] = ['#3B82F6', '#10B981'];
  
  public donutLegend: ApexLegend = {
    show: true,
    position: 'bottom',
    fontSize: '12px',
    markers: {
      strokeWidth: 0,
      shape: 'circle'
    }
  };
  
  public donutPlotOptions: ApexPlotOptions = {
    pie: {
      donut: {
        size: '65%',
        labels: {
          show: true,
          total: {
            show: true,
            showAlways: false,
            label: 'Cold Boot',
            fontSize: '12px',
            fontWeight: 400,
            color: '#6B7280',
            formatter: function () {
              return '38s';
            }
          },
          value: {
            show: true,
            fontSize: '24px',
            fontWeight: 'bold',
            color: '#1F2937',
            offsetY: -10,
            formatter: function (val) {
              return parseFloat(String(val)).toFixed(1) + '%';
            }
          }
        }
      }
    }
  };

  public donutStates = {
    hover: {
      filter: {
        type: 'lighten',
        value: 0.15,
      }
    },
    active: {
      allowMultipleDataPointsSelection: false,
      filter: {
        type: 'lighten',
        value: 0.35,
      }
    }
  };
}