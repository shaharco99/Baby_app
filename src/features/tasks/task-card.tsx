import { CalendarDays, User } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { PriorityBadge } from '@/components/shared/priority-badge'
import { MetaChip } from '@/components/shared/meta-chip'
import { formatHebrewDate, isPastDate } from '@/lib/pregnancy'
import type { TaskItem } from '@/types/models'
import { cn } from '@/lib/utils'

export function TaskCard({
  task,
  onToggleDone,
  onClick,
}: {
  task: TaskItem
  onToggleDone: (done: boolean) => void
  onClick: () => void
}) {
  const overdue = task.dueDate && !task.done && isPastDate(task.dueDate)

  return (
    <Card
      className={cn(
        'cursor-pointer border-s-4 transition-colors',
        task.priority === 'high' && 'border-s-blush',
        task.priority === 'normal' && 'border-s-moss',
        task.priority === 'low' && 'border-s-border',
      )}
      onClick={onClick}
    >
      <CardContent className="flex items-center gap-3 py-3">
        <Checkbox
          checked={task.done}
          onCheckedChange={(v) => onToggleDone(v === true)}
          onClick={(e) => e.stopPropagation()}
          aria-label={`סימון ${task.title} כבוצע`}
        />
        <div className="min-w-0 flex-1">
          <p className={cn('font-medium text-foreground', task.done && 'line-through opacity-60')}>
            {task.title}
          </p>
          <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
            <MetaChip>{task.category}</MetaChip>
            <PriorityBadge priority={task.priority} />
            {task.dueDate && (
              <MetaChip className={cn(overdue && 'border-destructive/40 text-destructive')}>
                <CalendarDays className="size-3" aria-hidden />
                {formatHebrewDate(task.dueDate)}
              </MetaChip>
            )}
            {task.assignee && (
              <MetaChip>
                <User className="size-3" aria-hidden />
                {task.assignee}
              </MetaChip>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
