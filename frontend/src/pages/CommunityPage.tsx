import { Users } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { dashboardApi } from '../api/endpoints';
import type { CommunityStats } from '../api/types';
import { ComplaintCard } from '../components/ComplaintCard';
import { Alert, EmptyState, SkeletonCard } from '../components/Feedback';
import { EMPTY_FILTERS, FilterBar, type Filters } from '../components/FilterBar';
import { Pagination } from '../components/Pagination';
import { useFeed } from '../hooks/useFeed';

function CategoryChart({ stats }: { stats: CommunityStats }) {
  const [asTable, setAsTable] = useState(false);
  const rows = Object.entries(stats.byCategory).map(([name, count]) => ({ name, count }));
  if (rows.length === 0) return null;
  return (
    <section className="card p-5">
      <div className="mb-1 flex items-start justify-between gap-3">
        <div>
          <h2 className="font-semibold text-ink-900">Public issues by category</h2>
          <p className="text-xs text-ink-500">Confirmed duplicates are counted once.</p>
        </div>
        <button className="btn-ghost px-2 py-1 text-xs" onClick={() => setAsTable((t) => !t)} aria-pressed={asTable}>
          {asTable ? 'Show chart' : 'Show table'}
        </button>
      </div>
      {asTable ? (
        <table className="mt-3 w-full text-sm">
          <thead><tr className="text-left text-ink-500"><th className="py-1 font-medium">Category</th><th className="py-1 text-right font-medium">Issues</th></tr></thead>
          <tbody>{rows.map((r) => <tr key={r.name} className="border-t border-ink-100"><td className="py-1.5">{r.name}</td><td className="py-1.5 text-right tabular-nums">{r.count}</td></tr>)}</tbody>
        </table>
      ) : (
        <div style={{ height: Math.max(160, rows.length * 34) }} className="mt-2">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={rows} layout="vertical" margin={{ top: 0, right: 16, bottom: 0, left: 8 }} barCategoryGap={6}>
              <CartesianGrid horizontal={false} stroke="#eef0ee" />
              <XAxis type="number" allowDecimals={false} tick={{ fontSize: 12, fill: '#66706b' }} axisLine={false} tickLine={false} />
              <YAxis type="category" dataKey="name" width={170} tick={{ fontSize: 12, fill: '#3a423e' }} axisLine={false} tickLine={false} />
              <Tooltip cursor={{ fill: '#effaf3' }} formatter={(v: number) => [v, 'Issues']} contentStyle={{ borderRadius: 8, borderColor: '#dde1de', fontSize: 12 }} />
              <Bar dataKey="count" fill="#15784b" radius={[0, 4, 4, 0]} maxBarSize={20} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </section>
  );
}

export default function CommunityPage() {
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS);
  const [page, setPage] = useState(0);
  const { data, loading, error, reload } = useFeed(filters, false, page);
  const [stats, setStats] = useState<CommunityStats | null>(null);

  useEffect(() => {
    dashboardApi.community().then(setStats).catch(() => {});
  }, []);

  return (
    <div className="space-y-5">
      <div>
        <h1 className="page-title">Community feed</h1>
        <p className="mt-1 text-ink-600">Issues reported by citizens across the city. Upvote the ones that affect you and follow them for updates.</p>
      </div>
      {stats && (
        <div className="grid gap-4 lg:grid-cols-3">
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-1">
            <div className="card p-4"><p className="text-sm text-ink-500">Open public issues</p><p className="text-2xl font-bold tabular-nums text-ink-900">{stats.openIssues}</p></div>
            <div className="card p-4"><p className="text-sm text-ink-500">Resolved in the last 30 days</p><p className="text-2xl font-bold tabular-nums text-brand-700">{stats.resolvedLast30Days}</p></div>
          </div>
          <div className="lg:col-span-2"><CategoryChart stats={stats} /></div>
        </div>
      )}
      <FilterBar value={filters} onChange={(f) => { setFilters(f); setPage(0); }} />
      {error && <Alert kind="error" action={<button className="btn-secondary" onClick={reload}>Retry</button>}>{error}</Alert>}
      {loading && !data ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{Array.from({ length: 6 }, (_, i) => <SkeletonCard key={i} />)}</div>
      ) : data && data.items.length === 0 ? (
        <EmptyState icon={Users} title="No issues match your filters">Try a different search or clear the filters.</EmptyState>
      ) : (
        data && (
          <>
            <p className="text-sm text-ink-500">{data.totalItems} issue{data.totalItems === 1 ? '' : 's'}</p>
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
