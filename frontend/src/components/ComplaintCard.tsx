import { ArrowBigUp, Camera, Eye, MapPin } from 'lucide-react';
import { Link } from 'react-router-dom';
import { mediaUrl } from '../api/client';
import type { ComplaintSummary } from '../api/types';
import { timeAgo } from '../lib/format';
import { CategoryIcon } from './CategoryIcon';
import { PriorityBadge, StatusBadge } from './StatusBadge';

export function ComplaintCard({ complaint: c, to }: { complaint: ComplaintSummary; to?: string }) {
  return (
    <Link
      to={to ?? `/complaints/${c.id}`}
      className="card group flex flex-col overflow-hidden transition-shadow hover:shadow-raised focus-visible:ring-2"
    >
      <div className="relative h-40 bg-ink-100">
        {c.thumbnailUrl ? (
          <img src={mediaUrl(c.thumbnailUrl)} alt="" loading="lazy" className="h-full w-full object-cover" />
        ) : (
          <div className="flex h-full items-center justify-center text-ink-400">
            <CategoryIcon code={c.categoryCode} className="h-10 w-10" />
          </div>
        )}
        <div className="absolute left-3 top-3">
          <StatusBadge status={c.status} />
        </div>
        {c.photoCount > 1 && (
          <span className="absolute bottom-2 right-2 inline-flex items-center gap-1 rounded-md bg-black/60 px-1.5 py-0.5 text-xs text-white">
            <Camera className="h-3 w-3" /> {c.photoCount}
          </span>
        )}
      </div>
      <div className="flex flex-1 flex-col p-4">
        <div className="mb-1 flex items-center gap-1.5 text-xs font-medium text-brand-700">
          <CategoryIcon code={c.categoryCode} className="h-3.5 w-3.5" />
          {c.categoryName}
        </div>
        <h3 className="line-clamp-2 font-semibold text-ink-900 group-hover:text-brand-800">{c.title}</h3>
        <p className="mt-1 flex items-center gap-1 text-sm text-ink-500">
          <MapPin className="h-3.5 w-3.5 shrink-0" />
          <span className="truncate">{c.locality}</span>
        </p>
        <div className="mt-2 flex flex-wrap gap-1.5">
          {c.mine && <span className="chip bg-brand-50 text-brand-800">Your report</span>}
          <PriorityBadge priority={c.priority} />
          {c.duplicateOfReference && <span className="chip bg-ink-100 text-ink-700">Duplicate of {c.duplicateOfReference}</span>}
          {c.wardVerificationPending && <span className="chip bg-amber-50 text-amber-800">Ward being verified</span>}
        </div>
        <div className="mt-auto flex items-center justify-between pt-3 text-xs text-ink-500">
          <span className="font-mono">{c.referenceNo}</span>
          <span className="flex items-center gap-3">
            <span className="inline-flex items-center gap-0.5" title="Upvotes">
              <ArrowBigUp className={`h-4 w-4 ${c.upvoted ? 'fill-brand-600 text-brand-600' : ''}`} /> {c.upvoteCount}
            </span>
            <span className="inline-flex items-center gap-1" title="Followers">
              <Eye className="h-3.5 w-3.5" /> {c.followerCount}
            </span>
            <span>{timeAgo(c.updatedAt)}</span>
          </span>
        </div>
      </div>
    </Link>
  );
}
