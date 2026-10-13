/**
 * Location search using the public OpenStreetMap Nominatim service. Requests are only made when
 * the user explicitly searches (no autocomplete), in line with Nominatim's usage policy.
 */
export interface GeocodeResult {
  label: string;
  lat: number;
  lng: number;
}

export async function searchPlaces(query: string, signal?: AbortSignal): Promise<GeocodeResult[]> {
  const params = new URLSearchParams({
    q: query,
    format: 'jsonv2',
    limit: '5',
    countrycodes: 'in',
    addressdetails: '0',
  });
  const response = await fetch(`https://nominatim.openstreetmap.org/search?${params}`, {
    signal,
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) throw new Error('Location search is unavailable right now');
  const data = (await response.json()) as { display_name: string; lat: string; lon: string }[];
  return data.map((d) => ({ label: d.display_name, lat: Number(d.lat), lng: Number(d.lon) }));
}

export async function reverseGeocode(lat: number, lng: number, signal?: AbortSignal): Promise<{ locality?: string; address?: string }> {
  const params = new URLSearchParams({ lat: String(lat), lon: String(lng), format: 'jsonv2', zoom: '17' });
  const response = await fetch(`https://nominatim.openstreetmap.org/reverse?${params}`, {
    signal,
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) return {};
  const data = (await response.json()) as {
    display_name?: string;
    address?: Record<string, string>;
  };
  const a = data.address ?? {};
  return {
    locality: a.suburb || a.neighbourhood || a.city_district || a.quarter || a.town || a.city,
    address: data.display_name,
  };
}

/** Default map centre: Hyderabad. */
export const DEFAULT_CENTER: [number, number] = [17.4239, 78.4738];
