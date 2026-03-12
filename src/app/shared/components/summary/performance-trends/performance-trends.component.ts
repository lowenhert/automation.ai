import { Component, OnInit, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgApexchartsModule, ApexAxisChartSeries, ApexChart, ApexXAxis, ApexYAxis, ApexDataLabels, ApexStroke, ApexLegend, ApexGrid, ApexPlotOptions, ApexFill } from 'ng-apexcharts';

@Component({
  selector: 'app-performance-trends',
  imports: [CommonModule, NgApexchartsModule],
  template: `
    <div class="bg-white dark:bg-gray-900 rounded-lg shadow-md p-4 border border-gray-200 dark:border-gray-700 h-full flex flex-col">
      <h2 class="text-lg font-semibold text-gray-900 dark:text-white mb-3">Performance Trends</h2>
      
      <!-- KPI Comparison Chart -->
      <div class="flex-1 mb-2">
        <h3 class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-1">KPI Comparison</h3>
        <div class="h-36">
          <apx-chart
            [series]="barChartSeries"
            [chart]="barChart"
            [xaxis]="barXaxis"
            [yaxis]="barYaxis"
            [plotOptions]="barPlotOptions"
            [dataLabels]="barDataLabels"
            [colors]="barColors">
          </apx-chart>
        </div>
      </div>
      
      <!-- Zap Time Trend Chart -->
      <div class="flex-1">
        <h3 class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-1">Zap Time Trend</h3>
        <div class="h-36">
          <apx-chart
            [series]="lineChartSeries"
            [chart]="lineChart"
            [xaxis]="lineXaxis"
            [yaxis]="lineYaxis"
            [stroke]="lineStroke"
            [dataLabels]="lineDataLabels"
            [colors]="lineColors">
          </apx-chart>
        </div>
      </div>
    </div>
  `
})
export class PerformanceTrendsComponent implements OnInit, AfterViewInit {
  // Bar Chart Configuration
  public barChartSeries: ApexAxisChartSeries = [
    {
      name: 'Ref Build',
      data: [42, 1200, 300, 900]
    },
    {
      name: 'Test Build', 
      data: [38, 1150, 310, 870]
    }
  ];

  public barChart: ApexChart = {
    fontFamily: 'Inter, sans-serif',
    type: 'bar',
    height: 120,
    toolbar: { show: false },
    redrawOnParentResize: true,
    redrawOnWindowResize: true,
    selection: {
      enabled: false
    },
    animations: {
      enabled: true,
      speed: 800,
      animateGradually: {
        enabled: true,
        delay: 150
      }
    },
    events: {
      mounted: (chart) => {
        setTimeout(() => {
          chart.windowResizeHandler();
        }, 100);
      }
    }
  };

  public barXaxis: ApexXAxis = {
    categories: ['Cold Boot', 'Avg Zap', 'Key Response', 'Result Load'],
    axisBorder: { show: false },
    axisTicks: { show: false },
    labels: {
      style: {
        fontSize: '10px',
      },
    },
  };

  public barYaxis: ApexYAxis = {
    labels: {
      style: {
        fontSize: '12px',
      },
    },
  };

  public barPlotOptions: ApexPlotOptions = {
    bar: {
      horizontal: false,
      columnWidth: '60%',
      borderRadius: 4,
    },
  };

  public barDataLabels: ApexDataLabels = {
    enabled: false,
  };

  public barColors: string[] = ['#3B82F6', '#10B981'];

  // Line Chart Configuration  
  public lineChartSeries: ApexAxisChartSeries = [
    {
      name: 'Zap Time (ms)',
      data: [1250, 1200, 1180, 1160, 1170, 1150, 1155]
    }
  ];

  public lineChart: ApexChart = {
    fontFamily: 'Inter, sans-serif',
    type: 'line',
    height: 120,
    toolbar: { show: false },
    redrawOnParentResize: true,
    redrawOnWindowResize: true,
    selection: {
      enabled: false
    },
    animations: {
      enabled: true,
      speed: 800,
      animateGradually: {
        enabled: true,
        delay: 150
      }
    },
    events: {
      mounted: (chart) => {
        setTimeout(() => {
          chart.windowResizeHandler();
        }, 100);
      }
    }
  };

  public lineXaxis: ApexXAxis = {
    categories: ['1', '2', '3', '4', '5', '6', '7'],
    title: {
      text: 'Sample Run',
      style: {
        fontSize: '10px',
      }
    },
    axisBorder: { show: false },
    axisTicks: { show: false },
    labels: {
      style: {
        fontSize: '10px',
      },
    },
  };

  public lineYaxis: ApexYAxis = {
    title: {
      text: 'Time (ms)'
    },
    labels: {
      style: {
        fontSize: '12px',
      },
    },
  };

  public lineStroke: ApexStroke = {
    width: 3,
    curve: 'smooth',
  };

  public lineDataLabels: ApexDataLabels = {
    enabled: false,
  };

  public lineColors: string[] = ['#10B981'];

  ngOnInit() {
    // Component initialization
  }

  ngAfterViewInit() {
    // Force chart resize after view initialization to prevent overlapping
    setTimeout(() => {
      window.dispatchEvent(new Event('resize'));
    }, 200);
  }
}