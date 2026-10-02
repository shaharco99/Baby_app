export type Priority = 'low' | 'normal' | 'high'

export type Assignee = 'שחר' | 'טופז' | 'שניהם'

export const PRIORITY_LABEL: Record<Priority, string> = {
  low: 'לא דחוף',
  normal: 'רגיל',
  high: 'חשוב',
}

export const SHOPPING_CATEGORIES = [
  'תינוקייה',
  'בגדים',
  'האכלה',
  'טיפוח ובריאות',
  'בטיחות',
  'ציוד ליולדת',
  'אחר',
] as const

export type ShoppingCategory = (typeof SHOPPING_CATEGORIES)[number]

export type ShoppingStatus = 'need' | 'ordered' | 'bought'

export const SHOPPING_STATUS_LABEL: Record<ShoppingStatus, string> = {
  need: 'צריך לקנות',
  ordered: 'הוזמן',
  bought: 'נקנה',
}

export interface ShoppingAlternative {
  id: string
  name: string
  price?: number
  link?: string
  note?: string
}

export interface ShoppingItem {
  id: string
  name: string
  category: ShoppingCategory
  estimatedPrice?: number
  actualPrice?: number
  priority: Priority
  status: ShoppingStatus
  assignee?: Assignee
  note?: string
  link?: string
  alternatives: ShoppingAlternative[]
  chosenAlternativeId?: string
  createdAt: number
}

export const TASK_CATEGORIES = [
  'הכנת הבית',
  'מסמכים וביטוח',
  'רפואי',
  'תיק ליולדת',
  'אחר',
] as const

export type TaskCategory = (typeof TASK_CATEGORIES)[number]

export type TaskFilter = 'all' | TaskCategory
export type ShoppingFilter = 'all' | ShoppingStatus

export interface TaskItem {
  id: string
  title: string
  category: TaskCategory
  dueDate?: string
  priority: Priority
  assignee?: Assignee
  done: boolean
  note?: string
  createdAt: number
}

export interface ImportantDate {
  id: string
  date: string
  title: string
  wish?: string
  createdAt: number
}

export type ThemeMode = 'light' | 'dark' | 'system'

export interface AppSettings {
  dueDate: string
  babyName?: string
  parents: [string, string]
  theme: ThemeMode
}

export interface AppSnapshot {
  version: 1
  settings: AppSettings
  shoppingItems: ShoppingItem[]
  tasks: TaskItem[]
  importantDates: ImportantDate[]
  exportedAt: string
}
