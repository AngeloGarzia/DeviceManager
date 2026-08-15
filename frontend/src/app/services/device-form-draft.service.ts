import { Injectable } from '@angular/core';
import { DevicePhoto } from '../models/models';

export interface DeviceFormDraftValues {
  nom: string;
  reference: string;
  usage: string;
  informationTechnique: string;
  dateAcquisition: string;
  obsolete: boolean;
  stock: number;
  sfmId: number | null;
  masId: number | null;
}

export interface DeviceFormDraftPhoto {
  file: File;
  previewUrl: string;
}

export interface DeviceFormDraft {
  editId: number | null;
  forOrderRequest: boolean;
  form: DeviceFormDraftValues;
  existingPhotos: DevicePhoto[];
  newPhotos: DeviceFormDraftPhoto[];
}

@Injectable({ providedIn: 'root' })
export class DeviceFormDraftService {
  private draft: DeviceFormDraft | null = null;

  save(draft: DeviceFormDraft): void {
    this.draft = draft;
  }

  hasDraft(): boolean {
    return this.draft != null;
  }

  peek(): DeviceFormDraft | null {
    return this.draft;
  }

  take(): DeviceFormDraft | null {
    const current = this.draft;
    this.draft = null;
    return current;
  }

  clear(): void {
    this.draft = null;
  }
}
