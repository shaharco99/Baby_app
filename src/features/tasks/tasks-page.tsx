import { useMemo, useState } from 'react'
import { ListChecks, Plus, Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Progress } from '@/components/ui/progress'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { PageContainer } from '@/components/layout/page-container'
import { PageHeader } from '@/components/layout/page-header'
import { EmptyState } from '@/components/shared/empty-state'
import { useAppStore } from '@/stores/appStore'
import { TaskCard } from '@/features/tasks/task-card'
import { TaskForm } from '@/features/tasks/task-form'
import { HOSPITAL_BAG_PRESET } from '@/lib/hospital-bag-preset'
import { TASK_CATEGORIES, type TaskItem } from '@/types/models'

export default function TasksPage() {
  const tasks = useAppStore((s) => s.tasks)
  const addTask = useAppStore((s) => s.addTask)
  const updateTask = useAppStore((s) => s.updateTask)
  const removeTask = useAppStore((s) => s.removeTask)
  const filter = useAppStore((s) => s.taskFilter)
  const setFilter = useAppStore((s) => s.setTaskFilter)

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<TaskItem | undefined>(undefined)

  const filtered = useMemo(
    () => (filter === 'all' ? tasks : tasks.filter((t) => t.category === filter)),
    [tasks, filter],
  )

  const sorted = useMemo(
    () => [...filtered].sort((a, b) => Number(a.done) - Number(b.done) || a.createdAt - b.createdAt),
    [filtered],
  )

  const doneCount = tasks.filter((t) => t.done).length
  const donePercent = tasks.length === 0 ? 0 : Math.round((doneCount / tasks.length) * 100)

  const hospitalBagAlreadyAdded = tasks
    .filter((t) => t.category === 'תיק ליולדת')
    .map((t) => t.title)

  function openNew() {
    setEditing(undefined)
    setFormOpen(true)
  }

  function openEdit(task: TaskItem) {
    setEditing(task)
    setFormOpen(true)
  }

  function addHospitalBagPreset() {
    for (const title of HOSPITAL_BAG_PRESET) {
      if (!hospitalBagAlreadyAdded.includes(title)) {
        addTask({
          title,
          category: 'תיק ליולדת',
          priority: 'normal',
          done: false,
        })
      }
    }
    setFilter('תיק ליולדת')
  }

  const newTaskButton = (
    <Button onClick={openNew} size="sm">
      <Plus className="size-4" />
      משימה חדשה
    </Button>
  )

  return (
    <PageContainer>
      <PageHeader title="משימות" action={<div className="hidden sm:block">{newTaskButton}</div>} />

      {tasks.length > 0 && (
        <div className="space-y-1.5">
          <div className="flex items-center justify-between text-xs text-muted-foreground">
            <span>
              {doneCount} מתוך {tasks.length} בוצעו
            </span>
            <span className="tabular-nums">{donePercent}%</span>
          </div>
          <Progress value={donePercent} />
        </div>
      )}

      <Tabs value={filter} onValueChange={(v) => setFilter(v as typeof filter)}>
        <TabsList className="flex w-full flex-nowrap justify-start gap-1 overflow-x-auto bg-transparent p-0 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          <TabsTrigger
            value="all"
            className="shrink-0 rounded-full border border-border data-[state=active]:border-primary"
          >
            הכל
          </TabsTrigger>
          {TASK_CATEGORIES.map((c) => (
            <TabsTrigger
              key={c}
              value={c}
              className="shrink-0 rounded-full border border-border data-[state=active]:border-primary"
            >
              {c}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {filter === 'תיק ליולדת' && (
        <button
          type="button"
          onClick={addHospitalBagPreset}
          className="flex w-full items-center justify-center gap-2 rounded-2xl border border-dashed border-primary/50 bg-primary/5 py-3 text-sm text-primary"
        >
          <Sparkles className="size-4" />
          הוספת רשימת פריטים מומלצת לתיק ליולדת
        </button>
      )}

      {tasks.length === 0 ? (
        <EmptyState
          icon={ListChecks}
          title="עוד לא הוספתם משימות"
          description="אפשר להתחיל מרשימה מוכנה או להוסיף משימה משלכם."
          action={<div className="sm:hidden">{newTaskButton}</div>}
        />
      ) : sorted.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">אין משימות בסינון הזה.</p>
      ) : (
        <div className="space-y-2">
          {sorted.map((task) => (
            <TaskCard
              key={task.id}
              task={task}
              onToggleDone={(done) => updateTask(task.id, { done })}
              onClick={() => openEdit(task)}
            />
          ))}
        </div>
      )}

      {tasks.length > 0 && (
        <Button
          onClick={openNew}
          size="icon"
          className="fixed end-4 z-40 size-14 rounded-full shadow-lg sm:hidden"
          style={{ bottom: 'calc(5.5rem + env(safe-area-inset-bottom))' }}
          aria-label="הוספת משימה"
        >
          <Plus className="size-6" />
        </Button>
      )}

      <TaskForm
        open={formOpen}
        onOpenChange={setFormOpen}
        initial={editing}
        defaultCategory={filter !== 'all' ? filter : undefined}
        onSubmit={(value) => {
          if (editing) {
            updateTask(editing.id, value)
          } else {
            addTask(value)
          }
        }}
        onDelete={
          editing
            ? () => {
                removeTask(editing.id)
                setFormOpen(false)
              }
            : undefined
        }
      />
    </PageContainer>
  )
}
