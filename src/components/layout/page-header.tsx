import type { ReactNode } from 'react'

/**
 * Page title row. The title is hidden on mobile because the sticky app bar already
 * shows the current section name, so the page never renders two competing headings.
 */
export function PageHeader({ title, action }: { title: string; action?: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-2">
      <h1 className="hidden font-heading text-2xl text-foreground sm:block">{title}</h1>
      {action}
    </div>
  )
}
