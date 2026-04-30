export function Header() {
  return (
    <header className="bg-slate-900 text-white px-6 py-4 flex items-center gap-3 shadow-lg">
      <span className="text-2xl">🛡️</span>
      <div>
        <h1 className="text-xl font-bold tracking-wide">SecureScope</h1>
        <p className="text-xs text-slate-400">Security Event Monitoring Dashboard</p>
      </div>
    </header>
  )
}
