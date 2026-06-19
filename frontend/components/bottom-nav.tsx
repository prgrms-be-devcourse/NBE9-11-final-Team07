'use client'

import { Home, CalendarCheck, Ticket, User } from 'lucide-react'
import { cn } from '@/lib/utils'

export type TabKey = 'home' | 'reservations' | 'coupons' | 'mypage'

const tabs: { key: TabKey; label: string; Icon: typeof Home }[] = [
  { key: 'home', label: '홈', Icon: Home },
  { key: 'reservations', label: '내 예약', Icon: CalendarCheck },
  { key: 'coupons', label: '내 쿠폰', Icon: Ticket },
  { key: 'mypage', label: '마이', Icon: User },
]

interface BottomNavProps {
  active: TabKey
  onChange: (tab: TabKey) => void
}

export function BottomNav({ active, onChange }: BottomNavProps) {
  return (
    <nav className="border-t border-border bg-card flex items-stretch">
      {tabs.map(({ key, label, Icon }) => {
        const isActive = active === key
        return (
          <button
            key={key}
            onClick={() => onChange(key)}
            className={cn(
              'flex flex-1 flex-col items-center justify-center gap-0.5 py-2.5 text-[10px] font-medium transition-colors',
              isActive ? 'text-foreground' : 'text-muted-foreground',
            )}
            aria-label={label}
          >
            <Icon
              size={22}
              strokeWidth={isActive ? 2.2 : 1.6}
              className={isActive ? 'text-foreground' : 'text-muted-foreground'}
            />
            <span className={cn('leading-none', isActive && 'font-semibold')}>{label}</span>
          </button>
        )
      })}
    </nav>
  )
}
