import { UserPlus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { errorMessage, fieldErrors } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Alert, Spinner } from '../components/Feedback';
import { AuthShell } from './AuthShell';

const PHONE = /^(\+?91[\s-]?)?[6-9]\d{4}[\s-]?\d{5}$/;
const PASSWORD = /^(?=.*[A-Za-z])(?=.*\d).{8,72}$/;

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', password: '', confirm: '', locality: '', address: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const validate = () => {
    const e: Record<string, string> = {};
    if (form.fullName.trim().length < 2) e.fullName = 'Enter your full name';
    if (!/^\S+@\S+\.\S+$/.test(form.email.trim())) e.email = 'Enter a valid email';
    if (!PHONE.test(form.phone.trim())) e.phone = 'Enter a valid 10-digit Indian mobile number';
    if (!PASSWORD.test(form.password)) e.password = 'Use 8-72 characters with at least one letter and one number';
    if (form.confirm !== form.password) e.confirm = 'Passwords do not match';
    if (!form.locality.trim()) e.locality = 'Enter your locality';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    if (!validate()) return;
    setBusy(true);
    try {
      await register({
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        password: form.password,
        locality: form.locality.trim(),
        address: form.address.trim() || undefined,
      });
      navigate('/', { replace: true });
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err, 'Could not create your account.'));
    } finally {
      setBusy(false);
    }
  };

  const field = (k: keyof typeof form, label: string, props: React.InputHTMLAttributes<HTMLInputElement> = {}) => (
    <div>
      <label htmlFor={k} className="label">{label}</label>
      <input
        id={k}
        className={`input ${errors[k] ? 'input-error' : ''}`}
        value={form[k]}
        onChange={set(k)}
        aria-invalid={!!errors[k]}
        aria-describedby={errors[k] ? `${k}-error` : undefined}
        {...props}
      />
      {errors[k] && <p id={`${k}-error`} className="field-error">{errors[k]}</p>}
    </div>
  );

  return (
    <AuthShell title="Create your citizen account" subtitle="Register to report civic issues in your locality and follow their progress.">
      <form onSubmit={submit} className="space-y-4" noValidate>
        {error && <Alert kind="error">{error}</Alert>}
        {field('fullName', 'Full name', { autoComplete: 'name' })}
        <div className="grid gap-4 sm:grid-cols-2">
          {field('email', 'Email', { type: 'email', autoComplete: 'email' })}
          {field('phone', 'Mobile number', { type: 'tel', autoComplete: 'tel', placeholder: '98765 43210' })}
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          {field('password', 'Password', { type: 'password', autoComplete: 'new-password' })}
          {field('confirm', 'Confirm password', { type: 'password', autoComplete: 'new-password' })}
        </div>
        {field('locality', 'Locality', { placeholder: 'e.g. Madhapur', autoComplete: 'address-level3' })}
        {field('address', 'Address (optional)', { autoComplete: 'street-address' })}
        <p className="text-xs text-ink-500">
          Your name, email and phone are shared only with the municipal officials handling your complaints, never on the public community feed.
        </p>
        <button type="submit" className="btn-primary w-full py-2.5" disabled={busy}>
          {busy ? <Spinner className="h-4 w-4 text-white" /> : <UserPlus className="h-4 w-4" />} Create account
        </button>
        <p className="text-center text-sm text-ink-600">
          Already registered?{' '}
          <Link to="/login" className="font-semibold text-brand-700 hover:underline">Sign in</Link>
        </p>
      </form>
    </AuthShell>
  );
}
