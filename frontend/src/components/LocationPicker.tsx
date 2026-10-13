import L from 'leaflet';
import { Crosshair, Loader2, Search } from 'lucide-react';
import { useEffect, useState } from 'react';
import { MapContainer, Marker, TileLayer, useMap, useMapEvents } from 'react-leaflet';
import { DEFAULT_CENTER, searchPlaces, type GeocodeResult } from '../lib/geocode';

export const OSM_TILES = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png';
export const OSM_ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

export const pinIcon = (colour = '#12603e', size = 18) =>
  L.divIcon({
    className: '',
    html: `<div class="marker-pin" style="width:${size}px;height:${size}px;background:${colour}"></div>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
    popupAnchor: [0, -size / 2],
  });

const selectedIcon = L.divIcon({
  className: '',
  html: `<svg width="34" height="44" viewBox="0 0 34 44" xmlns="http://www.w3.org/2000/svg"><path d="M17 0C7.6 0 0 7.4 0 16.6 0 29 17 44 17 44s17-15 17-27.4C34 7.4 26.4 0 17 0z" fill="#12603e"/><circle cx="17" cy="16.5" r="6.5" fill="#fff"/></svg>`,
  iconSize: [34, 44],
  iconAnchor: [17, 44],
});

function ClickToPick({ onPick }: { onPick: (lat: number, lng: number) => void }) {
  useMapEvents({ click: (e) => onPick(e.latlng.lat, e.latlng.lng) });
  return null;
}

function FlyTo({ target }: { target: [number, number] | null }) {
  const map = useMap();
  useEffect(() => {
    if (target) map.flyTo(target, Math.max(map.getZoom(), 16), { duration: 0.6 });
  }, [target, map]);
  return null;
}

export interface PickedLocation {
  lat: number;
  lng: number;
}

/**
 * Map-based location picker. Browser geolocation is requested only when the user presses
 * "Use my current location" — never automatically.
 */
export function LocationPicker({
  value,
  onChange,
  error,
}: {
  value: PickedLocation | null;
  onChange: (loc: PickedLocation) => void;
  error?: string;
}) {
  const [flyTarget, setFlyTarget] = useState<[number, number] | null>(null);
  const [geoState, setGeoState] = useState<'idle' | 'locating' | 'error'>('idle');
  const [geoMessage, setGeoMessage] = useState('');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<GeocodeResult[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState('');

  const pick = (lat: number, lng: number, fly = false) => {
    const rounded = { lat: Number(lat.toFixed(6)), lng: Number(lng.toFixed(6)) };
    onChange(rounded);
    if (fly) setFlyTarget([rounded.lat, rounded.lng]);
  };

  const locate = () => {
    if (!('geolocation' in navigator)) {
      setGeoState('error');
      setGeoMessage('Your browser does not support location access. Tap the map instead.');
      return;
    }
    setGeoState('locating');
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setGeoState('idle');
        setGeoMessage(pos.coords.accuracy > 100 ? `Approximate location (±${Math.round(pos.coords.accuracy)} m). Drag the pin to adjust.` : '');
        pick(pos.coords.latitude, pos.coords.longitude, true);
      },
      (err) => {
        setGeoState('error');
        setGeoMessage(
          err.code === err.PERMISSION_DENIED
            ? 'Location permission was denied. You can still tap the map to place the pin.'
            : 'Could not determine your location. Tap the map to place the pin.',
        );
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 60000 },
    );
  };

  const search = async () => {
    if (query.trim().length < 3) return;
    setSearching(true);
    setSearchError('');
    try {
      const found = await searchPlaces(query.trim());
      setResults(found);
      if (found.length === 0) setSearchError('No matching places found in India.');
    } catch {
      setSearchError('Location search is unavailable right now. Tap the map instead.');
    } finally {
      setSearching(false);
    }
  };

  return (
    <div>
      <div className="mb-2 flex flex-col gap-2 sm:flex-row">
        {/* Not a <form>: this picker sits inside the complaint form, and forms cannot be nested. */}
        <div className="relative flex flex-1 gap-2" role="search">
          <input
            className="input"
            placeholder="Search a place or landmark (e.g. Ameerpet Metro)"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                search();
              }
            }}
            aria-label="Search location"
          />
          <button type="button" onClick={search} className="btn-secondary shrink-0" disabled={searching} aria-label="Search">
            {searching ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}
          </button>
          {results && results.length > 0 && (
            <ul className="absolute left-0 right-0 top-full z-[1000] mt-1 max-h-60 overflow-auto rounded-lg border border-ink-200 bg-white shadow-raised">
              {results.map((r, i) => (
                <li key={i}>
                  <button
                    type="button"
                    className="block w-full px-3 py-2 text-left text-sm hover:bg-brand-50"
                    onClick={() => {
                      pick(r.lat, r.lng, true);
                      setResults(null);
                    }}
                  >
                    {r.label}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        <button type="button" className="btn-secondary shrink-0" onClick={locate} disabled={geoState === 'locating'}>
          {geoState === 'locating' ? <Loader2 className="h-4 w-4 animate-spin" /> : <Crosshair className="h-4 w-4" />}
          Use my current location
        </button>
      </div>
      {searchError && <p className="mb-2 text-xs text-ink-500">{searchError}</p>}
      <div className={`overflow-hidden rounded-xl border ${error ? 'border-rose-500' : 'border-ink-300'}`}>
        <MapContainer center={value ? [value.lat, value.lng] : DEFAULT_CENTER} zoom={value ? 16 : 12} className="h-72 w-full sm:h-80">
          <TileLayer url={OSM_TILES} attribution={OSM_ATTRIBUTION} />
          <ClickToPick onPick={(lat, lng) => pick(lat, lng)} />
          <FlyTo target={flyTarget} />
          {value && (
            <Marker
              position={[value.lat, value.lng]}
              icon={selectedIcon}
              draggable
              eventHandlers={{
                dragend: (e) => {
                  const p = (e.target as L.Marker).getLatLng();
                  pick(p.lat, p.lng);
                },
              }}
            />
          )}
        </MapContainer>
      </div>
      <p className="mt-1.5 text-xs text-ink-500">
        {value ? `Pinned at ${value.lat.toFixed(5)}, ${value.lng.toFixed(5)}. Drag the pin or tap elsewhere to adjust.` : 'Tap the map where the issue is.'}
      </p>
      {geoMessage && <p className={`mt-1 text-xs ${geoState === 'error' ? 'text-amber-700' : 'text-ink-500'}`}>{geoMessage}</p>}
      {error && <p className="field-error">{error}</p>}
    </div>
  );
}
