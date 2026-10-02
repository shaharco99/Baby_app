import { Card, CardContent } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { PriorityBadge } from '@/components/shared/priority-badge'
import { MetaChip } from '@/components/shared/meta-chip'
import { formatIls, itemEffectivePrice } from '@/features/shopping/budget'
import { SHOPPING_STATUS_LABEL, type ShoppingItem } from '@/types/models'
import { cn } from '@/lib/utils'

export function ShoppingItemCard({
  item,
  onToggleBought,
  onClick,
}: {
  item: ShoppingItem
  onToggleBought: (bought: boolean) => void
  onClick: () => void
}) {
  const price = itemEffectivePrice(item)
  const bought = item.status === 'bought'

  return (
    <Card
      className={cn(
        'cursor-pointer border-s-4 transition-colors',
        item.priority === 'high' && 'border-s-blush',
        item.priority === 'normal' && 'border-s-moss',
        item.priority === 'low' && 'border-s-border',
        bought && 'bg-muted/40',
      )}
      onClick={onClick}
    >
      <CardContent className="flex items-center gap-3 py-3">
        <Checkbox
          checked={bought}
          onCheckedChange={(v) => onToggleBought(v === true)}
          onClick={(e) => e.stopPropagation()}
          aria-label={`סימון ${item.name} כנקנה`}
        />
        <div className="min-w-0 flex-1">
          <div className="flex items-center justify-between gap-2">
            <p className={cn('truncate font-medium text-foreground', bought && 'line-through opacity-60')}>
              {item.name}
            </p>
            {price != null && (
              <span className="shrink-0 tabular-nums text-sm text-foreground">{formatIls(price)}</span>
            )}
          </div>
          <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
            <MetaChip>{item.category}</MetaChip>
            <PriorityBadge priority={item.priority} />
            {!bought && <MetaChip>{SHOPPING_STATUS_LABEL[item.status]}</MetaChip>}
            {item.assignee && <MetaChip>{item.assignee}</MetaChip>}
            {item.alternatives.length > 0 && (
              <MetaChip className="border-primary/40 text-primary">
                {item.alternatives.length} אפשרויות בבדיקה
              </MetaChip>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
