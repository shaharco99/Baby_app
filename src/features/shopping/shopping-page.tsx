import { useMemo, useState } from 'react'
import { Plus, ShoppingBag } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { PageContainer } from '@/components/layout/page-container'
import { PageHeader } from '@/components/layout/page-header'
import { EmptyState } from '@/components/shared/empty-state'
import { useAppStore } from '@/stores/appStore'
import { ShoppingItemCard } from '@/features/shopping/shopping-item-card'
import { ShoppingItemForm } from '@/features/shopping/shopping-item-form'
import { BudgetSummaryCard } from '@/features/shopping/budget-summary-card'
import type { ShoppingItem } from '@/types/models'

export default function ShoppingPage() {
  const items = useAppStore((s) => s.shoppingItems)
  const addItem = useAppStore((s) => s.addShoppingItem)
  const updateItem = useAppStore((s) => s.updateShoppingItem)
  const removeItem = useAppStore((s) => s.removeShoppingItem)
  const filter = useAppStore((s) => s.shoppingFilter)
  const setFilter = useAppStore((s) => s.setShoppingFilter)

  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<ShoppingItem | undefined>(undefined)

  const filtered = useMemo(
    () => (filter === 'all' ? items : items.filter((i) => i.status === filter)),
    [items, filter],
  )

  function openNew() {
    setEditing(undefined)
    setFormOpen(true)
  }

  function openEdit(item: ShoppingItem) {
    setEditing(item)
    setFormOpen(true)
  }

  const newItemButton = (
    <Button onClick={openNew} size="sm">
      <Plus className="size-4" />
      פריט חדש
    </Button>
  )

  return (
    <PageContainer>
      <PageHeader title="רשימת קניות" action={<div className="hidden sm:block">{newItemButton}</div>} />

      <BudgetSummaryCard items={items} />

      <Tabs value={filter} onValueChange={(v) => setFilter(v as typeof filter)}>
        <TabsList className="w-full">
          <TabsTrigger value="all">הכל</TabsTrigger>
          <TabsTrigger value="need">צריך</TabsTrigger>
          <TabsTrigger value="ordered">הוזמן</TabsTrigger>
          <TabsTrigger value="bought">נקנה</TabsTrigger>
        </TabsList>
      </Tabs>

      {items.length === 0 ? (
        <EmptyState
          icon={ShoppingBag}
          title="עוד לא הוספתם פריטים"
          description="אפשר להתחיל להוסיף פריטים לרשימה."
          action={<div className="sm:hidden">{newItemButton}</div>}
        />
      ) : filtered.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">אין פריטים בסינון הזה.</p>
      ) : (
        <div className="space-y-2">
          {filtered.map((item) => (
            <ShoppingItemCard
              key={item.id}
              item={item}
              onToggleBought={(bought) => updateItem(item.id, { status: bought ? 'bought' : 'need' })}
              onClick={() => openEdit(item)}
            />
          ))}
        </div>
      )}

      {items.length > 0 && (
        <Button
          onClick={openNew}
          size="icon"
          className="fixed end-4 z-40 size-14 rounded-full shadow-lg sm:hidden"
          style={{ bottom: 'calc(5.5rem + env(safe-area-inset-bottom))' }}
          aria-label="הוספת פריט"
        >
          <Plus className="size-6" />
        </Button>
      )}

      <ShoppingItemForm
        open={formOpen}
        onOpenChange={setFormOpen}
        initial={editing}
        onSubmit={(value) => {
          if (editing) {
            updateItem(editing.id, value)
          } else {
            addItem(value)
          }
        }}
        onDelete={
          editing
            ? () => {
                removeItem(editing.id)
                setFormOpen(false)
              }
            : undefined
        }
      />
    </PageContainer>
  )
}
