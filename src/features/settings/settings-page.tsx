import { useRef, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogDescription,
} from '@/components/ui/dialog'
import { PageContainer } from '@/components/layout/page-container'
import { PageHeader } from '@/components/layout/page-header'
import { useAppStore } from '@/stores/appStore'
import { isValidAppSnapshot } from '@/lib/snapshot-validation'
import type { ThemeMode } from '@/types/models'

export default function SettingsPage() {
  const settings = useAppStore((s) => s.settings)
  const updateSettings = useAppStore((s) => s.updateSettings)
  const exportSnapshot = useAppStore((s) => s.exportSnapshot)
  const importSnapshot = useAppStore((s) => s.importSnapshot)
  const clearAllData = useAppStore((s) => s.clearAllData)

  const fileInputRef = useRef<HTMLInputElement>(null)
  const importModeRef = useRef<'replace' | 'merge'>('merge')
  const [clearDialogOpen, setClearDialogOpen] = useState(false)

  function handleExport() {
    const snapshot = exportSnapshot()
    const blob = new Blob([JSON.stringify(snapshot, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `od-yareach-${new Date().toISOString().slice(0, 10)}.json`
    a.click()
    URL.revokeObjectURL(url)
    toast.success('הקובץ ירד בהצלחה')
  }

  function triggerImport(mode: 'replace' | 'merge') {
    importModeRef.current = mode
    fileInputRef.current?.click()
  }

  async function handleFileChosen(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    try {
      const text = await file.text()
      const parsed: unknown = JSON.parse(text)
      if (!isValidAppSnapshot(parsed)) {
        throw new Error('invalid')
      }
      importSnapshot(parsed, importModeRef.current)
      toast.success('הנתונים יובאו בהצלחה')
    } catch {
      toast.error('לא הצלחנו לקרוא את הקובץ. ודאו שזה קובץ שיוצא מהאתר הזה.')
    }
  }

  return (
    <PageContainer>
      <PageHeader title="הגדרות" />

      <Card>
        <CardHeader>
          <CardTitle className="font-heading text-base">פרטים</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-1.5">
            <Label htmlFor="due-date">תאריך משוער ללידה</Label>
            <Input
              id="due-date"
              type="date"
              value={settings.dueDate}
              onChange={(e) => updateSettings({ dueDate: e.target.value })}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="baby-name">שם התינוקת (אם כבר יש)</Label>
            <Input
              id="baby-name"
              value={settings.babyName ?? ''}
              onChange={(e) => updateSettings({ babyName: e.target.value || undefined })}
              placeholder="אופציונלי"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="parent-a">שם ההורה הראשון</Label>
              <Input
                id="parent-a"
                value={settings.parents[0]}
                onChange={(e) => updateSettings({ parents: [e.target.value, settings.parents[1]] })}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="parent-b">שם ההורה השני</Label>
              <Input
                id="parent-b"
                value={settings.parents[1]}
                onChange={(e) => updateSettings({ parents: [settings.parents[0], e.target.value] })}
              />
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="font-heading text-base">תצוגה</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-1.5">
            <Label>ערכת נושא</Label>
            <Select
              value={settings.theme}
              onValueChange={(v) => updateSettings({ theme: v as ThemeMode })}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="system">לפי המכשיר</SelectItem>
                <SelectItem value="light">בהיר</SelectItem>
                <SelectItem value="dark">כהה</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="font-heading text-base">גיבוי ושיתוף נתונים</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-sm text-muted-foreground">
            האתר לא מחובר לשרת כרגע — כל מה שמוזן נשמר רק במכשיר הזה. כדי לשתף את הרשימה בין שני
            המכשירים שלכם, ייצאו קובץ גיבוי מפה וישלחו אותו, ואז יבואו אותו במכשיר השני.
          </p>
          <div className="flex flex-wrap gap-2">
            <Button onClick={handleExport} variant="default">
              ייצוא קובץ גיבוי
            </Button>
            <Button onClick={() => triggerImport('merge')} variant="outline">
              ייבוא ומיזוג
            </Button>
            <Button onClick={() => triggerImport('replace')} variant="outline">
              ייבוא והחלפה מלאה
            </Button>
          </div>
          <input
            ref={fileInputRef}
            type="file"
            accept="application/json"
            className="hidden"
            onChange={handleFileChosen}
          />
        </CardContent>
      </Card>

      <Card className="border-destructive/40">
        <CardHeader>
          <CardTitle className="font-heading text-base text-destructive">אזור מסוכן</CardTitle>
        </CardHeader>
        <CardContent>
          <Button variant="outline" className="border-destructive text-destructive" onClick={() => setClearDialogOpen(true)}>
            מחיקת כל הנתונים
          </Button>
        </CardContent>
      </Card>

      <Dialog open={clearDialogOpen} onOpenChange={setClearDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>למחוק את כל הנתונים?</DialogTitle>
            <DialogDescription>
              הפעולה הזו תמחק את כל הפריטים, המשימות והתאריכים שהוזנו במכשיר הזה. לא ניתן לשחזר
              אלא אם יש לכם קובץ גיבוי.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setClearDialogOpen(false)}>
              ביטול
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                clearAllData()
                setClearDialogOpen(false)
                toast.success('הנתונים נמחקו')
              }}
            >
              מחיקה
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageContainer>
  )
}
