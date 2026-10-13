import { List, Map as MapIcon, MapPinOff } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { MapContainer, Polygon, TileLayer, Tooltip as LeafletTooltip } from 'react-leaflet';
import { Link, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { complaintApi } from '../api/endpoints';
import type { ComplaintStatus, MapMarker } from '../api/types';
import { CategoryIcon } from '../components/CategoryIcon';
import { ClusterLayer, type ClusterPoint } from '../components/ClusterLayer';
import { Alert, EmptyState, Spinner } from '../components/Feedback';
import { OSM_ATTRIBUTION, OSM_TILES } from '../components/LocationPicker';
import { StatusBadge } from '../components/StatusBadge';
import { useCategories, useWards } from '../hooks/useReference';
import { timeAgo } from '../lib/format';
import { DEFAULT_CENTER } from '../lib/geocode';
import { ALL_STATUSES, STATUS_META } from '../lib/status';

const escapeHtml = (s: string) =>
  s.replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch]!);

export default function MapPage() {
  const navigate = useNavigate();
  const categories = useCategories();
  const wards = useWards();
  const [markers, setMarkers] = useState<MapMarker[] | null>(null);
  const [error, setError] = useState('');
  const [category, setCategory] = useState('');
  const [statuses, setStatuses] = useState<ComplaintStatus[]>([]);
  const [view, setView] = useState<'map' | 'list'>('map');
  const [showWards, setShowWards] = useState(false);

  const load = useCallback(() => {
    setMarkers(null);
    complaintApi
      .map({ category: category || undefined, status: statuses.length ? statuses : undefined })
      .then(setMarkers)
      .catch((e) => setError(errorMessage(e)));
  }, [category, statuses]);
  useEffect(load, [load]);

  const points = useMemo<ClusterPoint[]>(
    () =>
      (markers ?? []).map((m) => ({
        id: m.id,
        lat: m.latitude,
        lng: m.longitude,
        colour: STATUS_META[m.status].marker,
        popupHtml: `<div style="min-width:200px">
            <div style="font-size:11px;color:#66706b;font-family:monospace">${escapeHtml(m.referenceNo)}</div>
            <div style="font-weight:600;color:#161a18;margin:2px 0 4px">${escapeHtml(m.title)}</div>
            <div style="font-size:12px;color:#4b5550">${escapeHtml(m.categoryName)} · ${escapeHtml(m.locality)}</div>
            <div style="font-size:12px;margin-top:4px"><span style="display:inline-block;width:8px;height:8px;border-radius:9999px;background:${STATUS_META[m.status].marker};margin-right:4px"></span>${STATUS_META[m.status].label} · ${m.upvoteCount} upvote${m.upvoteCount === 1 ? '' : 's'}</div>
            <button data-open style="margin-top:8px;background:#12603e;color:#fff;border:0;border-radius:6px;padding:5px 10px;font-size:12px;font-weight:600;cursor:pointer">View details</button>
          </div>`,
      })),
    [markers],
  );
  const openComplaint = useCallback((id: number) => navigate(`/complaints/${id}`), [navigate]);

  const wardPolygons = useMemo(
    () =>
      wards
        .filter((w) => w.boundaryJson)
        .map((w) => {
          try {
            return { ward: w, coords: JSON.parse(w.boundaryJson!) as [number, number][] };
          } catch {
            return null;
          }
        })
        .filter((x): x is NonNullable<typeof x> => x !== null),
    [wards],
  );

  const toggleStatus = (s: ComplaintStatus) => setStatuses((cur) => (cur.includes(s) ? cur.filter((x) => x !== s) : [...cur, s]));

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="page-title">Issue map</h1>
          <p className="mt-1 text-ink-600">Public complaints and your own reports, at their reported locations.</p>
        </div>
        <div className="inline-flex rounded-lg border border-ink-300 bg-white p-0.5" role="group" aria-label="View">
          <button className={`btn px-3 py-1.5 ${view === 'map' ? 'bg-brand-700 text-white' : 'text-ink-700'}`} onClick={() => setView('map')} aria-pressed={view === 'map'}>
            <MapIcon className="h-4 w-4" /> Map
          </button>
          <button className={`btn px-3 py-1.5 ${view === 'list' ? 'bg-brand-700 text-white' : 'text-ink-700'}`} onClick={() => setView('list')} aria-pressed={view === 'list'}>
            <List className="h-4 w-4" /> List
          </button>
        </div>
      </div>

      <div className="card flex flex-wrap items-center gap-3 p-3">
        <select className="input w-auto" value={category} onChange={(e) => setCategory(e.target.value)} aria-label="Category">
          <option value="">All categories</option>
          {categories.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
        </select>
        <div className="flex flex-wrap gap-1.5" role="group" aria-label="Filter by status">
          {ALL_STATUSES.map((s) => (
            <button
              key={s}
              onClick={() => toggleStatus(s)}
              aria-pressed={statuses.includes(s)}
              className={`chip border ${statuses.includes(s) ? 'border-ink-800 bg-ink-800 text-white' : 'border-ink-200 bg-white text-ink-700 hover:border-ink-400'}`}
            >
              <span className="h-2 w-2 rounded-full" style={{ background: STATUS_META[s].marker }} aria-hidden="true" />
              {STATUS_META[s].label}
            </button>
          ))}
        </div>
        {wardPolygons.length > 0 && view === 'map' && (
          <label className="ml-auto flex items-center gap-2 text-sm text-ink-700">
            <input type="checkbox" checked={showWards} onChange={(e) => setShowWards(e.target.checked)} className="h-4 w-4 rounded border-ink-300" />
            Show ward boundaries
          </label>
        )}
      </div>

      {error && <Alert kind="error" action={<button className="btn-secondary" onClick={load}>Retry</button>}>{error}</Alert>}

      {view === 'map' ? (
        <div className="relative overflow-hidden rounded-xl border border-ink-200 shadow-card">
          <MapContainer center={DEFAULT_CENTER} zoom={12} className="h-[65vh] min-h-[420px] w-full">
            <TileLayer url={OSM_TILES} attribution={OSM_ATTRIBUTION} />
            {showWards &&
              wardPolygons.map(({ ward, coords }) => (
                <Polygon key={ward.id} positions={coords} pathOptions={{ color: '#12603e', weight: 1.5, fillOpacity: 0.05, dashArray: '4 4' }}>
                  <LeafletTooltip sticky>{ward.name}{ward.boundarySource?.startsWith('DEMO') ? ' (demo boundary)' : ''}</LeafletTooltip>
                </Polygon>
              ))}
            <ClusterLayer points={points} onOpen={openComplaint} />
          </MapContainer>
          {markers === null && (
            <div className="absolute inset-0 flex items-center justify-center bg-white/60"><Spinner className="h-8 w-8" /></div>
          )}
          <div className="absolute bottom-3 left-3 z-[400] rounded-lg bg-white/95 p-3 text-xs shadow-raised">
            <p className="mb-1.5 font-semibold text-ink-800">Legend</p>
            <ul className="grid grid-cols-2 gap-x-4 gap-y-1">
              {ALL_STATUSES.map((s) => (
                <li key={s} className="flex items-center gap-1.5 text-ink-700">
                  <span className="marker-pin inline-block h-3 w-3" style={{ background: STATUS_META[s].marker }} aria-hidden="true" />
                  {STATUS_META[s].label}
                </li>
              ))}
              <li className="col-span-2 mt-1 flex items-center gap-1.5 text-ink-700">
                <span className="marker-cluster-custom inline-flex h-5 w-5 text-[10px]" style={{ borderWidth: 2 }} aria-hidden="true">3</span>
                Cluster of nearby issues (click to zoom)
              </li>
            </ul>
          </div>
          {markers && (
            <div className="absolute right-3 top-3 z-[400] rounded-lg bg-white/95 px-3 py-1.5 text-xs font-medium text-ink-700 shadow">
              {markers.length} issue{markers.length === 1 ? '' : 's'} shown
            </div>
          )}
        </div>
      ) : markers === null ? (
        <div className="flex justify-center py-10"><Spinner className="h-8 w-8" /></div>
      ) : markers.length === 0 ? (
        <EmptyState icon={MapPinOff} title="No issues match these filters" />
      ) : (
        <ul className="card divide-y divide-ink-100">
          {markers.map((m) => (
            <li key={m.id}>
              <Link to={`/complaints/${m.id}`} className="flex items-center gap-3 px-4 py-3 hover:bg-ink-50">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-700"><CategoryIcon code={m.categoryCode} /></span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium text-ink-900">{m.title}</span>
                  <span className="block truncate text-xs text-ink-500"><span className="font-mono">{m.referenceNo}</span> · {m.locality} · {timeAgo(m.createdAt)}{m.mine ? ' · Your report' : ''}</span>
                </span>
                <StatusBadge status={m.status} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
