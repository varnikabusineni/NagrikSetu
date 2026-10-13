import { useCallback, useEffect, useState } from 'react';
import { errorMessage } from '../api/client';
import { complaintApi } from '../api/endpoints';
import type { ComplaintSummary, Page } from '../api/types';
import type { Filters } from '../components/FilterBar';

export function useFeed(filters: Filters, mine: boolean, page: number) {
  const [data, setData] = useState<Page<ComplaintSummary> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setLoading(true);
    setError('');
    complaintApi
      .feed({
        q: filters.q || undefined,
        category: filters.category || undefined,
        status: filters.status ? [filters.status] : undefined,
        locality: filters.locality || undefined,
        from: filters.from || undefined,
        to: filters.to || undefined,
        sort: filters.sort,
        mine,
        page,
        size: 12,
      })
      .then(setData)
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setLoading(false));
  }, [filters, mine, page]);

  useEffect(load, [load]);
  return { data, loading, error, reload: load };
}
