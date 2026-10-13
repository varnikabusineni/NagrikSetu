import { ArrowBigUp, ArrowLeft, Copy, Eye, EyeOff, Flag, Globe2, Lock, MapPin, MessageSquarePlus, Star } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { MapContainer, Marker, TileLayer } from 'react-leaflet';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { errorMessage, fieldErrors } from '../api/client';
import { complaintApi } from '../api/endpoints';
import type { ComplaintDetail } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { CategoryIcon } from '../components/CategoryIcon';
import { EvidenceGallery } from '../components/EvidenceGallery';
import { Alert, EmptyState, FullPageSpinner, Spinner } from '../components/Feedback';
import { OSM_ATTRIBUTION, OSM_TILES, pinIcon } from '../components/LocationPicker';
import { Modal } from '../components/Modal';
import { PhotoPicker } from '../components/PhotoPicker';
import { PriorityBadge, StatusBadge } from '../components/StatusBadge';
import { StatusTracker } from '../components/StatusTracker';
import { Timeline } from '../components/Timeline';
import { useNotifications } from '../hooks/NotificationContext';
import { formatDateTime } from '../lib/format';
import { OfficialActions } from './official/OfficialActions';

function Detail({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-3 py-2 text-sm">
      <dt className="text-ink-500">{label}</dt>
      <dd className="text-right font-medium text-ink-900">{children}</dd>
    </div>
  );
}

