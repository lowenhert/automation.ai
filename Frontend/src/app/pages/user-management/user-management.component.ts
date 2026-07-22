import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PageBreadcrumbComponent } from '../../shared/components/common/page-breadcrumb/page-breadcrumb.component';
import { UserManagementService, UsersListResponse, CreateUserRequest } from '../../shared/services/user-management.service';
import { AuthService, UserProfile } from '../../shared/services/auth.service';
import { NotificationService } from '../../shared/services/notification.service';
import { ButtonComponent } from '../../shared/components/ui/button/button.component';
import { ModalComponent } from '../../shared/components/ui/modal/modal.component';
import { InputFieldComponent } from '../../shared/components/form/input/input-field.component';
import { LabelComponent } from '../../shared/components/form/label/label.component';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-user-management',
  imports: [
    CommonModule,
    PageBreadcrumbComponent,
    ButtonComponent,
    ModalComponent,
    InputFieldComponent,
    LabelComponent,
    FormsModule
  ],
  templateUrl: './user-management.component.html',
  styleUrls: ['./user-management.component.css']
})
export class UserManagementComponent implements OnInit {
  private userManagementService = inject(UserManagementService);
  private authService = inject(AuthService);
  private notificationService = inject(NotificationService);

  users: UserProfile[] = [];
  totalUsers = 0;
  isLoading = true;
  error: string | null = null;
  
  // Modal states
  isCreateModalOpen = false;
  isEditModalOpen = false;
  selectedUser: UserProfile | null = null;

  // Edit form data
  editUser: { id?: number; username: string; email: string; firstName: string; lastName: string; phoneNumber: string; role: string; provider: string; password: string } = { username: '', email: '', firstName: '', lastName: '', phoneNumber: '', role: 'USER', provider: 'local', password: '' };

  // Form data
  newUser: CreateUserRequest = {
    username: '',
    email: '',
    password: '',
    role: 'USER',
    firstName: '',
    lastName: '',
    phoneNumber: '',
    provider: 'local'
  };

  ngOnInit() {
    this.loadUsers();
  }

  loadUsers() {
    this.isLoading = true;
    this.error = null;
    
    this.userManagementService.getAllUsers().subscribe({
      next: (response: UsersListResponse) => {
        this.users = response.rows;
        this.totalUsers = response.count;
        this.isLoading = false;
      },
      error: (error) => {
        this.error = 'Failed to load users';
        this.isLoading = false;
        console.error('Error loading users:', error);
      }
    });
  }

  // Modal methods
  openCreateModal() {
    console.log('=== OPENING CREATE USER MODAL ===');
    this.isCreateModalOpen = true;
    this.resetNewUserForm();
    console.log('Modal opened, form reset to:', this.newUser);
  }

  closeCreateModal() {
    this.isCreateModalOpen = false;
    this.resetNewUserForm();
  }

  openEditModal(user: UserProfile) {
    this.selectedUser = user;
    this.editUser = {
      id: user.id,
      username: user.username,
      email: user.email,
      firstName: user.firstName,
      lastName: user.lastName,
      phoneNumber: user.phoneNumber,
      role: user.role,
      provider: user.provider,
      password: ''
    };
    this.isEditModalOpen = true;
  }

  closeEditModal() {
    this.isEditModalOpen = false;
    this.selectedUser = null;
    this.editUser = { username: '', email: '', firstName: '', lastName: '', phoneNumber: '', role: 'USER', provider: 'local', password: '' };
  }

  saveEditUser() {
    if (!this.selectedUser) return;
    const updateData: Partial<CreateUserRequest> = {
      username: this.editUser.username,
      email: this.editUser.email,
      firstName: this.editUser.firstName,
      lastName: this.editUser.lastName,
      phoneNumber: this.editUser.phoneNumber,
      role: this.editUser.role,
      provider: this.editUser.provider
    };
    if (this.editUser.password && this.editUser.password.trim()) {
      updateData.password = this.editUser.password;
    }
    this.userManagementService.updateUser(this.selectedUser.id, updateData).subscribe({
      next: () => {
        this.notificationService.showSuccess('Success', `User "${this.selectedUser!.username}" updated successfully`);
        this.loadUsers();
        this.closeEditModal();
      },
      error: (error) => {
        let errorMessage = 'Failed to update user';
        if (error.error?.message) errorMessage = error.error.message;
        else if (error.error?.error) errorMessage = error.error.error;
        this.notificationService.showError('Update Failed', errorMessage);
      }
    });
  }

