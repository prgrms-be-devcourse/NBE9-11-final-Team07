export interface ApiResponse<T> {
    code: string
    message: string
    data: T
}

export class ApiError extends Error {
    constructor(
        message: string,
        public readonly status: number,
        public readonly code?: string,
    ) {
        super(message)
        this.name = 'ApiError'
    }
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080'

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers = new Headers(init.headers)
    if (init.body && !headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json')
    }
    const response = await fetch(`${API_BASE_URL}${path}`, {
        ...init,
        headers,
        credentials: 'include',  // 쿠키 인증
    })
    const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null
    if (!response.ok) {
        throw new ApiError(
            payload?.message ?? 'API 요청에 실패했습니다.',
            response.status,
            payload?.code,
        )
    }
    if (!payload) {
        throw new ApiError('서버 응답을 읽을 수 없습니다.', response.status)
    }
    return payload.data
}