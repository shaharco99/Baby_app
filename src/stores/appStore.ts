import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { createAppStorage } from '@/lib/storage'
import type {
  AppSettings,
  AppSnapshot,
  ImportantDate,
  ShoppingFilter,
  ShoppingItem,
  TaskFilter,
  TaskItem,
} from '@/types/models'

function defaultDueDate(): string {
  const d = new Date()
  d.setDate(d.getDate() + 30)
  return d.toISOString().slice(0, 10)
}

const defaultSettings: AppSettings = {
  dueDate: defaultDueDate(),
  babyName: undefined,
  parents: ['שחר', 'טופז'],
  theme: 'system',
}

interface AppState {
  settings: AppSettings
  shoppingItems: ShoppingItem[]
  tasks: TaskItem[]
  importantDates: ImportantDate[]

  taskFilter: TaskFilter
  shoppingFilter: ShoppingFilter
  setTaskFilter: (filter: TaskFilter) => void
  setShoppingFilter: (filter: ShoppingFilter) => void

  updateSettings: (patch: Partial<AppSettings>) => void

  addShoppingItem: (item: Omit<ShoppingItem, 'id' | 'createdAt' | 'alternatives'>) => void
  updateShoppingItem: (id: string, patch: Partial<ShoppingItem>) => void
  removeShoppingItem: (id: string) => void

  addTask: (task: Omit<TaskItem, 'id' | 'createdAt'>) => void
  updateTask: (id: string, patch: Partial<TaskItem>) => void
  removeTask: (id: string) => void

  addImportantDate: (date: Omit<ImportantDate, 'id' | 'createdAt'>) => void
  updateImportantDate: (id: string, patch: Partial<ImportantDate>) => void
  removeImportantDate: (id: string) => void

  exportSnapshot: () => AppSnapshot
  importSnapshot: (snapshot: AppSnapshot, mode: 'replace' | 'merge') => void
  clearAllData: () => void
}

function newId(): string {
  return crypto.randomUUID()
}

/** Merges imported records into existing ones, skipping any id already present. */
function mergeById<T extends { id: string }>(existing: T[], incoming: T[]): T[] {
  const existingIds = new Set(existing.map((item) => item.id))
  return [...existing, ...incoming.filter((item) => !existingIds.has(item.id))]
}

export const useAppStore = create<AppState>()(
  persist(
    (set, get) => ({
      settings: defaultSettings,
      shoppingItems: [],
      tasks: [],
      importantDates: [],

      taskFilter: 'all',
      shoppingFilter: 'all',
      setTaskFilter: (filter) => set({ taskFilter: filter }),
      setShoppingFilter: (filter) => set({ shoppingFilter: filter }),

      updateSettings: (patch) =>
        set((s) => ({ settings: { ...s.settings, ...patch } })),

      addShoppingItem: (item) =>
        set((s) => ({
          shoppingItems: [
            ...s.shoppingItems,
            { ...item, id: newId(), createdAt: Date.now(), alternatives: [] },
          ],
        })),
      updateShoppingItem: (id, patch) =>
        set((s) => ({
          shoppingItems: s.shoppingItems.map((i) => (i.id === id ? { ...i, ...patch } : i)),
        })),
      removeShoppingItem: (id) =>
        set((s) => ({ shoppingItems: s.shoppingItems.filter((i) => i.id !== id) })),

      addTask: (task) =>
        set((s) => ({ tasks: [...s.tasks, { ...task, id: newId(), createdAt: Date.now() }] })),
      updateTask: (id, patch) =>
        set((s) => ({ tasks: s.tasks.map((t) => (t.id === id ? { ...t, ...patch } : t)) })),
      removeTask: (id) => set((s) => ({ tasks: s.tasks.filter((t) => t.id !== id) })),

      addImportantDate: (date) =>
        set((s) => ({
          importantDates: [...s.importantDates, { ...date, id: newId(), createdAt: Date.now() }],
        })),
      updateImportantDate: (id, patch) =>
        set((s) => ({
          importantDates: s.importantDates.map((d) => (d.id === id ? { ...d, ...patch } : d)),
        })),
      removeImportantDate: (id) =>
        set((s) => ({ importantDates: s.importantDates.filter((d) => d.id !== id) })),

      exportSnapshot: () => {
        const s = get()
        return {
          version: 1,
          settings: s.settings,
          shoppingItems: s.shoppingItems,
          tasks: s.tasks,
          importantDates: s.importantDates,
          exportedAt: new Date().toISOString(),
        }
      },
      importSnapshot: (snapshot, mode) =>
        set((s) =>
          mode === 'replace'
            ? {
                settings: snapshot.settings,
                shoppingItems: snapshot.shoppingItems,
                tasks: snapshot.tasks,
                importantDates: snapshot.importantDates,
              }
            : {
                settings: snapshot.settings,
                shoppingItems: mergeById(s.shoppingItems, snapshot.shoppingItems),
                tasks: mergeById(s.tasks, snapshot.tasks),
                importantDates: mergeById(s.importantDates, snapshot.importantDates),
              },
        ),
      clearAllData: () =>
        set({
          settings: defaultSettings,
          shoppingItems: [],
          tasks: [],
          importantDates: [],
        }),
    }),
    {
      name: 'od-yareach-store',
      storage: createAppStorage(),
    },
  ),
)
