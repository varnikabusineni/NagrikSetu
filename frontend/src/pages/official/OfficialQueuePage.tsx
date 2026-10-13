import { Copy, Inbox, MapPinned } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage } from '../../api/client';
import { officialApi } from '../../api/endpoints';
import type { ComplaintStatus, ComplaintSummary, DuplicateFlag, OfficialStats, Page } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { CategoryIcon } from '../../components/CategoryIcon';
import { Alert, EmptyState, FullPageSpinner } from '../../components/Feedback';
import { Pagination } from '../../components/Pagination';
import { PriorityBadge, StatusBadge } from '../../components/StatusBadge';
import { useNotifications } from '../../hooks/NotificationContext';
import { timeAgo } from '../../lib/format';
import { ALL_STATUSES, STATUS_META } from '../../lib/status';

export default function OfficialQueuePage() {
  const { user } = useAuth();
  const { version } = useNotifications();
  const [stats, setStats] = useState<OfficialStats | null>(null);
  const [status, setStatus] = useState<ComplaintStatus | ''>('');
  const [view, setView] = useState<'all' | 'unverified'>('all');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<ComplaintSummary> | null>(null);
  const [flags, setFlags] = useState<DuplicateFlag[]>([]);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setError('');
    officialApi
      .queue({ status: status ? [status] : undefined, q: q || undefined, view, page })
      .then(setData)
      .catch((e) => setError(errorMessage(e)));
    officialApi.stats().then(setStats).catch(() => {});
    officialApi.duplicateFlags().then(setFlags).catch(() => setFlags([]));
  }, [status, q, view, page]);

  useEffect(() => {
    const t = setTimeout(load, q ? 300 : 0);
    return () => clearTimeout(t);
  }, [load, version, q]);

  const review = async (id: number, confirm: boolean) => {
    try {
      await officialApi.reviewDuplicate(id, confirm);
      load();
    } catch (e) {
      setError(errorMessage(e));
    }
  };

  if (!stats && !data && !error) return <FullPageSpinner label="Loading your work queue…" />;

  return (
    <div className="space-y-5">
      <div>
        <h1 className="page-title">Work queue</h1>
        <p className="mt-1 text-ink-600">{user?.roleName} · {stats?.scopeLabel}</p>
      </div>

      {stats && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-7">
          {ALL_STATUSES.map((s) => (
            <button
              key={s}
              onClick={() => { setStatus(status === s ? '' : s); setView('all'); setPage(0); }}
              className={`card p-3 text-left transition-shadow hover:shadow-raised ${status === s ? 'ring-2 ring-brand-600' : ''}`}
              aria-pressed={status === s}
            >
              <span className="flex items-center gap-1.5 text-xs text-ink-500"><span className={`h-2 w-2 rounded-full ${STATUS_META[s].dot}`} />{STATUS_META[s].label}</span>
              <span className="mt-1 block text-2xl font-bold tabular-nums text-ink-900">{stats.byStatus[s] ?? 0}</span>
            </button>
          ))}
        </div>
      )}

      {stats && stats.pendingWardVerification > 0 && (
        <Alert kind="warning" title={`${stats.pendingWardVerification} complaint(s) need ward verification`}
          action={<button className="btn-secondary" onClick={() => { setView('unverified'); setStatus(''); setPage(0); }}><MapPinned className="h-4 w-4" /> Review</button>}>
          Their locations did not match a configured ward boundary.
        </Alert>
      )}

      {flags.length > 0 && (
        <section className="card p-5">
          <h2 className="mb-3 flex items-center gap-2 font-semibold text-ink-900"><Copy className="h-4 w-4" /> Possible duplicates flagged by citizens</h2>
          <ul className="divide-y divide-ink-100">
            {flags.map((f) => (
              <li key={f.id} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center">
                <div className="min-w-0 flex-1 text-sm">
                  <Link to={`/complaints/${f.complaintId}`} className="font-mono font-semibold text-brand-800 hover:underline">{f.complaintReference}</Link>
                  <span className="text-ink-500"> may duplicate </span>
                  <Link to={`/complaints/${f.duplicateOfId}`} className="font-mono font-semibold text-brand-800 hover:underline">{f.duplicateOfReference}</Link>
                  <p className="truncate text-ink-600">{f.complaintTitle} ↔ {f.duplicateOfTitle}</p>
                  {f.reason && <p className="text-xs text-ink-500">“{f.reason}”</p>}
                </div>
                <div className="flex gap-2">
                  <button className="btn-primary px-3 py-1.5" onClick={() => review(f.id, true)}>Confirm duplicate</button>
                  <button className="btn-secondary px-3 py-1.5" onClick={() => review(f.id, false)}>Dismiss</button>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="card flex flex-wrap items-center gap-2 p-3">
        <input className="input max-w-sm" placeholder="Search title, reference or locality" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} aria-label="Search queue" />
        {view === 'unverified' && <span className="chip bg-amber-50 text-amber-800">Showing complaints awaiting ward verification</span>}
        {(status || view !== 'all') && <button className="btn-ghost px-2 py-1 text-xs" onClick={() => { setStatus(''); setView('all'); }}>Show all</button>}
      </div>

      {error && <Alert kind="error" action={<button className="btn-secondary" onClick={load}>Retry</button>}>{error}</Alert>}
      {data && data.items.length === 0 ? (
        <EmptyState icon={Inbox} title="Nothing here">No complaints match this view.</EmptyState>
      ) : data ? (
        <>
          <ul className="card divide-y divide-ink-100 overflow-hidden">
            {data.items.map((c) => (
              <li key={c.id}>
                <Link to={`/complaints/${c.id}`} className="flex items-center gap-3 px-4 py-3 hover:bg-ink-50">
                  <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-700"><CategoryIcon code={c.categoryCode} className="h-5 w-5" /></span>
                  <span className="min-w-0 flex-1">
                    <span className="flex items-center gap-2">
                      <span className="truncate font-medium text-ink-900">{c.title}</span>
                      <PriorityBadge priority={c.priority} />
                    </span>
                    <span className="block truncate text-xs text-ink-500">
                      <span className="font-mono">{c.referenceNo}</span> · {c.locality} · {c.departmentName ?? 'No department yet'} · {c.photoCount} photo{c.photoCount === 1 ? '' : 's'} · updated {timeAgo(c.updatedAt)}
                    </span>
                  </span>
                  <StatusBadge status={c.status} />
                </Link>
              </li>
            ))}
          </ul>
          <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
        </>
      ) : null}
    </div>
  );
}
