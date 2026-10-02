import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

/** Small pill for secondary card metadata (category, date, assignee). */
export function MetaChip({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full border border-border px-2 py-0.5 text-xs text-muted-foreground',
        className,
      )}
    >
      {children}
    </span>
  )
}
