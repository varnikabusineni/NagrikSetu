import { Building2, CircleDot, Droplets, HelpCircle, Lightbulb, Trash2, Waves, Wrench, type LucideIcon } from 'lucide-react';

const ICONS: Record<string, LucideIcon> = {
  POTHOLE: CircleDot,
  GARBAGE: Trash2,
  DRAINAGE: Waves,
  WATER_LEAKAGE: Droplets,
  WATER_SUPPLY: Droplets,
  STREETLIGHT: Lightbulb,
  INFRASTRUCTURE: Building2,
  OTHER: HelpCircle,
};

export function CategoryIcon({ code, className = 'h-4 w-4' }: { code: string; className?: string }) {
  const Icon = ICONS[code] ?? Wrench;
  return <Icon className={className} aria-hidden="true" />;
}
