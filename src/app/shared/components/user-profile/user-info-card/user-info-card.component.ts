import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { ModalService } from '../../../services/modal.service';
import { InputFieldComponent } from '../../form/input/input-field.component';
import { ButtonComponent } from '../../ui/button/button.component';
import { LabelComponent } from '../../form/label/label.component';
import { ModalComponent } from '../../ui/modal/modal.component';
import { UserProfile } from '../../../services/auth.service';

@Component({
  selector: 'app-user-info-card',
  imports: [
    InputFieldComponent,
    ButtonComponent,
    LabelComponent,
    ModalComponent
],
  templateUrl: './user-info-card.component.html',
  styles: ``
})
export class UserInfoCardComponent implements OnChanges {
  @Input() userProfile: UserProfile | null = null;

  constructor(public modal: ModalService) {}

  isOpen = false;
  openModal() { this.isOpen = true; }
  closeModal() { this.isOpen = false; }

  user = {
    firstName: 'Musharof',
    lastName: 'Chowdhury',
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