  // Form methods
  resetNewUserForm() {
    console.log('Resetting user form...');
    this.newUser = {
      username: '',
      email: '',
      password: '',
      role: 'USER',
      firstName: '',
      lastName: '',
      phoneNumber: '',
      provider: 'local'
    };
    console.log('Form reset to:', this.newUser);
  }

  createUser() {
    
    if (!this.isFormValid()) {
      console.warn('Form validation failed');
      this.notificationService.showError('Validation Error', 'Please fill in all required fields');
      return;
    }
    
    this.userManagementService.createUser(this.newUser).subscribe({
      next: (createdUser) => {
        console.log('✅ User created successfully:', createdUser);
        this.notificationService.showSuccess('Success', `User "${this.newUser.username}" created successfully`);
        this.loadUsers(); // Reload users list
        this.closeCreateModal();
      },
      error: (error) => {
        // Extract error message from API response
        let errorMessage = 'Failed to create user';
        if (error.error?.message) {
          errorMessage = error.error.message;
        } else if (error.error?.error) {
          errorMessage = error.error.error;
        } else if (error.message) {
          errorMessage = error.message;
        }

        console.log('Extracted error message:', errorMessage);

        // Show specific error based on status code
        switch (error.status) {
          case 400:
            this.notificationService.showError('Validation Error', errorMessage || 'Invalid user data provided');
            break;
          case 409:
            this.notificationService.showError('Conflict Error', errorMessage || 'Username or email already exists');
            break;
          case 403:
            this.notificationService.showError('Permission Error', 'You do not have permission to create users');
            break;
          case 500:
            this.notificationService.showError('Server Error', 'Internal server error occurred');
            break;
          default:
            this.notificationService.showError('Create User Failed', errorMessage);
            break;
        }
      }
    });
  }

  deleteUser(userId: number, userName: string) {
    if (confirm(`Are you sure you want to delete user "${userName}"? This action cannot be undone.`)) {
      this.userManagementService.deleteUser(userId).subscribe({
        next: () => {
          this.notificationService.showSuccess('Success', `User "${userName}" deleted successfully`);
          this.loadUsers(); // Reload users list
        },
        error: (error) => {      
          let errorMessage = 'Failed to delete user';
          if (error.error?.message) {
            errorMessage = error.error.message;
          } else if (error.error?.error) {
            errorMessage = error.error.error;
          }
          
          // Show specific error based on status code
          switch (error.status) {
            case 403:
              this.notificationService.showError('Permission Error', 'You do not have permission to delete users');
              break;
            case 404:
              this.notificationService.showError('User Not Found', 'The user you are trying to delete does not exist');
              break;
            default:
              this.notificationService.showError('Delete Failed', errorMessage);
              break;
          }
        }
      });
    }
  }

  toggleUserStatus(user: UserProfile) {
    const newActiveStatus = !user.active;
    const updatedData: Partial<CreateUserRequest> = { 
      username: user.username,
      email: user.email,
      firstName: user.firstName,
      lastName: user.lastName,
      phoneNumber: user.phoneNumber,
      role: user.role,
      provider: user.provider,
      active: newActiveStatus
    };
    
    this.userManagementService.updateUser(user.id, updatedData).subscribe({
      next: () => {
        this.notificationService.showSuccess('Success', `User ${user.username} ${newActiveStatus ? 'activated' : 'deactivated'} successfully`);
        this.loadUsers(); // Reload users list
      },
      error: (error) => {
        console.error('Error updating user status:', error);
        
        let errorMessage = 'Failed to update user status';
        if (error.error?.message) {
          errorMessage = error.error.message;
        } else if (error.error?.error) {
          errorMessage = error.error.error;
        }
        
        this.notificationService.showError('Update Failed', errorMessage);
      }
    });
  }

  isFormValid(): boolean {
    const isValid = !!(
      this.newUser.username &&
      this.newUser.email &&
      this.newUser.password &&
      this.newUser.firstName &&
      this.newUser.lastName &&
      this.newUser.phoneNumber
    );
    
    console.log('Form validation check:', {
      username: !!this.newUser.username,
      email: !!this.newUser.email,
      password: !!this.newUser.password,
      firstName: !!this.newUser.firstName,
      lastName: !!this.newUser.lastName,
      phoneNumber: !!this.newUser.phoneNumber,
      overall: isValid
    });
    
    return isValid;
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleDateString();
  }

  getRoleColor(role: string): string {
    switch (role.toUpperCase()) {
      case 'ADMIN':
        return 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200';
      case 'USER':
        return 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200';
      case 'VIEWER':
        return 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200';
      default:
        return 'bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-200';
    }
  }

  getStatusColor(active: boolean): string {
    return active 
      ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
      : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200';
  }
}