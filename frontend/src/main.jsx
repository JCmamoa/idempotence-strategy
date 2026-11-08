import React, { useCallback, useEffect, useState } from "react"
import { createRoot } from "react-dom/client"
import "./style.css"

function App() {
  const [messages, setMessages] = useState([])
  const [error, setError] = useState("")
  const [payload, setPayload] = useState('{"orderId":"ORD-1042","amount":129.90}')
  const [simulateFailure, setSimulateFailure] = useState(true)
  const [busy, setBusy] = useState(false)

  const refresh = useCallback(async () => {
    try {
      const response = await fetch("/api/dlq")
      if (!response.ok) throw new Error(`API retornou HTTP ${response.status}`)
      setMessages(await response.json())
      setError("")
    } catch (exception) {
      setError(`Não foi possível carregar a DLQ: ${exception.message}`)
    }
  }, [])

  useEffect(() => {
    refresh()
    const timer = setInterval(refresh, 5000)
    return () => clearInterval(timer)
  }, [refresh])

  async function sendEvent(event) {
    event.preventDefault()
    setBusy(true)
    try {
      JSON.parse(payload)
      const response = await fetch("/api/events", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ payload, simulateFailure }),
      })
      if (!response.ok) throw new Error(`API retornou HTTP ${response.status}`)
      setError("")
      window.setTimeout(refresh, 1200)
    } catch (exception) {
      setError(exception instanceof SyntaxError ? "Payload precisa ser um JSON válido." : exception.message)
    } finally {
      setBusy(false)
    }
  }

  async function reprocess(message) {
    const reason = window.prompt("Motivo obrigatório para auditoria:")
    if (!reason?.trim()) return
    try {
      const response = await fetch(`/api/dlq/${message.id}/reprocess`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ payload: message.payload, simulateFailure: false, reason }),
      })
      if (!response.ok) {
        const detail = await response.json().catch(() => ({}))
        throw new Error(detail.message || `API retornou HTTP ${response.status}`)
      }
      await refresh()
    } catch (exception) {
      setError(`Falha ao solicitar reprocessamento: ${exception.message}`)
    }
  }

  const openCount = messages.filter((message) => message.status === "OPEN").length

  return (
    <main className="shell">
      <header className="topbar">
        <div className="brand-mark">D</div>
        <div>
          <div className="eyebrow">OPERAÇÕES DE MENSAGERIA</div>
          <h1>Dead Letter Console</h1>
        </div>
        <div className="connection"><span /> RabbitMQ conectado</div>
      </header>

      <section className="intro">
        <div>
          <p className="eyebrow">CENTRAL DE OPERAÇÕES</p>
          <h2>Mensagens sob controle.</h2>
          <p className="muted">Inspecione falhas, acompanhe tentativas e reprocese eventos com rastreabilidade.</p>
        </div>
        <div className="metric">
          <span className="metric-label">MENSAGENS ABERTAS</span>
          <strong>{String(openCount).padStart(2, "0")}</strong>
          <span className="metric-foot">na fila de tratamento</span>
        </div>
      </section>

      <section className="panel producer">
        <div className="section-heading">
          <div><p className="eyebrow">AMBIENTE LOCAL</p><h3>Publicar evento de teste</h3></div>
          <span className="tag">EVENTS.MAIN</span>
        </div>
        <form onSubmit={sendEvent}>
          <label htmlFor="payload">Payload JSON</label>
          <textarea id="payload" value={payload} onChange={(event) => setPayload(event.target.value)} rows="3" />
          <div className="form-footer">
            <label className="checkbox"><input type="checkbox" checked={simulateFailure} onChange={(event) => setSimulateFailure(event.target.checked)} /> Simular falha de negócio para testar retries e DLQ</label>
            <button type="submit" disabled={busy}>{busy ? "Publicando..." : "Publicar evento"} <span>↗</span></button>
          </div>
        </form>
      </section>

      {error && <div className="error">{error}</div>}

      <section className="panel">
        <div className="section-heading list-heading">
          <div><p className="eyebrow">TRATAMENTO MANUAL</p><h3>Fila de mensagens mortas <span className="count">{messages.length}</span></h3></div>
          <button className="refresh" onClick={refresh} aria-label="Atualizar fila">↻ Atualizar</button>
        </div>
        {messages.length === 0 ? (
          <div className="empty"><div className="empty-icon">✓</div><strong>Nenhuma mensagem na DLQ</strong><span>Mensagens que esgotarem as tentativas aparecerão aqui.</span></div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>EVENTO</th><th>FALHA</th><th>TENTATIVAS</th><th>RECEBIDA</th><th>STATUS</th><th></th></tr></thead>
              <tbody>{messages.map((message) => (
                <tr key={message.id}>
                  <td><code>{message.eventId.slice(0, 12)}…</code><details><summary>Ver payload</summary><pre>{message.payload}</pre></details></td>
                  <td className="failure">{message.failureReason}</td>
                  <td><span className="attempts">{message.attempts}x</span></td>
                  <td className="date">{new Date(message.createdAt).toLocaleString("pt-BR")}</td>
                  <td><span className={`status ${message.status.toLowerCase()}`}>{message.status === "OPEN" ? "Aberta" : message.status === "REPROCESSING" ? "Reprocessando" : "Reprocessada"}</span></td>
                  <td><button className="replay" disabled={message.status === "REPROCESSING"} onClick={() => reprocess(message)}>Reprocessar ↗</button></td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        )}
      </section>

      <footer><span>DLQ CONSOLE · MVP LOCAL</span><span>Retry exponencial: 1 min → 5 min → DLQ</span></footer>
    </main>
  )
}

createRoot(document.getElementById("root")).render(<App />)
