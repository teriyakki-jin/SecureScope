import { Header } from './components/Layout/Header'
import { EventFeed } from './components/EventFeed/EventFeed'
import { AlertTable } from './components/AlertTable/AlertTable'
import { IpChart } from './components/IpChart/IpChart'

export default function App() {
  return (
    <div className="min-h-screen bg-slate-900 text-white">
      <Header />

      <main className="max-w-7xl mx-auto px-4 py-6 space-y-6">
        {/* 실시간 피드 */}
        <EventFeed />

        {/* IP 통계 차트 */}
        <IpChart />

        {/* 탐지 이력 테이블 */}
        <AlertTable />
      </main>
    </div>
  )
}
