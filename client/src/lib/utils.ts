import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Merge Tailwind class names, de-duplicating conflicting utilities.
 * Used by shadcn/ui components and the migrated Custom* atom layer.
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
