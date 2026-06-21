'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { LogIn, LogOut, Settings } from 'lucide-react'
import { cn } from '@/lib/utils'
import { API_BASE_URL } from '@/lib/api'

interface AppHeaderProps {
  className?: string
}

// 로그인 여부 판별용 플래그 쿠키 (access_token 은 HttpOnly 라 JS 로 읽을 수 없음)
function hasLoggedInCookie(): boolean {
  return document.cookie.split('; ').some((c) => c.startsWith('logged_in='))
}

export function AppHeader({ className }: AppHeaderProps) {
  const router = useRouter()
  const [isLoggedIn, setIsLoggedIn] = useState(false)

  useEffect(() => {
    setIsLoggedIn(hasLoggedInCookie())
  }, [])

  async function handleLogout() {
    try {
      // HttpOnly access_token 은 서버만 삭제할 수 있으므로 백엔드 로그아웃 호출
      await fetch(`${API_BASE_URL}/auth/logout`, {
        method: 'POST',
        credentials: 'include',
      })
    } catch {
      // 네트워크 오류가 나도 로그인 화면으로 보낸다.
    }
    setIsLoggedIn(false)
    router.push('/login')
  }

  return (
    <header className={cn('flex items-center justify-between px-4 py-3 bg-card border-b border-border', className)}>
      <span className="text-[18px] font-black tracking-tighter text-foreground leading-none">POPSPOT</span>

      <div className="flex items-center gap-3">
        {isLoggedIn ? (
          <>
            <button
              onClick={() => router.push('/organizer')}
              className="flex items-center gap-1 text-[12px] font-semibold text-foreground active:opacity-60 transition-opacity"
              aria-label="관리자"
            >
              <Settings size={15} strokeWidth={1.8} />
              관리자
            </button>
            <button
              onClick={handleLogout}
              className="flex items-center gap-1 text-[12px] font-semibold text-muted-foreground active:opacity-60 transition-opacity"
              aria-label="로그아웃"
            >
              <LogOut size={15} strokeWidth={1.8} />
              로그아웃
            </button>
          </>
        ) : (
          <button
            onClick={() => router.push('/login')}
            className="flex items-center gap-1 text-[12px] font-semibold text-foreground active:opacity-60 transition-opacity"
            aria-label="로그인"
          >
            <LogIn size={15} strokeWidth={1.8} />
            로그인
          </button>
        )}
      </div>
    </header>
  )
}
