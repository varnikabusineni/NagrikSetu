import { ArrowBigUp, Bell, CheckCircle2, ClipboardList, Hourglass, PlusCircle, Users, Wrench } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { dashboardApi } from '../api/endpoints';
import type { CitizenDashboard } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { CategoryIcon } from '../components/CategoryIcon';
import { Alert, EmptyState, FullPageSpinner } from '../components/Feedback';
import { StatCard } from '../components/StatCard';
import { StatusBadge } from '../components/StatusBadge';
import { useNotifications } from '../hooks/NotificationContext';
import { timeAgo } from '../lib/format';

export default function DashboardPage() {
  const { user } = useAuth();
  const { version } = useNotifications();
  const [data, setData] = useState<CitizenDashboard | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    dashboardApi.citizen().then(setData).catch((e) => setError(errorMessage(e)));
  }, []);
  useEffect(load, [load, version]);

  if (error && !data) return <Alert kind="error" action={<button className="btn-secondary" onClick={load}>Retry</button>}>{error}</Alert>;
  if (!data) return <FullPageSpinner label="Loading your dashboard…" />;
  const firstName = user?.fullName.split(' ')[0] ?? '';
  const t = data.totals;

  return (
    <div className="space-y-8">
      <section className="relative overflow-hidden rounded-2xl bg-brand-800 px-6 py-7 text-white sm:px-8">
        <div className="pointer-events-none absolute -right-10 -top-16 h-56 w-56 rounded-full bg-brand-600/40" aria-hidden="true" />
        <div className="relative flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-sm text-brand-100">Namaste,</p>
            <h1 className="text-2xl font-bold sm:text-3xl">{firstName}</h1>
            <p className="mt-1 max-w-lg text-sm text-brand-100">
              Spotted a pothole, overflowing garbage or a broken streetlight{user?.locality ? ` in ${user.locality}` : ''}? Report it with a photo and we&apos;ll route it to the right department.
            </p>
          </div>
          <Link to="/report" className="btn inline-flex shrink-0 bg-white px-5 py-3 text-base text-brand-800 hover:bg-brand-50">
            <PlusCircle className="h-5 w-5" /> Report an Issue
          </Link>
        </div>
      </section>

      <section aria-label="Your complaints at a glance" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard label="Total submitted" value={t.total} icon={ClipboardList} tone="sky" />
        <StatCard label="Awaiting review" value={t.awaitingReview} icon={Hourglass} tone="amber" hint="Submitted, under review or reopened" />
        <StatCard label="In progress" value={t.inProgress} icon={Wrench} tone="violet" hint="Officer assigned or work under way" />
        <StatCard label="Resolved" value={t.resolved} icon={CheckCircle2} tone="brand" />
      </section>

      <div className="grid gap-6 lg:grid-cols-3 [&>*]:min-w-0">
        <section className="card p-5 lg:col-span-2">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-ink-900">Recently updated</h2>
            <Link to="/complaints" className="text-sm font-semibold text-brand-700 hover:underline">View all</Link>
          </div>
          {data.recentlyUpdated.length === 0 ? (
            <EmptyState icon={ClipboardList} title="No complaints yet" action={<Link to="/report" className="btn-primary">Report your first issue</Link>}>
              Issues you report will appear here with their latest status.
            </EmptyState>
          ) : (
            <ul className="divide-y divide-ink-100">
              {data.recentlyUpdated.map((c) => (
                <li key={c.id}>
                  <Link to={`/complaints/${c.id}`} className="flex items-center gap-3 py-3 hover:bg-ink-50 sm:-mx-2 sm:rounded-lg sm:px-2">
                    <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
                      <CategoryIcon code={c.categoryCode} className="h-5 w-5" />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-medium text-ink-900">{c.title}</span>
                      <span className="block truncate text-xs text-ink-500">
                        <span className="font-mono">{c.referenceNo}</span> · {c.locality} · updated {timeAgo(c.updatedAt)}
                      </span>
                    </span>
                    <StatusBadge status={c.status} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="card p-5">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-ink-900">Notifications</h2>
            {data.unreadNotifications > 0 && <span className="chip bg-saffron-500 text-white">{data.unreadNotifications} new</span>}
          </div>
          {data.notifications.length === 0 ? (
            <p className="text-sm text-ink-500">You&apos;re all caught up.</p>
          ) : (
            <ul className="space-y-3">
              {data.notifications.map((n) => (
                <li key={n.id} className="flex gap-2.5">
                  <Bell className={`mt-0.5 h-4 w-4 shrink-0 ${n.read ? 'text-ink-300' : 'text-saffron-500'}`} />
                  <div className="min-w-0">
                    <p className={`text-sm ${n.read ? 'text-ink-600' : 'font-semibold text-ink-900'}`}>{n.title}</p>
                    <p className="text-xs text-ink-500">{timeAgo(n.createdAt)}</p>
                  </div>
                </li>
              ))}
            </ul>
          )}
          <Link to="/notifications" className="btn-secondary mt-4 w-full">Open notification centre</Link>
        </section>
      </div>

      <section>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="flex items-center gap-2 text-lg font-semibold text-ink-900">
            <Users className="h-5 w-5 text-brand-700" /> Community issues receiving attention
          </h2>
          <Link to="/community" className="text-sm font-semibold text-brand-700 hover:underline">Browse community</Link>
        </div>
        {data.communityAttention.length === 0 ? (
          <p className="text-sm text-ink-500">No open community issues right now.</p>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
            {data.communityAttention.map((c) => (
              <li key={c.id}>
                <Link to={`/complaints/${c.id}`} className="card block h-full p-4 hover:shadow-raised">
                  <div className="flex items-center justify-between">
                    <StatusBadge status={c.status} />
                    <span className="inline-flex items-center text-sm font-semibold text-ink-700" title="Upvotes">
                      <ArrowBigUp className="h-4 w-4" /> {c.upvoteCount}
                    </span>
                  </div>
                  <p className="mt-2 line-clamp-2 text-sm font-medium text-ink-900">{c.title}</p>
                  <p className="mt-1 text-xs text-ink-500">{c.locality}</p>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
