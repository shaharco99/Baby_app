import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

/** Standard page wrapper — keeps vertical rhythm identical across every screen. */
export function PageContainer({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn('space-y-4', className)}>{children}</div>
}
