import { Search, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import type { ComplaintStatus } from '../api/types';
import { useCategories } from '../hooks/useReference';
import { ALL_STATUSES, STATUS_META } from '../lib/status';

export interface Filters {
  q: string;
  category: string;
  status: ComplaintStatus | '';
  locality: string;
  from: string;
  to: string;
  sort: 'newest' | 'activity' | 'updated';
}

export const EMPTY_FILTERS: Filters = { q: '', category: '', status: '', locality: '', from: '', to: '', sort: 'newest' };

/** Filters live in one row above the results; text inputs apply after a short pause. */
export function FilterBar({ value, onChange, showLocality = true, statuses = ALL_STATUSES }: {
  value: Filters;
  onChange: (f: Filters) => void;
  showLocality?: boolean;
  statuses?: ComplaintStatus[];
}) {
  const categories = useCategories();
  const [q, setQ] = useState(value.q);
  const [locality, setLocality] = useState(value.locality);

  useEffect(() => {
    const t = setTimeout(() => {
      if (q !== value.q || locality !== value.locality) onChange({ ...value, q, locality });
    }, 350);
    return () => clearTimeout(t);
  }, [q, locality]); // eslint-disable-line react-hooks/exhaustive-deps

  const active = value.category || value.status || value.locality || value.from || value.to || value.q;

  return (
    <div className="card p-3">
      <div className="grid gap-2 md:grid-cols-12">
        <div className="relative md:col-span-4">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-400" />
          <input className="input pl-9" placeholder="Search title, description or reference" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search" />
        </div>
        <select className="input md:col-span-2" value={value.category} onChange={(e) => onChange({ ...value, category: e.target.value })} aria-label="Category">
          <option value="">All categories</option>
          {categories.map((c) => <option key={c.code} value={c.code}>{c.name}</option>)}
        </select>
        <select className="input md:col-span-2" value={value.status} onChange={(e) => onChange({ ...value, status: e.target.value as ComplaintStatus | '' })} aria-label="Status">
          <option value="">All statuses</option>
          {statuses.map((s) => <option key={s} value={s}>{STATUS_META[s].label}</option>)}
        </select>
        {showLocality && (
          <input className="input md:col-span-2" placeholder="Locality" value={locality} onChange={(e) => setLocality(e.target.value)} aria-label="Locality" />
        )}
        <select className={`input ${showLocality ? 'md:col-span-2' : 'md:col-span-4'}`} value={value.sort} onChange={(e) => onChange({ ...value, sort: e.target.value as Filters['sort'] })} aria-label="Sort">
          <option value="newest">Newest first</option>
          <option value="activity">Most community activity</option>
          <option value="updated">Recently updated</option>
        </select>
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-2 text-sm">
        <label className="flex items-center gap-1.5 text-ink-600">
          From <input type="date" className="input w-auto py-1" value={value.from} max={value.to || undefined} onChange={(e) => onChange({ ...value, from: e.target.value })} />
        </label>
        <label className="flex items-center gap-1.5 text-ink-600">
          To <input type="date" className="input w-auto py-1" value={value.to} min={value.from || undefined} onChange={(e) => onChange({ ...value, to: e.target.value })} />
        </label>
        {active && (
          <button
            className="btn-ghost px-2 py-1 text-xs"
            onClick={() => {
              setQ('');
              setLocality('');
              onChange({ ...EMPTY_FILTERS, sort: value.sort });
            }}
          >
            <X className="h-3.5 w-3.5" /> Clear filters
          </button>
        )}
      </div>
    </div>
  );
}
