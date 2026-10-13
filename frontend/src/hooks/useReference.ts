import { useEffect, useState } from 'react';
import { referenceApi } from '../api/endpoints';
import type { Category, Department, Ward } from '../api/types';

let categoriesCache: Promise<Category[]> | null = null;
let departmentsCache: Promise<Department[]> | null = null;
let wardsCache: Promise<Ward[]> | null = null;

function useCached<T>(get: () => Promise<T[]>): T[] {
  const [data, setData] = useState<T[]>([]);
  useEffect(() => {
    let alive = true;
    get()
      .then((d) => alive && setData(d))
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, [get]);
  return data;
}

const getCategories = () => (categoriesCache ??= referenceApi.categories().catch((e) => ((categoriesCache = null), Promise.reject(e))));
const getDepartments = () => (departmentsCache ??= referenceApi.departments().catch((e) => ((departmentsCache = null), Promise.reject(e))));
const getWards = () => (wardsCache ??= referenceApi.wards().catch((e) => ((wardsCache = null), Promise.reject(e))));

export const useCategories = () => useCached(getCategories);
export const useDepartments = () => useCached(getDepartments);
export const useWards = () => useCached(getWards);
