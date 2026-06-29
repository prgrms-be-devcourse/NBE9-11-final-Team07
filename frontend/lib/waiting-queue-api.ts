import { apiRequest } from '@/lib/api'

export type WaitingQueueStatus = 'ADMITTED' | 'WAITING' | 'NOT_IN_QUEUE'

export interface WaitingStatusResponse {
  status: WaitingQueueStatus
  rank: number | null
  estimatedSeconds: number | null
  pollIntervalSeconds: number | null
}

export const waitingQueueApi = {
  getStatus: (popupStoreId: string) =>
    apiRequest<WaitingStatusResponse>(`/popups/${popupStoreId}/waiting-status`),
}
