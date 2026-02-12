import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { InputFieldComponent } from './../../form/input/input-field.component';
import { ModalService } from '../../../services/modal.service';
import { ModalComponent } from '../../ui/modal/modal.component';
import { ButtonComponent } from '../../ui/button/button.component';
import { UserProfile } from '../../../services/auth.service';

@Component({
  selector: 'app-user-meta-card',
  imports: [
    ModalComponent,
    InputFieldComponent,
    ButtonComponent
],
  templateUrl: './user-meta-card.component.html',
  styles: ``
})
export class UserMetaCardComponent implements OnChanges {
  @Input() userProfile: UserProfile | null = null;

  constructor(public modal: ModalService) {}

  isOpen = false;
  openModal() { this.isOpen = true; }
  closeModal() { this.isOpen = false; }

  // User data populated from API
  user = {
    firstName: 'Musharof',
    lastName: 'Chowdhury',
    role: 'Team Manager',
    location: 'Arizona, United States',
    avatar: '/images/user/owner.jpg',
    email: 'randomuser@pimjo.com',
    phone: '+09 363 398 46',
    bio: 'Team Manager',
  };

  ngOnChanges(changes: SimpleChanges) {
    if (changes['userProfile'] && this.userProfile) {
      this.updateUserData();
    }
  }

  private updateUserData() {
    if (this.userProfile) {
      this.user = {
        firstName: this.userProfile.firstName,
        lastName: this.userProfile.lastName,
        role: this.userProfile.role,
        location: 'Arizona, United States', // Not provided by API, keep default
        avatar: '/images/user/owner.jpg', // Not provided by API, keep default
        email: this.userProfile.email,
        phone: this.userProfile.phoneNumber,
        bio: this.userProfile.role, // Using role as bio since bio is not provided by API
      };
    }
  }

  handleSave() {
    // Handle save logic here
    console.log('Saving changes...');
    this.modal.closeModal();
  }
}
