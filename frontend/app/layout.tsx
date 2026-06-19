import type { Metadata, Viewport } from 'next'
import { Geist_Mono } from 'next/font/google'
import './globals.css'

const geistMono = Geist_Mono({
  variable: '--font-geist-mono',
  subsets: ['latin'],
})

export const metadata: Metadata = {
  title: 'POPSPOT — 팝업스토어 예약 & 한정 굿즈',
  description: '팝업스토어 입장권 예약, 한정판 굿즈 구매, 오프라인 쿠폰을 한 곳에서',
  generator: 'v0.app',
}

export const viewport: Viewport = {
  themeColor: '#ffffff',
  width: 'device-width',
  initialScale: 1,
  userScalable: false,
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="ko" className={`${geistMono.variable} bg-[oklch(0.94_0_0)]`}>
      <body className="font-sans antialiased">{children}</body>
    </html>
  )
}
