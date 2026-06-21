'use client'

import { API_BASE_URL } from '@/lib/api'

const GOOGLE_OAUTH_URL = `${API_BASE_URL}/oauth2/authorization/google`

function GoogleIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M17.64 9.205c0-.639-.057-1.252-.164-1.841H9v3.481h4.844a4.14 4.14 0 0 1-1.796 2.716v2.259h2.908c1.702-1.567 2.684-3.875 2.684-6.615Z"
      />
      <path
        fill="#34A853"
        d="M9 18c2.43 0 4.467-.806 5.956-2.18l-2.908-2.259c-.806.54-1.837.859-3.048.859-2.344 0-4.328-1.583-5.036-3.71H.957v2.332A8.997 8.997 0 0 0 9 18Z"
      />
      <path
        fill="#FBBC05"
        d="M3.964 10.71A5.41 5.41 0 0 1 3.682 9c0-.593.102-1.17.282-1.71V4.958H.957A8.997 8.997 0 0 0 0 9c0 1.452.348 2.827.957 4.042l3.007-2.332Z"
      />
      <path
        fill="#EA4335"
        d="M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0A8.997 8.997 0 0 0 .957 4.958L3.964 7.29C4.672 5.163 6.656 3.58 9 3.58Z"
      />
    </svg>
  )
}

export function LoginScreen() {
  function handleGoogleLogin() {
    window.location.href = GOOGLE_OAUTH_URL
  }

  return (
    <div className="flex flex-col h-full overflow-hidden bg-background">
      <div className="flex-1 flex flex-col items-center justify-center px-8 text-center">
        {/* Brand */}
        <h1 className="text-3xl font-black tracking-tight text-foreground">POPSPOT</h1>
        <p className="text-sm text-muted-foreground mt-3 text-balance">
          팝업스토어 예약부터 한정 굿즈, 쿠폰까지<br />한 곳에서 만나보세요.
        </p>
      </div>

      {/* Bottom action */}
      <div className="px-6 pb-10 pt-3 shrink-0">
        <button
          onClick={handleGoogleLogin}
          className="w-full py-4 rounded-xl bg-card border border-border text-foreground font-semibold text-sm active:scale-[0.98] transition-all flex items-center justify-center gap-2.5"
        >
          <GoogleIcon />
          Google 계정으로 계속하기
        </button>
        <p className="text-[11px] text-muted-foreground mt-4 text-balance">
          로그인 시 이용약관 및 개인정보 처리방침에 동의하게 됩니다.
        </p>
      </div>
    </div>
  )
}
