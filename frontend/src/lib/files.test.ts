import { describe, expect, it } from 'vitest';
import { acceptPhotos, checkCoordinates, checkPhoto, MAX_PHOTOS } from './files';

const file = (name: string, type: string, size = 1000) => ({ name, type, size });

describe('checkPhoto', () => {
  it('accepts supported image types', () => {
    expect(checkPhoto(file('a.jpg', 'image/jpeg')).ok).toBe(true);
    expect(checkPhoto(file('a.png', 'image/png')).ok).toBe(true);
    expect(checkPhoto(file('a.webp', 'image/webp')).ok).toBe(true);
    expect(checkPhoto(file('a.JPEG', '')).ok).toBe(true);
  });

  it('rejects other types, empty and oversized files', () => {
    expect(checkPhoto(file('a.gif', 'image/gif')).ok).toBe(false);
    expect(checkPhoto(file('a.pdf', 'application/pdf')).ok).toBe(false);
    expect(checkPhoto(file('a.png', 'image/png', 0)).ok).toBe(false);
    expect(checkPhoto(file('a.png', 'image/png', 9 * 1024 * 1024)).reason).toContain('8 MB');
  });
});

describe('acceptPhotos', () => {
  it('enforces the photo limit and reports rejected files', () => {
    const many = Array.from({ length: MAX_PHOTOS + 2 }, (_, i) => file(`${i}.jpg`, 'image/jpeg'));
    const result = acceptPhotos(1, [file('bad.txt', 'text/plain'), ...many]);
    expect(result.accepted).toHaveLength(MAX_PHOTOS - 1);
    expect(result.errors).toHaveLength(2);
  });
});

describe('checkCoordinates', () => {
  it('requires a location inside India', () => {
    expect(checkCoordinates(17.44, 78.39)).toBeNull();
    expect(checkCoordinates(null, null)).toMatch(/Pick/);
    expect(checkCoordinates(0, 0)).toMatch(/India/);
    expect(checkCoordinates(51.5, -0.12)).toMatch(/India/);
  });
});
