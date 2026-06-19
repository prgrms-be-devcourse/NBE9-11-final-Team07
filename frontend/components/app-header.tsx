'use client'

import { cn } from '@/lib/utils'

interface AppHeaderProps {
  className?: string
}

export function AppHeader({ className }: AppHeaderProps) {
  return (
    <header className={cn('flex items-center px-4 py-3 bg-card border-b border-border', className)}>
      <span className="text-[18px] font-black tracking-tighter text-foreground leading-none">POPSPOT</span>
    </header>
  )
}
