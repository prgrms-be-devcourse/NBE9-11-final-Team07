'use client'

import { useEffect, useRef, useState } from 'react'
import { AlertCircle, ArrowLeft, Clock3, Loader2 } from 'lucide-react'
import { waitingQueueApi } from '@/lib/waiting-queue-api'
import type { WaitingStatusResponse } from '@/lib/waiting-queue-api'

interface WaitingQueueScreenProps {
  storeId: string
  onBack: () => void
  onAdmitted: () => void
}

function formatEstimatedTime(seconds: number | null) {
  if (seconds == null) return '계산 중'
  if (seconds < 60) return `약 ${seconds}초`
  const minutes = Math.ceil(seconds / 60)
  return `약 ${minutes}분`
}

export function WaitingQueueScreen({ storeId, onBack, onAdmitted }: WaitingQueueScreenProps) {
  const [status, setStatus] = useState<WaitingStatusResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const admittedRef = useRef(false)

  useEffect(() => {
    let active = true
    let timer: ReturnType<typeof setTimeout> | null = null
    admittedRef.current = false

    async function poll() {
      try {
        const nextStatus = await waitingQueueApi.getStatus(storeId)
        if (!active) return

        setStatus(nextStatus)
        setError(null)
        setLoading(false)

        if (nextStatus.status === 'ADMITTED') {
          if (!admittedRef.current) {
            admittedRef.current = true
            timer = setTimeout(onAdmitted, 600)
          }
          return
        }

        if (nextStatus.status === 'WAITING') {
          const interval = Math.max(1, nextStatus.pollIntervalSeconds ?? 5) * 1000
          timer = setTimeout(poll, interval)
        }
      } catch (pollError) {
        if (!active) return
        setError(pollError instanceof Error ? pollError.message : '대기 상태를 확인하지 못했습니다.')
        setLoading(false)
        timer = setTimeout(poll, 5000)
      }
    }

    poll()

    return () => {
      active = false
      if (timer) clearTimeout(timer)
    }
  }, [storeId, onAdmitted])

  const isWaiting = status?.status === 'WAITING'
  const isAdmitted = status?.status === 'ADMITTED'
  const isNotInQueue = status?.status === 'NOT_IN_QUEUE'

  return (
    <div className="flex flex-col h-full overflow-hidden bg-background">
      <div className="flex items-center gap-3 px-4 py-3 border-b border-border bg-card shrink-0">
        <button
          onClick={onBack}
          className="flex items-center justify-center w-9 h-9 -ml-1 rounded-full hover:bg-secondary transition-colors"
          aria-label="뒤로 가기"
        >
          <ArrowLeft size={20} />
        </button>
        <h1 className="text-base font-bold text-foreground">입장 대기</h1>
      </div>

      <div className="flex-1 flex flex-col items-center justify-center px-6 text-center">
        {loading ? (
          <div className="flex flex-col items-center gap-3 text-muted-foreground">
            <Loader2 size={26} className="animate-spin" />
            <p className="text-sm font-medium">대기 상태를 확인하고 있어요.</p>
          </div>
        ) : error ? (
          <div className="flex flex-col items-center gap-3">
            <AlertCircle size={30} className="text-[oklch(0.62_0.24_25)]" />
            <div className="space-y-1">
              <p className="text-base font-bold text-foreground">대기 상태를 확인할 수 없습니다.</p>
              <p className="text-sm text-muted-foreground">{error}</p>
            </div>
          </div>
        ) : isNotInQueue ? (
          <div className="flex flex-col items-center gap-4">
            <div className="flex items-center justify-center w-14 h-14 rounded-full bg-secondary">
              <AlertCircle size={24} className="text-muted-foreground" />
            </div>
            <div className="space-y-2">
              <p className="text-base font-bold text-foreground">대기 정보가 만료되었거나 존재하지 않습니다.</p>
              <p className="text-sm text-muted-foreground">다시 입장해 주세요.</p>
            </div>
          </div>
        ) : isAdmitted ? (
          <div className="flex flex-col items-center gap-4">
            <div className="flex items-center justify-center w-14 h-14 rounded-full bg-foreground text-background">
              <Loader2 size={24} className="animate-spin" />
            </div>
            <div className="space-y-2">
              <p className="text-base font-bold text-foreground">입장 준비가 완료되었습니다.</p>
              <p className="text-sm text-muted-foreground">곧 자동으로 이동합니다.</p>
            </div>
          </div>
        ) : isWaiting ? (
          <div className="w-full max-w-[320px] space-y-8">
            <div className="space-y-2">
              <p className="text-sm font-bold text-muted-foreground">입장 대기 중</p>
              <p className="text-[15px] text-muted-foreground">순서가 되면 자동으로 입장합니다.</p>
            </div>

            <div className="space-y-3">
              <p className="text-xs font-bold text-muted-foreground tracking-wide">현재 대기 순번</p>
              <div className="flex items-end justify-center gap-1">
                <span className="text-6xl font-black leading-none text-foreground tabular-nums">
                  {status.rank ?? '-'}
                </span>
                <span className="pb-2 text-xl font-black text-foreground">번째</span>
              </div>
            </div>

            <div className="rounded-xl border border-border bg-secondary px-4 py-4 space-y-2">
              <div className="flex items-center justify-center gap-2 text-muted-foreground">
                <Clock3 size={15} />
                <span className="text-xs font-bold">예상 대기 시간</span>
              </div>
              <p className="text-xl font-black text-foreground">
                {formatEstimatedTime(status.estimatedSeconds)}
              </p>
            </div>

            <div className="flex items-center justify-center gap-2 text-muted-foreground">
              <Loader2 size={14} className="animate-spin" />
              <p className="text-xs font-medium">화면을 닫지 말고 잠시만 기다려 주세요.</p>
            </div>
          </div>
        ) : null}
      </div>
    </div>
  )
}
