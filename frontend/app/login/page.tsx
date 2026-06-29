import { LoginScreen } from '@/components/screens/login-screen'

export default function LoginPage() {
  return (
    <div className="min-h-screen flex items-center justify-center bg-[oklch(0.94_0_0)]">
      <div className="relative w-full max-w-[430px] h-screen sm:h-[812px] flex flex-col bg-background sm:rounded-[2.5rem] sm:overflow-hidden sm:shadow-2xl sm:border sm:border-black/10">
        <div className="flex-1 overflow-hidden flex flex-col">
          <LoginScreen />
        </div>
      </div>
    </div>
  )
}
