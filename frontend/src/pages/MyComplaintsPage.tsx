import { ClipboardList, PlusCircle } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ComplaintCard } from '../components/ComplaintCard';
import { Alert, EmptyState, SkeletonCard } from '../components/Feedback';
import { EMPTY_FILTERS, FilterBar, type Filters } from '../components/FilterBar';
import { Pagination } from '../components/Pagination';
import { useFeed } from '../hooks/useFeed';

export default function MyComplaintsPage() {
  const [filters, setFilters] = useState<Filters>({ ...EMPTY_FILTERS, sort: 'updated' });
  const [page, setPage] = useState(0);
  const { data, loading, error, reload } = useFeed(filters, true, page);

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="page-title">My complaints</h1>
          <p className="mt-1 text-ink-600">Every issue you have reported, with its current status.</p>
        </div>
        <Link to="/report" className="btn-primary"><PlusCircle className="h-4 w-4" /> Report an Issue</Link>
      </div>
      <FilterBar value={filters} onChange={(f) => { setFilters(f); setPage(0); }} showLocality={false} />
      {error && <Alert kind="error" action={<button className="btn-secondary" onClick={reload}>Retry</button>}>{error}</Alert>}
      {loading && !data ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{Array.from({ length: 6 }, (_, i) => <SkeletonCard key={i} />)}</div>
      ) : data && data.items.length === 0 ? (
        <EmptyState icon={ClipboardList} title="No complaints found" action={<Link to="/report" className="btn-primary">Report an issue</Link>}>
          {filters === EMPTY_FILTERS ? 'You have not reported any issues yet.' : 'Try clearing the filters.'}
        </EmptyState>
      ) : (
        data && (
          <>
            <p className="text-sm text-ink-500">{data.totalItems} complaint{data.totalItems === 1 ? '' : 's'}</p>
            <div className={`grid gap-4 sm:grid-cols-2 lg:grid-cols-3 ${loading ? 'opacity-60' : ''}`}>
              {data.items.map((c) => <ComplaintCard key={c.id} complaint={c} />)}
            </div>
            <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
          </>
        )
      )}
    </div>
  );
}
