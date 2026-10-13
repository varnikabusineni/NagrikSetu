import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { notificationApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';

interface NotificationState {
  unread: number;
  refresh: () => void;
  /** Increments whenever new notifications arrive, so pages can refetch their data. */
  version: number;
}

const NotificationContext = createContext<NotificationState>({ unread: 0, refresh: () => {}, version: 0 });

const POLL_MS = Number(import.meta.env.VITE_NOTIFICATION_POLL_MS) || 30_000;

/** Polls the unread count so updates appear without reloading; pauses while the tab is hidden. */
export function NotificationProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [unread, setUnread] = useState(0);
  const [version, setVersion] = useState(0);
  const last = useRef(0);

  const refresh = useCallback(() => {
    if (!user) return;
    notificationApi
      .unreadCount()
      .then((count) => {
        if (count > last.current) setVersion((v) => v + 1);
        last.current = count;
        setUnread(count);
      })
      .catch(() => {
        /* transient failures are retried on the next poll */
      });
  }, [user]);

  useEffect(() => {
    if (!user) {
      setUnread(0);
      last.current = 0;
      return;
    }
    refresh();
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') refresh();
    }, POLL_MS);
    const onVisible = () => document.visibilityState === 'visible' && refresh();
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [user, refresh]);

  return <NotificationContext.Provider value={{ unread, refresh, version }}>{children}</NotificationContext.Provider>;
}

export const useNotifications = () => useContext(NotificationContext);
