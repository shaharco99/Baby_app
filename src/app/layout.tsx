import { NavLink, Outlet, useLocation } from 'react-router'
import { cn } from '@/lib/utils'
import { NAV_ITEMS } from '@/components/layout/nav-items'
import { useVisualViewportHeight } from '@/lib/use-visual-viewport'

export function RootLayout() {
  useVisualViewportHeight()
  const { pathname } = useLocation()

  // On mobile the app bar carries the section name (pages hide their own h1),
  // so there is only ever one heading on screen. Desktop keeps the brand wordmark.
  const sectionLabel =
    NAV_ITEMS.find((item) => (item.to === '/' ? pathname === '/' : pathname.startsWith(item.to)))
      ?.label ?? 'אור ירח'

  return (
    <div className="mx-auto flex min-h-dvh max-w-3xl flex-col bg-background">
      <header className="sticky top-0 z-40 border-b border-border bg-background/90 backdrop-blur">
        <div className="flex items-center justify-between px-4 py-3">
          <span className="hidden font-heading text-xl text-foreground sm:block">אור ירח</span>
          <span className="font-heading text-xl text-foreground sm:hidden">{sectionLabel}</span>
          <nav className="hidden gap-1 sm:flex">
            {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                end={to === '/'}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm transition-colors',
                    isActive
                      ? 'bg-primary text-primary-foreground'
                      : 'text-muted-foreground hover:bg-muted hover:text-foreground',
                  )
                }
              >
                <Icon className="size-4" aria-hidden />
                {label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>

      <main className="flex-1 px-4 pb-24 pt-4 sm:pb-8">
        <Outlet />
      </main>

      <nav
        className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-card sm:hidden"
        style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
        aria-label="ניווט ראשי"
      >
        <div className="mx-auto flex max-w-3xl justify-around">
          {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={({ isActive }) =>
                cn(
                  'flex min-w-[4.25rem] flex-col items-center gap-1 py-2.5 text-xs',
                  isActive ? 'text-primary' : 'text-muted-foreground',
                )
              }
            >
              <Icon className="size-5" aria-hidden />
              {label}
            </NavLink>
          ))}
        </div>
      </nav>
    </div>
  )
}
