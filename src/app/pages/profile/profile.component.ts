
import { Component, OnInit, inject } from '@angular/core';
import { PageBreadcrumbComponent } from '../../shared/components/common/page-breadcrumb/page-breadcrumb.component';
import { UserMetaCardComponent } from '../../shared/components/user-profile/user-meta-card/user-meta-card.component';
import { UserInfoCardComponent } from '../../shared/components/user-profile/user-info-card/user-info-card.component';
import { AuthService, UserProfile } from '../../shared/services/auth.service';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-profile',
  imports: [
    CommonModule,
    PageBreadcrumbComponent,
    UserMetaCardComponent,
    UserInfoCardComponent
],
  templateUrl: './profile.component.html',
  styles: ``
})
export class ProfileComponent implements OnInit {
  private authService = inject(AuthService);
  
  userProfile: UserProfile | null = null;
  isLoading = true;
  error: string | null = null;

  ngOnInit() {
    this.fetchUserProfile();
  }

  fetchUserProfile() {
    this.isLoading = true;
    this.error = null;
    
    this.authService.fetchUserProfile().subscribe({
      next: (profile) => {
        this.userProfile = profile;
        this.isLoading = false;
      },
      error: (error) => {
        this.error = 'Failed to load user profile';
        this.isLoading = false;
        console.error('Error fetching user profile:', error);
      }
    });
  }
}
