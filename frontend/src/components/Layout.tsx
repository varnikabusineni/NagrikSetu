import { Bell, ClipboardList, LayoutDashboard, LogOut, Map, Menu, PlusCircle, ShieldCheck, User as UserIcon, Users, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useNotifications } from '../hooks/NotificationContext';
import { Logo } from './Logo';

export function Layout() {
  const { user, logout, isOfficial } = useAuth();
  const { unread } = useNotifications();
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => setMenuOpen(false), [location.pathname]);

  const links = isOfficial
    ? [
        { to: '/official', label: 'Work Queue', icon: ShieldCheck, end: true },
        { to: '/community', label: 'Community', icon: Users },
        { to: '/map', label: 'Map', icon: Map },
      ]
    : [
        { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
        { to: '/complaints', label: 'My Complaints', icon: ClipboardList },
        { to: '/community', label: 'Community', icon: Users },
        { to: '/map', label: 'Map', icon: Map },
      ];

  const onLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const navClass = ({ isActive }: { isActive: boolean }) =>
    `inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
      isActive ? 'bg-brand-50 text-brand-800' : 'text-ink-600 hover:bg-ink-100 hover:text-ink-900'
    }`;

  return (
    <div className="flex min-h-screen flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded focus:bg-white focus:px-3 focus:py-2">
        Skip to content
      </a>
      <header className="sticky top-0 z-30 border-b border-ink-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-7xl items-center gap-4 px-4 sm:px-6">
          <Link to={isOfficial ? '/official' : '/'} className="shrink-0" aria-label="Nagrik Setu home">
            <Logo />
          </Link>
          <nav className="ml-4 hidden items-center gap-1 lg:flex" aria-label="Main">
            {links.map((l) => (
              <NavLink key={l.to} to={l.to} end={l.end} className={navClass}>
                <l.icon className="h-4 w-4" aria-hidden="true" />
                {l.label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-1 sm:gap-2">
            {!isOfficial && (
              <Link to="/report" className="btn-primary hidden sm:inline-flex">
                <PlusCircle className="h-4 w-4" /> Report an Issue
              </Link>
            )}
            <Link
              to="/notifications"
              className="relative rounded-lg p-2 text-ink-600 hover:bg-ink-100 hover:text-ink-900"
              aria-label={unread ? `Notifications, ${unread} unread` : 'Notifications'}
            >
              <Bell className="h-5 w-5" />
              {unread > 0 && (
                <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-saffron-500 px-1 text-[10px] font-bold text-white">
                  {unread > 99 ? '99+' : unread}
                </span>
              )}
            </Link>
            <Link
              to="/profile"
              className="hidden items-center gap-2 rounded-lg px-2 py-1.5 text-sm hover:bg-ink-100 md:inline-flex"
              title={user?.email}
            >
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-brand-100 font-semibold text-brand-800">
                {user?.fullName.charAt(0).toUpperCase()}
              </span>
              <span className="max-w-[10rem] truncate text-left">
                <span className="block truncate font-medium text-ink-900">{user?.fullName.replace(/\s*\(.*\)$/, '')}</span>
                <span className="block truncate text-xs text-ink-500">{user?.roleName}</span>
              </span>
            </Link>
            <button className="btn-ghost hidden p-2 md:inline-flex" onClick={onLogout} aria-label="Sign out" title="Sign out">
              <LogOut className="h-5 w-5" />
            </button>
            <button className="btn-ghost p-2 lg:hidden" onClick={() => setMenuOpen((o) => !o)} aria-label="Menu" aria-expanded={menuOpen}>
              {menuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </button>
          </div>
        </div>
        {menuOpen && (
          <nav className="border-t border-ink-200 bg-white px-4 py-3 lg:hidden" aria-label="Mobile">
            <div className="flex flex-col gap-1">
              {links.map((l) => (
                <NavLink key={l.to} to={l.to} end={l.end} className={navClass}>
                  <l.icon className="h-4 w-4" /> {l.label}
                </NavLink>
              ))}
              <NavLink to="/profile" className={navClass}>
                <UserIcon className="h-4 w-4" /> Profile
              </NavLink>
              {!isOfficial && (
                <Link to="/report" className="btn-primary mt-2">
                  <PlusCircle className="h-4 w-4" /> Report an Issue
                </Link>
              )}
              <button className="btn-secondary mt-1" onClick={onLogout}>
                <LogOut className="h-4 w-4" /> Sign out
              </button>
            </div>
          </nav>
        )}
      </header>
      <main id="main" className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6 sm:py-8">
        <Outlet />
      </main>
      <footer className="border-t border-ink-200 bg-white">
        <div className="mx-auto flex max-w-7xl flex-col gap-1 px-4 py-4 text-xs text-ink-500 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <span>© {new Date().getFullYear()} Nagrik Setu · Connecting citizens with their municipality</span>
          <span>Map data © OpenStreetMap contributors</span>
        </div>
      </footer>
      {!isOfficial && (
        <Link
          to="/report"
          className="fixed bottom-5 right-5 z-30 flex h-14 w-14 items-center justify-center rounded-full bg-brand-700 text-white shadow-raised hover:bg-brand-800 sm:hidden"
          aria-label="Report an issue"
        >
          <PlusCircle className="h-7 w-7" />
        </Link>
      )}
    </div>
  );
}
