
import { Component, inject } from '@angular/core';
import { LabelComponent } from '../../form/label/label.component';
import { CheckboxComponent } from '../../form/input/checkbox.component';
import { ButtonComponent } from '../../ui/button/button.component';
import { InputFieldComponent } from '../../form/input/input-field.component';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService, NotificationService } from '../../../services';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-signin-form',
  imports: [
    LabelComponent,
    CheckboxComponent,
    ButtonComponent,
    InputFieldComponent,
    RouterModule,
    FormsModule,
    CommonModule
],
  templateUrl: './signin-form.component.html',
  styles: ``
})
export class SigninFormComponent {
  private authService = inject(AuthService);
  private notificationService = inject(NotificationService);

  showPassword = false;
  isChecked = false;
  isLoading = false;

  username = '';
  password = '';

  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
  }

  onSignIn() {
    if (!this.username || !this.password) {
      this.notificationService.showWarning(
        'Validation Error',
        'Please enter both username and password to continue.'
      );
      return;
    }

    this.isLoading = true;

    this.authService.login(this.username, this.password).subscribe({
      next: (response) => {
        console.log('Login successful:', response);
        // Navigation and success notification are now handled by AuthService
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Login failed:', error);
        // Error notifications are now handled by AuthService
        this.isLoading = false;
      }
    });
  }
}
