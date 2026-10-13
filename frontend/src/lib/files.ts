export const MAX_PHOTOS = 6;
export const MAX_PHOTO_BYTES = 8 * 1024 * 1024;
export const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
export const ACCEPT_ATTRIBUTE = '.jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp';

export interface FileCheck {
  ok: boolean;
  reason?: string;
}

/**
 * Client-side validation for a quick, friendly response. The server re-validates every file
 * (including its actual bytes), so this is a convenience, not a security control.
 */
export function checkPhoto(file: { name: string; type: string; size: number }): FileCheck {
  const ext = file.name.toLowerCase().split('.').pop() ?? '';
  const typeOk = ACCEPTED_TYPES.includes(file.type) || (!file.type && ['jpg', 'jpeg', 'png', 'webp'].includes(ext));
  if (!typeOk) return { ok: false, reason: `${file.name} is not a JPEG, PNG or WebP image` };
  if (file.size === 0) return { ok: false, reason: `${file.name} is empty` };
  if (file.size > MAX_PHOTO_BYTES) return { ok: false, reason: `${file.name} is larger than 8 MB` };
  return { ok: true };
}

export function acceptPhotos<T extends { name: string; type: string; size: number }>(
  current: number,
  incoming: T[],
): { accepted: T[]; errors: string[] } {
  const accepted: T[] = [];
  const errors: string[] = [];
  for (const file of incoming) {
    const check = checkPhoto(file);
    if (!check.ok) {
      errors.push(check.reason!);
    } else if (current + accepted.length >= MAX_PHOTOS) {
      errors.push(`You can attach up to ${MAX_PHOTOS} photos`);
      break;
    } else {
      accepted.push(file);
    }
  }
  return { accepted, errors };
}

/** Validates coordinates the same way the API does (service area: India). */
export function checkCoordinates(lat?: number | null, lng?: number | null): string | null {
  if (lat == null || lng == null || Number.isNaN(lat) || Number.isNaN(lng)) return 'Pick the location on the map';
  if (lat < 6 || lat > 37.5 || lng < 68 || lng > 97.5) return 'Location must be within India';
  return null;
}
