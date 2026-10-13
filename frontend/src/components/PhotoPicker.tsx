import { Camera, ImagePlus, Trash2 } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { formatBytes } from '../lib/format';
import { ACCEPT_ATTRIBUTE, acceptPhotos, MAX_PHOTOS } from '../lib/files';

/** Select, preview and remove photos before upload. Files are validated here and again on the server. */
export function PhotoPicker({
  files,
  onChange,
  disabled = false,
  max = MAX_PHOTOS,
  label = 'Photos',
}: {
  files: File[];
  onChange: (files: File[]) => void;
  disabled?: boolean;
  max?: number;
  label?: string;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [errors, setErrors] = useState<string[]>([]);
  const [dragging, setDragging] = useState(false);
  const previews = useMemo(() => files.map((f) => URL.createObjectURL(f)), [files]);

  useEffect(() => () => previews.forEach((url) => URL.revokeObjectURL(url)), [previews]);

  const add = (incoming: FileList | null) => {
    if (!incoming) return;
    const { accepted, errors: errs } = acceptPhotos(files.length, Array.from(incoming));
    setErrors(errs);
    if (accepted.length) onChange([...files, ...accepted].slice(0, max));
    if (input.current) input.current.value = '';
  };

  return (
    <div>
      <div className="mb-1 flex items-baseline justify-between">
        <span className="label mb-0">{label}</span>
        <span className="text-xs text-ink-500">
          {files.length}/{max} · JPEG, PNG or WebP · up to 8 MB each
        </span>
      </div>
      <div
        onDragOver={(e) => {
          e.preventDefault();
          if (!disabled) setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault();
          setDragging(false);
          if (!disabled) add(e.dataTransfer.files);
        }}
        className={`rounded-xl border-2 border-dashed p-3 transition-colors ${dragging ? 'border-brand-500 bg-brand-50' : 'border-ink-300 bg-ink-50/60'}`}
      >
        <ul className="grid grid-cols-3 gap-3 sm:grid-cols-4">
          {files.map((f, i) => (
            <li key={`${f.name}-${f.size}-${f.lastModified}-${i}`} className="group relative aspect-square overflow-hidden rounded-lg bg-white ring-1 ring-ink-200">
              <img src={previews[i]} alt={`Selected photo ${i + 1}: ${f.name}`} className="h-full w-full object-cover" />
              <div className="absolute inset-x-0 bottom-0 truncate bg-black/55 px-1.5 py-0.5 text-[10px] text-white">{formatBytes(f.size)}</div>
              {!disabled && (
                <button
                  type="button"
                  onClick={() => onChange(files.filter((_, j) => j !== i))}
                  className="absolute right-1 top-1 rounded-full bg-white/95 p-1.5 text-rose-700 shadow hover:bg-white"
                  aria-label={`Remove ${f.name}`}
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              )}
            </li>
          ))}
          {files.length < max && (
            <li className="aspect-square">
              <button
                type="button"
                disabled={disabled}
                onClick={() => input.current?.click()}
                className="flex h-full w-full flex-col items-center justify-center gap-1 rounded-lg border border-ink-300 bg-white text-sm text-ink-600 hover:border-brand-500 hover:text-brand-700 disabled:opacity-60"
              >
                {files.length === 0 ? <Camera className="h-6 w-6" /> : <ImagePlus className="h-6 w-6" />}
                <span className="text-xs font-medium">{files.length === 0 ? 'Add photos' : 'Add more'}</span>
              </button>
            </li>
          )}
        </ul>
        <p className="mt-2 text-xs text-ink-500">Drag photos here, or tap to choose. On a phone you can take a photo directly.</p>
      </div>
      <input
        ref={input}
        type="file"
        accept={ACCEPT_ATTRIBUTE}
        multiple
        className="sr-only"
        onChange={(e) => add(e.target.files)}
        disabled={disabled}
        aria-label="Choose photos"
      />
      {errors.length > 0 && (
        <ul className="mt-2 space-y-0.5">
          {errors.map((e, i) => (
            <li key={i} className="field-error">
              {e}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
