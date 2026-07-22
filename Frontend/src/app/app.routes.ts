import { Routes } from '@angular/router';
import { EcommerceComponent } from './pages/dashboard/ecommerce/ecommerce.component';
import { SummaryComponent } from './pages/dashboard/summary/summary.component';
import { StbboxesComponent } from './pages/dashboard/stbboxes/stbboxes.component';
import { ProfileComponent } from './pages/profile/profile.component';
import { FormElementsComponent } from './pages/forms/form-elements/form-elements.component';
import { BasicTablesComponent } from './pages/tables/basic-tables/basic-tables.component';
import { BlankComponent } from './pages/blank/blank.component';
import { NotFoundComponent } from './pages/other-page/not-found/not-found.component';
import { AppLayoutComponent } from './shared/layout/app-layout/app-layout.component';
import { InvoicesComponent } from './pages/invoices/invoices.component';
import { LineChartComponent } from './pages/charts/line-chart/line-chart.component';
import { BarChartComponent } from './pages/charts/bar-chart/bar-chart.component';
import { AlertsComponent } from './pages/ui-elements/alerts/alerts.component';
import { AvatarElementComponent } from './pages/ui-elements/avatar-element/avatar-element.component';
import { BadgesComponent } from './pages/ui-elements/badges/badges.component';
import { ButtonsComponent } from './pages/ui-elements/buttons/buttons.component';
import { ImagesComponent } from './pages/ui-elements/images/images.component';
import { VideosComponent } from './pages/ui-elements/videos/videos.component';
import { SignInComponent } from './pages/auth-pages/sign-in/sign-in.component';
import { SignUpComponent } from './pages/auth-pages/sign-up/sign-up.component';
import { CalenderComponent } from './pages/calender/calender.component';
import { UserManagementComponent } from './pages/user-management/user-management.component';
import { OttReportComponent } from './pages/dashboard/ott-report/ott-report.component';
import { StbLiveViewComponent } from './pages/dashboard/stb-live-view/stb-live-view.component';
import { StbSlotDetailComponent } from './pages/dashboard/stb-slot-detail/stb-slot-detail.component';
import { StbExecutionsComponent } from './pages/dashboard/stb-executions/stb-executions.component';
import { TpTestDashboardComponent } from './pages/dashboard/tp-test/tp-test-dashboard/tp-test-dashboard.component';
import { TpTestProjectsComponent } from './pages/dashboard/tp-test/tp-test-projects/tp-test-projects.component';
import { TpTestSuitesComponent } from './pages/dashboard/tp-test/tp-test-suites/tp-test-suites.component';
import { TpTestRunsComponent } from './pages/dashboard/tp-test/tp-test-runs/tp-test-runs.component';
import { AboutComponent } from './pages/about/about.component';
import { authGuard, guestGuard } from './shared/guards/auth.guard';
import { AdminGuard } from './shared/guards/admin.guard';

export const routes: Routes = [
  {
    path:'',
    component:AppLayoutComponent,
    canActivate: [authGuard], // Protect all dashboard routes
    children:[
      {
        path: '',
        redirectTo: 'stbboxes',
        pathMatch: 'full',
      },
      {
        path: 'summary',
        component: SummaryComponent,
        title: 'STB Performance Test Summary | Tata Play Dashboard',
      },
      {
        path: 'stbboxes',
        component: StbboxesComponent,
        title: 'STB Boxes | Tata Play Dashboard',
      },
      {
        path:'calendar',
        component:CalenderComponent,
        title:'Calendar | Tata Play Dashboard'
      },
      {
        path:'profile',
        component:ProfileComponent,
        title:'Profile | Tata Play Dashboard'
      },
      {
        path:'user-management',
        component:UserManagementComponent,
        canActivate: [AdminGuard],
        title:'User Management | Tata Play Dashboard'
      },
      {
        path:'ott-report',
        component:OttReportComponent,
        title:'OTT Report | Tata Play Dashboard'
      },
      {
        path:'stb-live-view',
        component:StbLiveViewComponent,
        title:'STB Box Live View | Tata Play Dashboard'
      },
      {
        path:'stb-live-view/:slot',
        component:StbSlotDetailComponent,
        title:'STB Slot Remote | Tata Play Dashboard'
      },
      {
        path:'stb-executions',
        component:StbExecutionsComponent,
        title:'STB Executions | Tata Play Dashboard'
      },
      {
        path:'tp-test',
        component:TpTestDashboardComponent,
        title:'Test Suite Dashboard | Tata Play Dashboard'
      },
      {
        path:'tp-test/projects',
        component:TpTestProjectsComponent,
        title:'Test Suite Projects | Tata Play Dashboard'
      },
      {
        path:'tp-test/projects/:projectId/suites',
        component:TpTestSuitesComponent,
        title:'Test Suite Suites | Tata Play Dashboard'
      },
      {
        path:'tp-test/runs',
        component:TpTestRunsComponent,
        title:'Test Suite Runs | Tata Play Dashboard'
      },
      {
        path:'about',
        component:AboutComponent,
        title:'About | Test Lab Framework'
      },
      {
        path:'form-elements',
        component:FormElementsComponent,
        title:'Form Elements | Tata Play Dashboard'
      },
      {
        path:'basic-tables',
        component:BasicTablesComponent,
        title:'Basic Tables | Tata Play Dashboard'
      },
      {
        path:'blank',
        component:BlankComponent,
        title:'Blank | Tata Play Dashboard'
      },
      // support tickets
      {
        path:'invoice',
        component:InvoicesComponent,
        title:'Invoices | Tata Play Dashboard'
      },
      {
        path:'line-chart',
        component:LineChartComponent,
        title:'Line Chart | Tata Play Dashboard'
      },
      {
        path:'bar-chart',
        component:BarChartComponent,
        title:'Bar Chart | Tata Play Dashboard'
      },
      {
        path:'alerts',
        component:AlertsComponent,
        title:'Alerts | Tata Play Dashboard'
      },
      {
        path:'avatars',
        component:AvatarElementComponent,
        title:'Avatars | Tata Play Dashboard'
      },
      {
        path:'badge',
        component:BadgesComponent,
        title:'Badges | Tata Play Dashboard'
      },
      {
        path:'buttons',
        component:ButtonsComponent,
        title:'Buttons | Tata Play Dashboard'
      },
      {
        path:'images',
        component:ImagesComponent,
        title:'Images | Tata Play Dashboard'
      },
      {
        path:'videos',
        component:VideosComponent,
        title:'Videos | Tata Play Dashboard'
      },
    ]
  },
  // auth pages
  {
    path:'signin',
    component:SignInComponent,
    canActivate: [guestGuard],
    title:'Sign In | Tata Play Dashboard'
  },
  {
    path:'signup',
    component:SignUpComponent,
    canActivate: [guestGuard],
    title:'Sign Up | Tata Play Dashboard'
  },
  {
    path:'**',
    component:NotFoundComponent,
    title:'Page Not Found | Tata Play Dashboard'
  },
];
