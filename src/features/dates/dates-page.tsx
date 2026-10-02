import { useMemo, useState } from 'react'
import { CalendarHeart, Heart, Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { PageContainer } from '@/components/layout/page-container'
import { PageHeader } from '@/components/layout/page-header'
import { EmptyState } from '@/components/shared/empty-state'
import { useAppStore } from '@/stores/appStore'
import { DateForm } from '@/features/dates/date-form'
import { formatHebrewDate, isPastDate } from '@/lib/pregnancy'
import type { ImportantDate } from '@/types/models'
import { cn } from '@/lib/utils'

export default function DatesPage() {
  const dates = useAppStore((s) => s.importantDates)
  const addDate = useAppStore((s) => s.addImportantDate)
  const updateDate = useAppStore((s) => s.updateImportantDate)
  const removeDate = useAppStore((s) => s.removeImportantDate)

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<ImportantDate | undefined>(undefined)

  const sorted = useMemo(() => [...dates].sort((a, b) => a.date.localeCompare(b.date)), [dates])

  function openNew() {
    setEditing(undefined)
    setFormOpen(true)
  }

  function openEdit(date: ImportantDate) {
    setEditing(date)
    setFormOpen(true)
  }

  const newDateButton = (
    <Button onClick={openNew} size="sm">
      <Plus className="size-4" />
      תאריך חדש
    </Button>
  )

  return (
    <PageContainer>
      <PageHeader title="תאריכים ומשאלות" action={<div className="hidden sm:block">{newDateButton}</div>} />

      {sorted.length === 0 ? (
        <EmptyState
          icon={CalendarHeart}
          title="עוד לא הוספתם תאריכים חשובים"
          description="אפשר להוסיף את התאריך המשוער, מסיבת קבלת פנים, ועוד."
          action={<div className="sm:hidden">{newDateButton}</div>}
        />
      ) : (
        <div className="space-y-2">
          {sorted.map((d) => {
            const past = isPastDate(d.date)
            return (
              <Card
                key={d.id}
                className={cn('cursor-pointer transition-colors', past && 'opacity-60')}
                onClick={() => openEdit(d)}
              >
                <CardContent className="flex items-start gap-3 py-3">
                  <Heart className={cn('mt-0.5 size-4 shrink-0', past ? 'text-muted-foreground' : 'text-blush')} />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <p className="font-medium text-foreground">{d.title}</p>
                      <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
                        {formatHebrewDate(d.date)}
                      </span>
                    </div>
                    {d.wish && <p className="mt-1 text-sm text-muted-foreground">{d.wish}</p>}
                  </div>
                </CardContent>
              </Card>
            )
          })}
        </div>
      )}

      {sorted.length > 0 && (
        <Button
          onClick={openNew}
          size="icon"
          className="fixed end-4 z-40 size-14 rounded-full shadow-lg sm:hidden"
          style={{ bottom: 'calc(5.5rem + env(safe-area-inset-bottom))' }}
          aria-label="הוספת תאריך"
        >
          <Plus className="size-6" />
        </Button>
      )}

      <DateForm
        open={formOpen}
        onOpenChange={setFormOpen}
        initial={editing}
        onSubmit={(value) => {
          if (editing) {
            updateDate(editing.id, value)
          } else {
            addDate(value)
          }
        }}
        onDelete={
          editing
            ? () => {
                removeDate(editing.id)
                setFormOpen(false)
              }
            : undefined
        }
      />
    </PageContainer>
  )
}
