import { Injectable } from '@angular/core';

const STORAGE_KEY = 'dm.camera.preferredDeviceId';

/**
 * Préférences caméra locales (navigateur) — accessibles à tous les utilisateurs.
 * Utilisées par le formulaire pièce pour ouvrir la bonne source.
 */
@Injectable({ providedIn: 'root' })
export class CameraPreferencesService {
  getPreferredDeviceId(): string | null {
    try {
      const value = localStorage.getItem(STORAGE_KEY)?.trim();
      return value || null;
    } catch {
      return null;
    }
  }

  setPreferredDeviceId(deviceId: string | null): void {
    try {
      if (!deviceId?.trim()) {
        localStorage.removeItem(STORAGE_KEY);
        return;
      }
      localStorage.setItem(STORAGE_KEY, deviceId.trim());
    } catch {
      /* quota / mode privé */
    }
  }

  cameraLabel(device: MediaDeviceInfo, index: number): string {
    if (device.label?.trim()) {
      return device.label;
    }
    return `Caméra ${index + 1}`;
  }
}