function FeedbackForm({ complaint, onSaved }: { complaint: ComplaintDetail; onSaved: () => void }) {
  const existing = complaint.feedback;
  const [editing, setEditing] = useState(!existing);
  const [rating, setRating] = useState(existing?.rating ?? 0);
  const [comment, setComment] = useState(existing?.comment ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const save = async () => {
    setBusy(true);
    setError('');
    try {
      if (existing) await complaintApi.editFeedback(complaint.id, rating, comment || undefined);
      else await complaintApi.giveFeedback(complaint.id, rating, comment || undefined);
      setEditing(false);
      onSaved();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  if (existing && !editing) {
    return (
      <div>
        <div className="flex items-center gap-1" aria-label={`Your rating: ${existing.rating} out of 5`}>
          {[1, 2, 3, 4, 5].map((n) => <Star key={n} className={`h-5 w-5 ${n <= existing.rating ? 'fill-saffron-500 text-saffron-500' : 'text-ink-300'}`} />)}
        </div>
        {existing.comment && <p className="mt-2 text-sm text-ink-700">“{existing.comment}”</p>}
        {complaint.canGiveFeedback && <button className="btn-ghost mt-2 px-2 py-1 text-xs" onClick={() => setEditing(true)}>Edit feedback</button>}
      </div>
    );
  }
  return (
    <div className="space-y-3">
      {error && <Alert kind="error">{error}</Alert>}
      <div className="flex gap-1" role="radiogroup" aria-label="Rating">
        {[1, 2, 3, 4, 5].map((n) => (
          <button key={n} type="button" role="radio" aria-checked={rating === n} aria-label={`${n} star${n > 1 ? 's' : ''}`} onClick={() => setRating(n)} className="rounded p-0.5">
            <Star className={`h-7 w-7 ${n <= rating ? 'fill-saffron-500 text-saffron-500' : 'text-ink-300 hover:text-saffron-500'}`} />
          </button>
        ))}
      </div>
      <textarea className="input" rows={3} maxLength={1000} placeholder="How was the resolution? (optional)" value={comment} onChange={(e) => setComment(e.target.value)} />
      <button className="btn-primary" onClick={save} disabled={busy || rating === 0}>
        {busy && <Spinner className="h-4 w-4 text-white" />} {existing ? 'Save changes' : 'Submit feedback'}
      </button>
    </div>
  );
}

export default function ComplaintDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { isOfficial } = useAuth();
  const { version } = useNotifications();
  const [c, setC] = useState<ComplaintDetail | null>(null);
  const [error, setError] = useState('');
  const [notFound, setNotFound] = useState(false);
  const [toast, setToast] = useState('');
  const [busy, setBusy] = useState(false);

  const [respondOpen, setRespondOpen] = useState(false);
  const [respondText, setRespondText] = useState('');
  const [respondPhotos, setRespondPhotos] = useState<File[]>([]);
  const [respondProgress, setRespondProgress] = useState<number | null>(null);
  const [flagOpen, setFlagOpen] = useState(false);
  const [flagRef, setFlagRef] = useState('');
  const [flagReason, setFlagReason] = useState('');
  const [modalError, setModalError] = useState('');

  const load = useCallback(() => {
    complaintApi
      .get(id!)
      .then((d) => {
        setC(d);
        setError('');
      })
      .catch((e) => {
        if (e?.response?.status === 404) setNotFound(true);
        else setError(errorMessage(e));
      });
  }, [id]);
  useEffect(load, [load, version]);

  const flash = (m: string) => {
    setToast(m);
    window.setTimeout(() => setToast(''), 4000);
  };

  const toggle = async (kind: 'upvote' | 'follow') => {
    if (!c) return;
    setBusy(true);
    try {
      if (kind === 'upvote') {
        const r = c.upvoted ? await complaintApi.removeUpvote(c.id) : await complaintApi.upvote(c.id);
        setC({ ...c, upvoted: r.upvoted, upvoteCount: r.upvoteCount });
      } else {
        const r = c.followed ? await complaintApi.unfollow(c.id) : await complaintApi.follow(c.id);
        setC({ ...c, followed: r.followed, followerCount: r.followerCount });
        flash(r.followed ? 'You will be notified about updates to this issue.' : 'You will no longer receive updates.');
      }
    } catch (e) {
      flash(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const respond = async () => {
    if (!c) return;
    const form = new FormData();
    if (respondText.trim()) form.append('message', respondText.trim());
    respondPhotos.forEach((p) => form.append('photos', p, p.name));
    setBusy(true);
    setModalError('');
    setRespondProgress(respondPhotos.length ? 0 : null);
    try {
      setC(await complaintApi.respond(c.id, form, respondPhotos.length ? setRespondProgress : undefined));
      setRespondOpen(false);
      setRespondText('');
      setRespondPhotos([]);
      flash('Your information was added and the officials were notified.');
    } catch (e) {
      setModalError(errorMessage(e));
    } finally {
      setBusy(false);
      setRespondProgress(null);
    }
  };

  const flag = async () => {
    if (!c) return;
    setBusy(true);
    setModalError('');
    try {
      const r = await complaintApi.flagDuplicate(c.id, flagRef.trim(), flagReason.trim() || undefined);
      setFlagOpen(false);
      flash(r.message);
    } catch (e) {
      setModalError(fieldErrors(e).duplicateOfReference ?? errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  if (notFound) {
    return (
      <EmptyState icon={Lock} title="Complaint not available" action={<button className="btn-secondary" onClick={() => navigate(-1)}>Go back</button>}>
        This complaint does not exist, or it is private and you do not have access to it.
      </EmptyState>
    );
  }
  if (error && !c) return <Alert kind="error" action={<button className="btn-secondary" onClick={load}>Retry</button>}>{error}</Alert>;
  if (!c) return <FullPageSpinner label="Loading complaint…" />;

  const owner = c.access === 'OWNER';
  const official = c.access === 'OFFICIAL';

  return (
    <div className="space-y-6">
      <button className="btn-ghost -ml-2 px-2" onClick={() => navigate(-1)}><ArrowLeft className="h-4 w-4" /> Back</button>

      {toast && <div className="fixed bottom-6 left-1/2 z-50 -translate-x-1/2 rounded-lg bg-ink-900 px-4 py-2.5 text-sm text-white shadow-raised" role="status">{toast}</div>}

      <header className="card p-5 sm:p-6">
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <button
            className="inline-flex items-center gap-1 font-mono font-semibold text-brand-800 hover:underline"
            onClick={() => navigator.clipboard?.writeText(c.referenceNo).then(() => flash('Reference copied'))}
            title="Copy reference"
          >
            {c.referenceNo} <Copy className="h-3.5 w-3.5" />
          </button>
          <span className="text-ink-300">•</span>
          <span className="inline-flex items-center gap-1 text-ink-600"><CategoryIcon code={c.categoryCode} /> {c.categoryName}</span>
          <span className="text-ink-300">•</span>
          <span className="inline-flex items-center gap-1 text-ink-600">
            {c.isPublic ? <><Globe2 className="h-3.5 w-3.5" /> Public</> : <><Lock className="h-3.5 w-3.5" /> Private</>}
          </span>
        </div>
        <div className="mt-2 flex flex-wrap items-start justify-between gap-3">
          <h1 className="text-xl font-bold text-ink-900 sm:text-2xl">{c.title}</h1>
          <div className="flex items-center gap-2">
            <PriorityBadge priority={c.priority} />
            <StatusBadge status={c.status} size="md" />
          </div>
        </div>
        <p className="mt-1 flex items-center gap-1 text-sm text-ink-500"><MapPin className="h-4 w-4" /> {c.address}</p>
        <div className="mt-5"><StatusTracker status={c.status} /></div>

        {(c.canParticipate || owner) && (
          <div className="mt-5 flex flex-wrap gap-2 border-t border-ink-100 pt-4">
            {c.canParticipate && (
              <>
                <button className={c.upvoted ? 'btn-primary' : 'btn-secondary'} onClick={() => toggle('upvote')} disabled={busy} aria-pressed={c.upvoted}>
                  <ArrowBigUp className="h-4 w-4" /> {c.upvoted ? 'Upvoted' : 'Upvote'} · {c.upvoteCount}
                </button>
                <button className="btn-secondary" onClick={() => toggle('follow')} disabled={busy} aria-pressed={c.followed}>
                  {c.followed ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />} {c.followed ? 'Unfollow' : 'Follow'} · {c.followerCount}
                </button>
              </>
            )}
            {owner && c.canRespond && (
              <button className="btn-secondary" onClick={() => { setModalError(''); setRespondOpen(true); }}>
                <MessageSquarePlus className="h-4 w-4" /> Add information or photos
              </button>
            )}
            {(c.canParticipate || owner) && !c.duplicateOfReference && (
              <button className="btn-ghost" onClick={() => { setModalError(''); setFlagRef(''); setFlagReason(''); setFlagOpen(true); }}>
                <Flag className="h-4 w-4" /> Flag as possible duplicate
              </button>
            )}
          </div>
        )}
      </header>

      {c.status === 'REJECTED' && c.rejectionReason && <Alert kind="error" title="Rejected by the municipality">{c.rejectionReason}</Alert>}
      {c.duplicateOfReference && (
        <Alert kind="info" title="Linked to an existing complaint">
          Officials confirmed this describes the same issue as{' '}
          <Link className="font-semibold underline" to={`/complaints/${c.duplicateOfId}`}>{c.duplicateOfReference}</Link>. Progress is tracked there.
        </Alert>
      )}
      {c.wardMatch === 'PENDING_VERIFICATION' && (
        <Alert kind="warning" title="Ward being verified">
          The location did not match a configured ward boundary, so the municipal control room is confirming which ward should handle it.
        </Alert>
      )}

      <div className="grid gap-6 lg:grid-cols-3 [&>*]:min-w-0">
        <div className="space-y-6 lg:col-span-2">
          {official && (
            <section className="card border-brand-200 p-5">
              <h2 className="mb-3 font-semibold text-ink-900">Official actions</h2>
              <OfficialActions complaint={c} onUpdated={(u) => { setC(u); flash('Complaint updated'); }} />
            </section>
          )}
          <section className="card p-5">
            <h2 className="mb-2 font-semibold text-ink-900">Description</h2>
            <p className="whitespace-pre-line text-ink-700">{c.description}</p>
          </section>
          <section className="card p-5">
            <h2 className="mb-3 font-semibold text-ink-900">Citizen evidence <span className="font-normal text-ink-500">({c.citizenEvidence.length})</span></h2>
            <EvidenceGallery items={c.citizenEvidence} emptyText="No photos were attached to this complaint." />
          </section>
          {(c.resolution || c.resolutionEvidence.length > 0) && (
            <section className="card border-brand-200 p-5">
              <h2 className="mb-2 font-semibold text-brand-800">Resolution</h2>
              {c.resolution && (
                <>
                  <p className="whitespace-pre-line text-ink-700">{c.resolution.summary}</p>
                  <p className="mt-1 text-xs text-ink-500">
                    {formatDateTime(c.resolution.resolvedAt)} · {c.resolution.resolvedByName ? `${c.resolution.resolvedByName} (${c.resolution.resolvedByRole})` : c.resolution.resolvedByRole}
                  </p>
                </>
              )}
              <h3 className="mb-2 mt-4 text-sm font-semibold text-ink-800">Resolution photos</h3>
              <EvidenceGallery items={c.resolutionEvidence} emptyText="No resolution photos were provided." />
            </section>
          )}
          {(owner || official) && c.status === 'RESOLVED' && (c.canGiveFeedback || c.feedback) && (
            <section className="card p-5">
              <h2 className="mb-3 font-semibold text-ink-900">{owner ? 'Rate the resolution' : 'Citizen feedback'}</h2>
              {owner ? <FeedbackForm complaint={c} onSaved={load} /> : c.feedback ? <FeedbackForm complaint={c} onSaved={load} /> : null}
            </section>
          )}
          <section className="card p-5">
            <h2 className="mb-4 font-semibold text-ink-900">Status history</h2>
            <Timeline entries={c.timeline} />
          </section>
        </div>

        <aside className="space-y-6">
          <section className="card overflow-hidden">
            <MapContainer center={[c.latitude, c.longitude]} zoom={16} className="h-56 w-full" scrollWheelZoom={false}>
              <TileLayer url={OSM_TILES} attribution={OSM_ATTRIBUTION} />
              <Marker position={[c.latitude, c.longitude]} icon={pinIcon('#12603e', 22)} />
            </MapContainer>
            <div className="p-4 text-sm">
              <p className="font-medium text-ink-900">{c.locality}</p>
              <p className="text-ink-500">{c.latitude.toFixed(5)}, {c.longitude.toFixed(5)}</p>
            </div>
          </section>
          <section className="card px-5 py-3">
            <dl className="divide-y divide-ink-100">
              <Detail label="Submitted">{formatDateTime(c.createdAt)}</Detail>
              <Detail label="Last updated">{formatDateTime(c.updatedAt)}</Detail>
              {c.resolvedAt && <Detail label="Resolved">{formatDateTime(c.resolvedAt)}</Detail>}
              <Detail label="Ward">{c.wardName ?? (c.wardMatch === 'PENDING_VERIFICATION' ? 'Being verified' : '—')}</Detail>
              <Detail label="Department">{c.departmentName ?? 'Not yet assigned'}</Detail>
              {c.assignedOfficerName && <Detail label="Field officer">{c.assignedOfficerName}</Detail>}
              <Detail label="Upvotes">{c.upvoteCount}</Detail>
              <Detail label="Followers">{c.followerCount}</Detail>
            </dl>
          </section>
          {official && c.reporter && (
            <section className="card p-5 text-sm">
              <h2 className="mb-2 font-semibold text-ink-900">Reporter</h2>
              <p className="font-medium text-ink-900">{c.reporter.fullName}</p>
              <p className="text-ink-600">{c.reporter.email}</p>
              {c.reporter.phone && <p className="text-ink-600">{c.reporter.phone}</p>}
              <p className="mt-2 text-xs text-ink-500">Visible to officials handling this complaint only.</p>
            </section>
          )}
          {official && c.assignments && c.assignments.length > 0 && (
            <section className="card p-5 text-sm">
              <h2 className="mb-2 font-semibold text-ink-900">Assignment history</h2>
              <ul className="space-y-2">
                {c.assignments.map((a, i) => (
                  <li key={i} className={a.active ? '' : 'text-ink-400'}>
                    <span className="font-medium">{a.type.charAt(0) + a.type.slice(1).toLowerCase()}:</span> {a.target}
                    {a.active ? <span className="chip ml-1 bg-brand-50 text-brand-800">current</span> : null}
                    <span className="block text-xs">by {a.assignedByName} · {formatDateTime(a.createdAt)}</span>
                  </li>
                ))}
              </ul>
            </section>
          )}
          {!isOfficial && !owner && (
            <p className="text-xs text-ink-500">Reporter details are never shown publicly.</p>
          )}
        </aside>
      </div>

      <Modal
        open={respondOpen}
        title="Add information"
        onClose={() => !busy && setRespondOpen(false)}
        footer={
          <>
            <button className="btn-secondary" onClick={() => setRespondOpen(false)} disabled={busy}>Cancel</button>
            <button className="btn-primary" onClick={respond} disabled={busy || (respondText.trim().length < 3 && respondPhotos.length === 0)}>
              {busy && <Spinner className="h-4 w-4 text-white" />} Send
            </button>
          </>
        }
      >
        <div className="space-y-4">
          {modalError && <Alert kind="error">{modalError}</Alert>}
          <div>
            <label className="label" htmlFor="respond">Message</label>
            <textarea id="respond" rows={4} maxLength={2000} className="input" value={respondText} onChange={(e) => setRespondText(e.target.value)} placeholder="Answer the officials' question or add details" />
          </div>
          <PhotoPicker files={respondPhotos} onChange={setRespondPhotos} disabled={busy} label="Additional photos (optional)" />
          {respondProgress != null && <div className="h-2 overflow-hidden rounded-full bg-ink-100"><div className="h-full bg-brand-600" style={{ width: `${respondProgress}%` }} /></div>}
        </div>
      </Modal>

      <Modal
        open={flagOpen}
        title="Flag a possible duplicate"
        onClose={() => !busy && setFlagOpen(false)}
        footer={
          <>
            <button className="btn-secondary" onClick={() => setFlagOpen(false)} disabled={busy}>Cancel</button>
            <button className="btn-primary" onClick={flag} disabled={busy || flagRef.trim().length < 5}>
              {busy && <Spinner className="h-4 w-4 text-white" />} Submit flag
            </button>
          </>
        }
      >
        <div className="space-y-4">
          <p className="text-sm text-ink-600">If this complaint describes the same issue as an earlier one, enter that complaint&apos;s reference. An official will review and decide.</p>
          {modalError && <Alert kind="error">{modalError}</Alert>}
          <div>
            <label className="label" htmlFor="ref">Reference of the original complaint</label>
            <input id="ref" className="input font-mono" placeholder="NS-YYMMDD-000000" value={flagRef} onChange={(e) => setFlagRef(e.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="reason">Why do you think so? (optional)</label>
            <textarea id="reason" rows={3} maxLength={500} className="input" value={flagReason} onChange={(e) => setFlagReason(e.target.value)} />
          </div>
        </div>
      </Modal>
    </div>
  );
}
