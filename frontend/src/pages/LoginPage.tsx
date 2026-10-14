import { Eye, EyeOff, KeyRound, LogIn } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Alert, Spinner } from '../components/Feedback';
import { AuthShell } from './AuthShell';

// Seeded development data only (see README "Demo accounts"); hidden from production builds.
const DEMO_PASSWORD = import.meta.env.VITE_DEMO_PASSWORD || 'NagrikDemo@2026';
const DEMO_ACCOUNTS = [
  { role: 'Citizen', email: 'priya.sharma@demo.nagriksetu.in' },
  { role: 'Citizen 2', email: 'arjun.reddy@demo.nagriksetu.in' },
  { role: 'Ward Head', email: 'wardhead.mdp@demo.nagriksetu.in' },
  { role: 'Dept. Head', email: 'head.roads@demo.nagriksetu.in' },
  { role: 'Officer', email: 'officer.roads@demo.nagriksetu.in' },
  { role: 'Admin', email: 'admin@demo.nagriksetu.in' },
];

export function LoginPage() {
  const { login, sessionMessage, clearSessionMessage } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [show, setShow] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [showForgot, setShowForgot] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const user = await login(email.trim(), password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from && from !== '/login' ? from : user.role === 'CITIZEN' ? '/' : '/official', { replace: true });
    } catch (err) {
      setError(errorMessage(err, 'Could not sign in. Please try again.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthShell title="Sign in" subtitle="Welcome back. Sign in to report and track civic issues.">
      <form onSubmit={submit} className="space-y-4" noValidate>
        {sessionMessage && (
          <Alert kind="warning" action={<button type="button" className="text-xs underline" onClick={clearSessionMessage}>Dismiss</button>}>
            {sessionMessage}
          </Alert>
        )}
        {error && <Alert kind="error">{error}</Alert>}
        <div>
          <label htmlFor="email" className="label">Email</label>
          <input id="email" type="email" autoComplete="email" required className="input" value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div>
          <div className="mb-1 flex items-center justify-between">
            <label htmlFor="password" className="label mb-0">Password</label>
            <button type="button" className="text-xs font-semibold text-brand-700 hover:underline" onClick={() => setShowForgot((s) => !s)} aria-expanded={showForgot}>
              Forgot password?
            </button>
          </div>
          <div className="relative">
            <input
              id="password"
              type={show ? 'text' : 'password'}
              autoComplete="current-password"
              required
              className="input pr-10"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <button
              type="button"
              className="absolute inset-y-0 right-0 flex items-center px-3 text-ink-500"
              onClick={() => setShow((s) => !s)}
              aria-label={show ? 'Hide password' : 'Show password'}
            >
              {show ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
        </div>
        {showForgot && (
          <Alert kind="info" title="Resetting a forgotten password">
            For security, passwords are reset by the municipal administrator rather than by email. Contact your ward office or the
            municipal control room with your registered email; they will set a temporary password that you can change from your Profile page.
          </Alert>
        )}
        <button type="submit" className="btn-primary w-full py-2.5" disabled={busy || !email || !password}>
          {busy ? <Spinner className="h-4 w-4 text-white" /> : <LogIn className="h-4 w-4" />} Sign in
        </button>
        <p className="text-center text-sm text-ink-600">
          New to Nagrik Setu?{' '}
          <Link to="/register" className="font-semibold text-brand-700 hover:underline">
            Create a citizen account
          </Link>
        </p>
        {import.meta.env.DEV && (
          <div className="rounded-lg border border-ink-200 bg-ink-50 p-3 text-xs text-ink-600">
            <p className="flex items-center gap-1.5 font-semibold text-ink-700">
              <KeyRound className="h-3.5 w-3.5" /> Demo accounts (development mode only)
            </p>
            <p className="mt-0.5">Click one to fill in its email and the demo password.</p>
            <div className="mt-2 grid grid-cols-2 gap-1.5 sm:grid-cols-3">
              {DEMO_ACCOUNTS.map((a) => (
                <button
                  key={a.email}
                  type="button"
                  className="rounded-md border border-ink-200 bg-white px-2 py-1.5 text-left hover:border-brand-500 hover:bg-brand-50"
                  onClick={() => {
                    setEmail(a.email);
                    setPassword(DEMO_PASSWORD);
                    setError('');
                  }}
                >
                  <span className="block font-semibold text-ink-800">{a.role}</span>
                  <span className="block truncate text-[11px] text-ink-500">{a.email.replace('@demo.nagriksetu.in', '')}</span>
                </button>
              ))}
            </div>
          </div>
        )}
      </form>
    </AuthShell>
  );
}
