import { ArrowBigUp, CheckCircle2, Globe2, Lock, RotateCcw, Send } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { errorMessage, fieldErrors, isRetryable, mediaUrl } from '../api/client';
import { complaintApi } from '../api/endpoints';
import type { ComplaintDetail, NearbyComplaint } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { CategoryIcon } from '../components/CategoryIcon';
import { Alert, Spinner } from '../components/Feedback';
import { LocationPicker, type PickedLocation } from '../components/LocationPicker';
import { PhotoPicker } from '../components/PhotoPicker';
import { StatusBadge } from '../components/StatusBadge';
import { useCategories } from '../hooks/useReference';
import { checkCoordinates } from '../lib/files';
import { formatDistance } from '../lib/format';
import { reverseGeocode } from '../lib/geocode';
import { useNotifications } from '../hooks/NotificationContext';

const newRequestId = () =>
  typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
      });

export default function ReportIssuePage() {
  const { user } = useAuth();
  const categories = useCategories();
  const navigate = useNavigate();
  const { refresh } = useNotifications();

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('');
  const [location, setLocation] = useState<PickedLocation | null>(null);
  const [locality, setLocality] = useState(user?.locality ?? '');
  const [address, setAddress] = useState('');
  const [isPublic, setIsPublic] = useState(true);
  const [photos, setPhotos] = useState<File[]>([]);

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState('');
  const [canRetry, setCanRetry] = useState(false);
  const [progress, setProgress] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [created, setCreated] = useState<ComplaintDetail | null>(null);
  const [nearby, setNearby] = useState<NearbyComplaint[]>([]);
  // Same id for retries of one submission, so a retry after a timeout never creates a duplicate.
  const requestId = useRef(newRequestId());
  const addressTouched = useRef(false);

  // Suggest existing nearby complaints of the same category (possible duplicates).
  useEffect(() => {
    if (!location) return;
    const timer = setTimeout(() => {
      complaintApi.nearby(location.lat, location.lng, category || undefined).then(setNearby).catch(() => setNearby([]));
    }, 400);
    return () => clearTimeout(timer);
  }, [location, category]);

  // Fill in the address from the map pin unless the citizen has typed one.
  useEffect(() => {
    if (!location || addressTouched.current) return;
    const ctrl = new AbortController();
    reverseGeocode(location.lat, location.lng, ctrl.signal)
      .then((r) => {
        if (addressTouched.current) return;
        if (r.address) setAddress(r.address.slice(0, 500));
        if (r.locality) setLocality(r.locality.slice(0, 120));
      })
      .catch(() => {});
    return () => ctrl.abort();
  }, [location]);

  const validate = () => {
    const e: Record<string, string> = {};
    if (title.trim().length < 5) e.title = 'Give a short title of at least 5 characters';
    if (description.trim().length < 10) e.description = 'Describe the issue in at least 10 characters';
    if (!category) e.categoryCode = 'Choose a category';
    const coord = checkCoordinates(location?.lat, location?.lng);
    if (coord) e.latitude = coord;
    if (!locality.trim()) e.locality = 'Enter the locality';
    if (!address.trim()) e.address = 'Enter an address or landmark';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const submit = async (ev?: FormEvent) => {
    ev?.preventDefault();
    setSubmitError('');
    setCanRetry(false);
    if (!validate()) {
      document.querySelector('[aria-invalid="true"], .field-error')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }
    const form = new FormData();
    form.append('title', title.trim());
    form.append('description', description.trim());
    form.append('categoryCode', category);
    form.append('locality', locality.trim());
    form.append('address', address.trim());
    form.append('latitude', String(location!.lat));
    form.append('longitude', String(location!.lng));
    form.append('isPublic', String(isPublic));
    form.append('clientRequestId', requestId.current);
    photos.forEach((p) => form.append('photos', p, p.name));

    setSubmitting(true);
    setProgress(photos.length ? 0 : null);
    try {
      const result = await complaintApi.create(form, photos.length ? setProgress : undefined);
      setCreated(result);
      refresh();
      window.scrollTo({ top: 0 });
    } catch (err) {
      setErrors(fieldErrors(err));
      setSubmitError(errorMessage(err, 'Your complaint could not be submitted.'));
      setCanRetry(isRetryable(err));
    } finally {
      setSubmitting(false);
      setProgress(null);
    }
  };

  if (created) {
    return (
      <div className="mx-auto max-w-xl">
        <div className="card p-8 text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-brand-100">
            <CheckCircle2 className="h-8 w-8 text-brand-700" />
          </div>
          <h1 className="mt-4 text-2xl font-bold text-ink-900">Complaint submitted</h1>
          <p className="mt-1 text-ink-600">Thank you for helping improve your neighbourhood.</p>
          <div className="mt-6 rounded-xl bg-ink-50 px-4 py-4">
            <p className="text-xs uppercase tracking-wide text-ink-500">Your reference number</p>
            <p className="mt-1 font-mono text-2xl font-bold text-brand-800">{created.referenceNo}</p>
          </div>
          <dl className="mt-5 grid grid-cols-2 gap-3 text-left text-sm">
            <div className="rounded-lg border border-ink-200 p-3">
              <dt className="text-ink-500">Status</dt>
              <dd className="mt-1"><StatusBadge status={created.status} /></dd>
            </div>
            <div className="rounded-lg border border-ink-200 p-3">
              <dt className="text-ink-500">Photos saved</dt>
              <dd className="mt-1 font-semibold text-ink-900">{created.citizenEvidence.length}</dd>
            </div>
            <div className="col-span-2 rounded-lg border border-ink-200 p-3">
              <dt className="text-ink-500">Routed to</dt>
              <dd className="mt-1 font-medium text-ink-900">
                {created.wardName
                  ? `${created.wardName} ward office`
                  : 'Municipal control room: the ward will be confirmed manually because the location did not match a configured ward boundary.'}
              </dd>
            </div>
          </dl>
          <div className="mt-6 flex flex-col gap-2 sm:flex-row sm:justify-center">
            <button className="btn-primary" onClick={() => navigate(`/complaints/${created.id}`)}>Track this complaint</button>
            <button
              className="btn-secondary"
              onClick={() => {
                requestId.current = newRequestId();
                addressTouched.current = false;
                setCreated(null);
                setTitle('');
                setDescription('');
                setCategory('');
                setLocation(null);
                setAddress('');
                setPhotos([]);
                setNearby([]);
              }}
            >
              Report another issue
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-6">
        <h1 className="page-title">Report an issue</h1>
        <p className="mt-1 text-ink-600">Clear photos and an exact location help officials act faster.</p>
      </div>

      <form onSubmit={submit} className="space-y-6" noValidate>
        <section className="card space-y-5 p-5 sm:p-6">
          <h2 className="font-semibold text-ink-900">1. What is the problem?</h2>
          <fieldset>
            <legend className="label">Category</legend>
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
              {categories.map((c) => (
                <label
                  key={c.code}
                  className={`flex cursor-pointer flex-col items-start gap-1.5 rounded-lg border p-3 text-sm transition-colors ${
                    category === c.code ? 'border-brand-600 bg-brand-50 ring-1 ring-brand-600' : 'border-ink-200 hover:border-ink-300'
                  }`}
                  title={c.description}
                >
                  <input type="radio" name="category" value={c.code} checked={category === c.code} onChange={() => setCategory(c.code)} className="sr-only" />
                  <CategoryIcon code={c.code} className={`h-5 w-5 ${category === c.code ? 'text-brand-700' : 'text-ink-500'}`} />
                  <span className="font-medium leading-tight text-ink-800">{c.name}</span>
                </label>
              ))}
            </div>
            {categories.length === 0 && <Spinner />}
            {errors.categoryCode && <p className="field-error">{errors.categoryCode}</p>}
          </fieldset>
          <div>
            <label htmlFor="title" className="label">Title</label>
            <input
              id="title"
              className={`input ${errors.title ? 'input-error' : ''}`}
              maxLength={150}
              placeholder="e.g. Deep pothole near the bus stop"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              aria-invalid={!!errors.title}
            />
            {errors.title && <p className="field-error">{errors.title}</p>}
          </div>
          <div>
            <label htmlFor="description" className="label">Description</label>
            <textarea
              id="description"
              rows={4}
              maxLength={4000}
              className={`input ${errors.description ? 'input-error' : ''}`}
              placeholder="What is the problem, how long has it been there, and who is affected?"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              aria-invalid={!!errors.description}
            />
            <div className="flex justify-between">
              {errors.description ? <p className="field-error">{errors.description}</p> : <span />}
              <span className="mt-1 text-xs text-ink-400">{description.length}/4000</span>
            </div>
          </div>
        </section>

        <section className="card space-y-5 p-5 sm:p-6">
          <h2 className="font-semibold text-ink-900">2. Where is it?</h2>
          <LocationPicker value={location} onChange={setLocation} error={errors.latitude || errors.longitude} />
          <div className="grid gap-4 sm:grid-cols-3">
            <div>
              <label htmlFor="locality" className="label">Locality</label>
              <input
                id="locality"
                className={`input ${errors.locality ? 'input-error' : ''}`}
                maxLength={120}
                value={locality}
                onChange={(e) => setLocality(e.target.value)}
                aria-invalid={!!errors.locality}
              />
              {errors.locality && <p className="field-error">{errors.locality}</p>}
            </div>
            <div className="sm:col-span-2">
              <label htmlFor="address" className="label">Address or landmark</label>
              <input
                id="address"
                className={`input ${errors.address ? 'input-error' : ''}`}
                maxLength={500}
                placeholder="Street, nearby landmark"
                value={address}
                onChange={(e) => {
                  addressTouched.current = true;
                  setAddress(e.target.value);
                }}
                aria-invalid={!!errors.address}
              />
              {errors.address && <p className="field-error">{errors.address}</p>}
            </div>
          </div>
          {nearby.length > 0 && (
            <Alert kind="info" title="Similar issues already reported nearby">
              <p className="mb-2">If one of these is the same problem, upvoting or following it helps officials prioritise. You can still submit your own report.</p>
              <ul className="space-y-2">
                {nearby.map((n) => (
                  <li key={n.id} className="flex items-center gap-3 rounded-lg bg-white p-2 ring-1 ring-sky-100">
                    {n.thumbnailUrl ? (
                      <img src={mediaUrl(n.thumbnailUrl)} alt="" className="h-12 w-12 shrink-0 rounded object-cover" />
                    ) : (
                      <span className="h-12 w-12 shrink-0 rounded bg-ink-100" />
                    )}
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-medium text-ink-900">{n.title}</span>
                      <span className="block text-xs text-ink-500">
                        {n.categoryName} · {formatDistance(n.distanceMeters)} · <ArrowBigUp className="inline h-3.5 w-3.5" /> {n.upvoteCount}
                      </span>
                    </span>
                    <Link to={`/complaints/${n.id}`} target="_blank" rel="noopener" className="btn-secondary shrink-0 px-3 py-1.5 text-xs">
                      View
                    </Link>
                  </li>
                ))}
              </ul>
            </Alert>
          )}
        </section>

        <section className="card space-y-5 p-5 sm:p-6">
          <h2 className="font-semibold text-ink-900">3. Add photos</h2>
          <PhotoPicker files={photos} onChange={setPhotos} disabled={submitting} />
          <fieldset>
            <legend className="label">Visibility</legend>
            <div className="grid gap-2 sm:grid-cols-2">
              {[
                { v: true, icon: Globe2, t: 'Public', d: 'Shown on the community feed and map without your name or contact details.' },
                { v: false, icon: Lock, t: 'Private', d: 'Only you and the officials handling it can see it.' },
              ].map((o) => (
                <label
                  key={o.t}
                  className={`flex cursor-pointer gap-3 rounded-lg border p-3 ${isPublic === o.v ? 'border-brand-600 bg-brand-50 ring-1 ring-brand-600' : 'border-ink-200'}`}
                >
                  <input type="radio" name="visibility" className="sr-only" checked={isPublic === o.v} onChange={() => setIsPublic(o.v)} />
                  <o.icon className="mt-0.5 h-5 w-5 shrink-0 text-brand-700" />
                  <span>
                    <span className="block text-sm font-semibold text-ink-900">{o.t}</span>
                    <span className="block text-xs text-ink-600">{o.d}</span>
                  </span>
                </label>
              ))}
            </div>
          </fieldset>
        </section>

        {submitError && (
          <Alert
            kind="error"
            title="Submission failed"
            action={
              canRetry ? (
                <button type="button" className="btn-secondary" onClick={() => submit()} disabled={submitting}>
                  <RotateCcw className="h-4 w-4" /> Retry
                </button>
              ) : undefined
            }
          >
            {submitError} {canRetry && 'Your details and photos are kept, so you can retry safely.'}
          </Alert>
        )}

        <div className="sticky bottom-0 -mx-4 border-t border-ink-200 bg-white/95 px-4 py-3 backdrop-blur sm:static sm:mx-0 sm:border-0 sm:bg-transparent sm:p-0">
          {progress != null && (
            <div className="mb-2" aria-live="polite">
              <div className="mb-1 flex justify-between text-xs text-ink-600">
                <span>{progress < 100 ? 'Uploading photos…' : 'Saving complaint…'}</span>
                <span>{progress}%</span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-ink-100" role="progressbar" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100}>
                <div className="h-full bg-brand-600 transition-all" style={{ width: `${progress}%` }} />
              </div>
            </div>
          )}
          <button type="submit" className="btn-primary w-full py-3 text-base sm:w-auto sm:px-8" disabled={submitting}>
            {submitting ? <Spinner className="h-4 w-4 text-white" /> : <Send className="h-4 w-4" />}
            {submitting ? 'Submitting…' : 'Submit complaint'}
          </button>
        </div>
      </form>
    </div>
  );
}
