import { Bell, CheckCheck } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { notificationApi } from '../api/endpoints';
import type { AppNotification, Page } from '../api/types';
import { Alert, EmptyState, FullPageSpinner } from '../components/Feedback';
import { Pagination } from '../components/Pagination';
import { useNotifications } from '../hooks/NotificationContext';
import { formatDateTime, timeAgo } from '../lib/format';

export default function NotificationsPage() {
  const navigate = useNavigate();
  const { refresh, version, unread } = useNotifications();
  const [page, setPage] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [data, setData] = useState<Page<AppNotification> | null>(null);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    notificationApi.list(page, unreadOnly).then(setData).catch((e) => setError(errorMessage(e)));
  }, [page, unreadOnly]);
  useEffect(load, [load, version]);

  const open = async (n: AppNotification) => {
    if (!n.read) {
      await notificationApi.markRead(n.id).catch(() => {});
      refresh();
    }
    if (n.complaintId) navigate(`/complaints/${n.complaintId}`);
    else load();
  };

  const markAll = async () => {
    await notificationApi.markAllRead();
    refresh();
    load();
  };

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="page-title">Notifications</h1>
          <p className="mt-1 text-sm text-ink-600">In-app updates about your complaints and issues you follow. This list refreshes automatically.</p>
        </div>
        <button className="btn-secondary" onClick={markAll} disabled={unread === 0}><CheckCheck className="h-4 w-4" /> Mark all as read</button>
      </div>
      <div className="inline-flex rounded-lg border border-ink-300 bg-white p-0.5 text-sm">
        {[false, true].map((u) => (
          <button key={String(u)} className={`rounded-md px-3 py-1.5 font-medium ${unreadOnly === u ? 'bg-brand-700 text-white' : 'text-ink-700'}`} onClick={() => { setUnreadOnly(u); setPage(0); }}>
            {u ? `Unread (${unread})` : 'All'}
          </button>
        ))}
      </div>
      {error && <Alert kind="error">{error}</Alert>}
      {!data ? (
        <FullPageSpinner />
      ) : data.items.length === 0 ? (
        <EmptyState icon={Bell} title={unreadOnly ? 'No unread notifications' : 'No notifications yet'}>Updates about your complaints will appear here.</EmptyState>
      ) : (
        <>
          <ul className="card divide-y divide-ink-100 overflow-hidden">
            {data.items.map((n) => (
              <li key={n.id}>
                <button onClick={() => open(n)} className={`flex w-full gap-3 px-4 py-3.5 text-left hover:bg-ink-50 ${n.read ? '' : 'bg-brand-50/50'}`}>
                  <span className={`mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full ${n.read ? 'bg-transparent' : 'bg-saffron-500'}`} aria-hidden="true" />
                  <span className="min-w-0 flex-1">
                    <span className={`block text-sm ${n.read ? 'text-ink-700' : 'font-semibold text-ink-900'}`}>{n.title}</span>
                    <span className="mt-0.5 block text-sm text-ink-600">{n.message}</span>
                    <span className="mt-1 block text-xs text-ink-400" title={formatDateTime(n.createdAt)}>
                      {timeAgo(n.createdAt)}{n.complaintReference ? ` · ${n.complaintReference}` : ''}{!n.read && <span className="sr-only"> (unread)</span>}
                    </span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
          <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}
