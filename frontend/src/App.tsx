import { useEffect, useRef, useState } from 'react';

type Account = { id: string; displayName: string; scenario: string; description: string };
type Tx = { id: string; occurredAt: string; direction: 'IN' | 'OUT'; amount: number; counterparty: string; description: string };
type Finding = { ruleId: string; title: string; points: number; explanation: string; transactionIds: string[]; alternativeExplanation: string };
type Analysis = { accountId: string; asOf: string; windowStart: string; riskScore: number; priority: string; historicalOutgoingCount: number; historicalMedian: number; incomingTotal: number; outgoingTotal: number; findings: Finding[]; scope: string };
type Review = { id: string; status: string; note: string; createdAt: string };
type Summary = { account: Account; analysis: Analysis; status: string };
type Detail = { account: Account; transactions: Tx[]; analysis: Analysis; reviews: Review[] };
type Explanation = { source: string; summary: string; nextSteps: string[]; limitation: string };
type Meta = { asOf: string; aiConfigured: boolean; reviewerConfigured: boolean; policy: Record<string, number> };
const money = (n: number) => new Intl.NumberFormat('en', { maximumFractionDigits: 0 }).format(n);
const time = (v: string) => new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Bangkok', day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(v));
const statusLabel = (s: string) => s.toLowerCase().replaceAll('_', ' ').replace(/^./, c => c.toUpperCase());
async function api<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, options);
  if (!response.ok) { const body = await response.json().catch(() => ({})); throw new Error([body.error || `Request failed (${response.status}).`, ...(body.issues || [])].join('\n')); }
  return response.json();
}
function Icon({ kind }: { kind: string }) {
  const paths: Record<string, React.ReactNode> = {
    grid: <><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></>,
    shield: <><path d="M12 3 4 6v6c0 5 8 9 8 9s8-4 8-9V6z"/><path d="m8 12 3 3 5-6"/></>,
    flow: <><rect x="3" y="3" width="7" height="6" rx="1.5"/><rect x="14" y="15" width="7" height="6" rx="1.5"/><path d="M7 9v8h7M10 6h7v9"/></>,
    arrow: <><path d="M5 12h14m-5-5 5 5-5 5"/></>,
    refresh: <><path d="M20 8a8 8 0 1 0 0 8M20 3v5h-5"/></>,
    spark: <><path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5z"/></>,
    lock: <><rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></>,
  };
  return <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[kind] || paths.arrow}</svg>;
}
function Badge({ priority }: { priority: string }) { return <span className={`badge ${priority.toLowerCase()}`}><i/>{priority.toLowerCase()} priority</span>; }

export default function App() {
  const [view, setView] = useState('overview');
  const [meta, setMeta] = useState<Meta>();
  const [accounts, setAccounts] = useState<Summary[]>([]);
  const [selected, setSelected] = useState('DEMO-003');
  const [detail, setDetail] = useState<Detail>();
  const [filter, setFilter] = useState('ALL');
  const [query, setQuery] = useState('');
  const [tab, setTab] = useState('findings');
  const [evidence, setEvidence] = useState<string[] | null>(null);
  const [explanation, setExplanation] = useState<Explanation>();
  const [aiBusy, setAiBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [token, setToken] = useState('');
  const [tokenDialog, setTokenDialog] = useState(false);
  const [note, setNote] = useState('');
  const [reviewStatus, setReviewStatus] = useState('PENDING_REVIEW');
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState('');
  const [revision, setRevision] = useState(0);
  const [importName, setImportName] = useState('');
  const [importFile, setImportFile] = useState<File>();
  const [fictional, setFictional] = useState(false);
  const [importBusy, setImportBusy] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);
  const currentAccount = useRef(selected);
  const analysisRequest = useRef(0);
  currentAccount.current = selected;

  useEffect(() => {
    let active = true;
    Promise.all([api<Meta>('/api/meta'), api<Summary[]>('/api/accounts')]).then(([m,a]) => {
      if (active) { setMeta(m); setAccounts(a); }
    }).catch(e => active && setError(e.message));
    return () => { active = false; };
  }, [revision]);
  useEffect(() => {
    let active = true;
    setLoading(true); setDetail(undefined); setExplanation(undefined); setAiBusy(false); setEvidence(null); setNote(''); setNotice(''); setError('');
    analysisRequest.current++;
    api<Detail>(`/api/accounts/${selected}`).then(d => {
      if (active) { setDetail(d); setReviewStatus(d.reviews[0]?.status || 'PENDING_REVIEW'); }
    }).catch(e => active && setError(e.message)).finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [selected, revision]);
  function refresh() { setRevision(r => r + 1); }
  async function uploadCsv(e: React.FormEvent) {
    e.preventDefault(); if (!importFile) return;
    setImportBusy(true); setError('');
    const data = new FormData();
    data.set('displayName', importName); data.set('syntheticConfirmed', String(fictional)); data.set('file', importFile);
    try {
      const result = await api<{ accountId: string }>('/api/imports', { method: 'POST', headers: { 'X-Reviewer-Token': token }, body: data });
      setImportName(''); setImportFile(undefined); setFictional(false);
      if (fileInput.current) fileInput.current.value = '';
      setFilter('ALL'); setQuery(''); setSelected(result.accountId); setTab('findings'); setView('overview'); refresh();
    } catch (e) { setError((e as Error).message); }
    finally { setImportBusy(false); }
  }
  async function explain() {
    const id = selected; const request = ++analysisRequest.current;
    setAiBusy(true); setError('');
    try {
      const result = await api<Explanation>(`/api/accounts/${id}/explanation`, { method: 'POST', headers: { 'X-Reviewer-Token': token } });
      if (currentAccount.current === id && request === analysisRequest.current) setExplanation(result);
    } catch (e) { if (currentAccount.current === id && request === analysisRequest.current) setError((e as Error).message); }
    finally { if (currentAccount.current === id && request === analysisRequest.current) setAiBusy(false); }
  }
  async function saveReview() {
    const id = selected; setSaving(true); setError('');
    try {
      await api<Review>(`/api/accounts/${id}/reviews`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Reviewer-Token': token }, body: JSON.stringify({ status: reviewStatus, note }) });
      const [d,a] = await Promise.all([api<Detail>(`/api/accounts/${id}`), api<Summary[]>('/api/accounts')]);
      setAccounts(a);
      if (currentAccount.current === id) { setDetail(d); setNote(''); setNotice('Review saved to the audit history.'); }
    } catch (e) { if (currentAccount.current === id) setError((e as Error).message); }
    finally { setSaving(false); }
  }
  const visible = accounts.filter(a => (filter === 'ALL' || a.analysis.priority === filter) && `${a.account.id} ${a.account.displayName}`.toLowerCase().includes(query.toLowerCase()));
  const analysis = detail?.analysis;
  const transactions = detail?.transactions.filter(t => !evidence || evidence.includes(t.id)) || [];
  const reviewed = accounts.filter(a => a.status !== 'PENDING_REVIEW').length;

  return <div className="shell">
    <aside className="sidebar">
      <div className="brand"><span className="brandmark"><Icon kind="shield"/></span><span>RiskDesk<small>TRANSACTION INTELLIGENCE</small></span></div>
      <div className="workspace"><span className="workspace-icon">PT</span><div>Portfolio workspace<small>Phyo Thant Kyaw</small></div><span className="tiny-dot"/></div>
      <p className="nav-label">WORKSPACE</p>
      <nav>{[['overview','grid','Transaction review'],['policy','shield','Rule policy'],['automation','flow','Automation'],['import','flow','Import CSV']].map(([v,i,l]) => <button key={v} className={view === v ? 'nav-item active' : 'nav-item'} onClick={() => setView(v)}><Icon kind={i}/>{l}{v === 'overview' && <span className="nav-count">{accounts.length}</span>}</button>)}</nav>
      <div className="side-bottom"><div className="prototype-tag"><span className="tiny-dot"/>Portfolio prototype</div><p>Evidence-led review.<br/>Human judgment stays in control.</p><a href="https://pt22-mfu.github.io/pt_portfolio/" target="_blank" rel="noreferrer">View PT's portfolio <Icon kind="arrow"/></a></div>
    </aside>
    <main>
      <header className="topbar"><span>Workspace <span className="crumb">/</span> {view === 'overview' ? 'Transaction review' : view === 'policy' ? 'Rule policy' : view === 'import' ? 'Import CSV' : 'Automation'}</span><div className="top-actions"><span className="demo-pill">SYNTHETIC DATA</span><button className="avatar" aria-label="Configure reviewer access" onClick={() => setTokenDialog(true)}>PT</button></div></header>
      <div className="page">
        <div className="page-title"><div><div className="eyebrow">TRANSACTION RISK REVIEW ASSISTANT</div><h1>{view === 'overview' ? 'See the signals. Review the evidence.' : view === 'policy' ? 'A transparent rule policy.' : view === 'import' ? 'Bring a new scenario to review.' : 'From findings to follow-up.'}</h1><p>{view === 'overview' ? 'Explore account activity, understand triggered rules and document your review.' : view === 'policy' ? 'Explicit thresholds, traceable evidence and a consistent review window.' : view === 'import' ? 'Upload fictional transactions and explore their rule evidence.' : 'A protected reporting endpoint for your n8n workflow.'}</p></div><button className="button secondary" onClick={refresh}><Icon kind="refresh"/>Refresh data</button></div>
        {error && <div className="alert" role="alert">{error} <button onClick={() => setError('')} aria-label="Dismiss error">×</button></div>}
        {view === 'overview' && <>
          <section className="stats" aria-label="Review overview">
            {[['Accounts', accounts.length, 'Seeded and imported fictional histories'],['High priority', accounts.filter(a => a.analysis.priority === 'HIGH').length, 'Multiple signals warrant review'],['Awaiting review', accounts.length-reviewed, 'Pending a reviewer decision'],['Reviewed', reviewed, 'Explanation or escalation recorded']].map(([label,value,desc]) => <div className="stat" key={label}><span>{label}</span><strong>{value}</strong><small>{desc}</small></div>)}
          </section>
          <div className="review-layout">
            <section className="account-panel card"><div className="section-head"><h2>Accounts</h2><span className="count">{visible.length}</span></div><label className="sr-only" htmlFor="account-search">Search accounts</label><input id="account-search" className="search" placeholder="Search name or account ID" value={query} onChange={e => setQuery(e.target.value)}/><div className="filters" aria-label="Filter by priority">{['ALL','HIGH','MEDIUM','LOW'].map(f => <button key={f} className={filter===f ? 'selected' : ''} onClick={() => setFilter(f)}>{f === 'ALL' ? 'All' : statusLabel(f)}</button>)}</div>
              <div className="account-list">{visible.map(a => <button className={`account-item ${selected === a.account.id ? 'chosen' : ''}`} key={a.account.id} onClick={() => { setSelected(a.account.id); setTab('findings'); }}><div className="account-item-top"><span className={`account-symbol ${a.analysis.priority.toLowerCase()}`}>{a.account.id.slice(-2)}</span><span className="account-name">{a.account.displayName}<small>{a.account.id}</small></span><Icon kind="arrow"/></div><div className="account-item-bottom"><Badge priority={a.analysis.priority}/><span>{a.analysis.riskScore}<small>/100</small></span></div><div className="case-state">{statusLabel(a.status)}</div></button>)}{!visible.length && <div className="empty">No accounts match this filter.</div>}</div>
              <div className="snapshot"><span className="tiny-dot"/>Account review snapshot<small>{analysis ? time(analysis.asOf) : meta ? time(meta.asOf) : 'Loading…'} · Bangkok time</small></div>
            </section>
            <section className="detail-area" aria-label="Selected account">
              {loading ? <div className="card loading"><span className="spinner"/>Loading account evidence…</div> : !detail ? <div className="card empty">Could not load this account. Use Refresh data to try again.</div> : <>
                <div className="card account-header"><div><div className="eyebrow">{detail.account.id} <span className="divider">/</span> THB ACCOUNT</div><h2>{detail.account.displayName}</h2><p>{detail.account.description}</p><span className="scenario">{detail.account.scenario}</span></div><div className={`score ${analysis!.priority.toLowerCase()}`} style={{ '--score': `${analysis!.riskScore}%` } as React.CSSProperties}><div><strong>{analysis!.riskScore}</strong><small>review score</small></div></div></div>
                <div className="window-summary"><div><span>24h incoming</span><strong>฿{money(analysis!.incomingTotal)}</strong></div><div><span>24h outgoing</span><strong>฿{money(analysis!.outgoingTotal)}</strong></div><div><span>30-day median outgoing</span><strong>{analysis!.historicalOutgoingCount ? `฿${money(analysis!.historicalMedian)}` : 'Insufficient history'}</strong><small>{analysis!.historicalOutgoingCount} baseline transactions</small></div></div>
                <div className="card evidence-panel"><div className="tabs" role="tablist" aria-label="Account details">{[['findings','Rule findings'],['activity','Transactions'],['review','Review history']].map(([v,l]) => <button role="tab" aria-selected={tab===v} className={tab===v ? 'active' : ''} key={v} onClick={() => { setTab(v); if(v==='activity') setEvidence(null); }}>{l}{v==='findings' && <span>{analysis!.findings.length}</span>}</button>)}</div>
                  {tab==='findings' && <div className="findings"><div className="findings-intro"><Badge priority={analysis!.priority}/><span>Review window: {time(analysis!.windowStart)} – {time(analysis!.asOf)}</span></div>{analysis!.findings.map((f,index) => <article className="finding" key={f.ruleId}><span className="finding-number">{String(index+1).padStart(2,'0')}</span><div><div className="finding-title"><h3>{f.title}</h3><span>+{f.points} points</span></div><p>{f.explanation}</p><details><summary>Possible legitimate explanation</summary><p>{f.alternativeExplanation}</p></details><button className="text-button" onClick={() => { setEvidence(f.transactionIds); setTab('activity'); }}>View {f.transactionIds.length} evidence transactions <Icon kind="arrow"/></button></div></article>)}{!analysis!.findings.length && <div className="no-findings"><Icon kind="shield"/><h3>No configured rules triggered</h3><p>This account's supplied activity did not trigger these five rules. This is not a guarantee of safety.</p></div>}<p className="scope-note">The score helps prioritize review. It is not a probability of fraud.</p></div>}
                  {tab==='activity' && <div className="activity"><div className="table-heading"><span>{evidence ? 'Linked evidence' : 'Complete supplied history'} · {transactions.length} transactions</span>{evidence && <button className="text-button" onClick={() => setEvidence(null)}>Show all transactions</button>}</div><div className="table-scroll"><table><thead><tr><th>Transaction / time</th><th>Counterparty</th><th>Amount (THB)</th></tr></thead><tbody>{transactions.map(t => <tr key={t.id}><td><strong>{t.id}</strong><small>{time(t.occurredAt)}</small></td><td>{t.counterparty}<small>{t.description}</small></td><td className={t.direction==='IN' ? 'amount-in' : ''}>{t.direction==='IN' ? '+' : '−'}{money(t.amount)}<small>{t.direction==='IN' ? 'Incoming' : 'Outgoing'}</small></td></tr>)}</tbody></table></div></div>}
                  {tab==='review' && <div className="review-history">{detail.reviews.map(r => <article key={r.id}><span className="status-label">{statusLabel(r.status)}</span><time>{time(r.createdAt)}</time><p>{r.note}</p></article>)}{!detail.reviews.length && <div className="empty">No review recorded yet. Add your rationale below.</div>}</div>}
                </div>
                <section className="ai-panel card"><div className="section-head"><h2><Icon kind="spark"/>Review explanation</h2><span className="source-label">{explanation ? explanation.source === 'GEMINI' ? 'AI generated' : explanation.source === 'FALLBACK' ? 'Rule fallback' : 'Rule-based template' : meta?.aiConfigured ? 'Gemini available' : 'Rule-based mode'}</span></div>{explanation ? <div className="explanation"><p>{explanation.summary}</p><h3>Suggested reviewer checks</h3><ul>{explanation.nextSteps.map((s,i) => <li key={i}>{s}</li>)}</ul><small>{explanation.limitation}</small></div> : <p className="muted">Get a concise explanation grounded in the computed rule findings. Review the original evidence before deciding.</p>}<button className="button primary" disabled={aiBusy} onClick={explain}>{aiBusy ? <span className="spinner"/> : <Icon kind="spark"/>}{aiBusy ? 'Preparing explanation…' : meta?.aiConfigured ? 'Generate AI explanation' : 'Generate rule explanation'}</button>{meta?.aiConfigured && !token && <small className="helper">Reviewer access is required for live AI calls. <button className="text-button" onClick={() => setTokenDialog(true)}>Configure access</button></small>}</section>
                <section className="card review-form"><div className="section-head"><h2>Document your review</h2><Icon kind="lock"/></div><p className="muted">Record a rationale using fictional information only. Updates are appended to the shared demo history.</p><div className="form-row"><label>Decision<select aria-label="Decision" value={reviewStatus} onChange={e => setReviewStatus(e.target.value)}><option value="PENDING_REVIEW">Pending review</option><option value="EXPLAINED">Explained</option><option value="ESCALATED">Escalated</option></select></label><label className="note-label">Review note<textarea value={note} maxLength={2000} onChange={e => setNote(e.target.value)} placeholder="What did you check, and what explains the activity?" rows={3}/></label></div><div className="form-footer"><small>{token ? 'Reviewer access supplied' : 'Read-only preview · configure reviewer access to save'}</small><button className="button secondary" onClick={() => setTokenDialog(true)}><Icon kind="lock"/>Access</button><button className="button primary" onClick={saveReview} disabled={saving || !note.trim() || !token || !meta?.reviewerConfigured}>{saving ? 'Saving…' : 'Save review'}</button></div>{notice && <p className="success" role="status">{notice}</p>}</section>
              </>}
            </section>
          </div>
        </>}
        {view==='policy' && <section className="card policy-page"><h2>Five explicit rules</h2><p className="muted">Demonstration thresholds chosen to exercise the fictional scenarios. These are not a real bank's policies.</p><div className="policy-list">{[
          ['Transfer burst', `${meta?.policy.burstCount ?? 5} outgoing transfers within ${meta?.policy.burstMinutes ?? 10} minutes`,25],
          ['Amount above baseline', `Above ${meta?.policy.unusualMultiplier ?? 5}× the prior 30-day median and at least THB ${money(meta?.policy.largeAmount ?? 10000)}; requires ${meta?.policy.minimumHistory ?? 5} historical transfers`,20],
          ['New recipient', `Recipient absent from supplied pre-window outgoing history; transfer at least THB ${money(meta?.policy.largeAmount ?? 10000)}`,15],
          ['Rapid fund movement', `At least ${(meta?.policy.rapidOutflowRatio ?? .8)*100}% of a deposit ≥ THB ${money(meta?.policy.rapidIncomingMinimum ?? 20000)} transferred out within ${meta?.policy.rapidMinutes ?? 30} minutes and before another deposit`,25],
          ['Activity after a long gap', `At least ${meta?.policy.dormantDays ?? 30} days without supplied activity, followed by outgoing transfers`,15]
        ].map(([name,description,points]) => <div key={name}><h3>{name}</h3><p>{description}</p><span>+{points} points</span></div>)}</div><div className="policy-footer"><h3>How the score works</h3><p>Each rule adds points once per account review. High: 50–100 · Medium: 20–49 · Low: 0–19. Correlated signals may overlap; this score is not calibrated.</p><h3>Time and evidence</h3><p>The last 24 hours are reviewed at a fixed snapshot. Future transactions are excluded. The baseline uses the 30 days before the review window. A recipient is “new” only relative to the supplied history.</p></div></section>}
        {view==='automation' && <section className="card automation-page"><div className="integration-icon"><Icon kind="flow"/></div><h2>A daily review digest</h2><p className="muted">The backend reporting endpoint is ready. The n8n workflow must be imported, configured and activated separately.</p><ol><li><strong>Schedule</strong><span>n8n runs at your chosen time.</span></li><li><strong>Fetch findings</strong><span>Authenticated request to <code>/api/reports/daily</code>.</span></li><li><strong>Prepare the digest</strong><span>Group high-priority accounts and include rule evidence.</span></li><li><strong>Review and share</strong><span>Save a report or connect your chosen notification channel.</span></li></ol><div className="info-box">Current dataset: fictional account snapshots. The exported workflow does not imply a live bank connection or active scheduled service.</div><button className="button secondary" onClick={() => setView('overview')}>Return to transaction review <Icon kind="arrow"/></button></section>}
        {view==='import' && <section className="card import-page">
          <div className="section-head"><h2>Import a fictional account</h2><Icon kind="lock"/></div>
          <p className="muted">One CSV creates one account. The entire file is validated before saving; rejected files create no account.</p>
          <div className="import-steps"><div><span className="eyebrow">01 · PREPARE</span><h3>Start with a sample</h3><p>Six columns, UTF-8 encoding and one transaction per row. Amounts are in THB; timestamps must include a timezone.</p><a className="button secondary" href="/samples/demo-transactions.csv" download>Download sample CSV <Icon kind="arrow"/></a></div><div><span className="eyebrow">02 · REVIEW</span><h3>A snapshot from your file</h3><p>The latest transaction sets the review cutoff. The preceding 24 hours are checked against the same five rules; earlier rows provide historical context.</p><small>Maximum 1 MB · 1,000 transactions · Unique transaction IDs</small></div></div>
          <form onSubmit={uploadCsv} className="import-form">
            <label>Account display name<input required maxLength={80} placeholder="e.g. Fictional shop account" value={importName} onChange={e=>setImportName(e.target.value)}/></label>
            <label>Transaction CSV<input ref={fileInput} required type="file" accept=".csv,text/csv" onChange={e=>setImportFile(e.target.files?.[0])}/></label>
            <label className="checkbox-label"><input type="checkbox" checked={fictional} onChange={e=>setFictional(e.target.checked)} required/>This file contains fictional demo data only, including names and descriptions.</label>
            <div className="info-box">This shared portfolio demo displays imported accounts to other visitors. Use invented data.</div>
            <div className="form-footer"><small>{token ? 'Reviewer access supplied' : 'Configure reviewer access to import'}</small><button type="button" className="button secondary" onClick={()=>setTokenDialog(true)}><Icon kind="lock"/>Access</button><button className="button primary" type="submit" disabled={importBusy || !token || !meta?.reviewerConfigured || !importFile || !fictional || !importName.trim()}>{importBusy ? 'Validating file…' : 'Import and review'}</button></div>
          </form>
          <details className="csv-spec"><summary>CSV format reference</summary><p><code>transaction_id,occurred_at,direction,amount,counterparty,description</code></p><p>Use IN or OUT, positive decimal amounts with up to two decimal places and timestamps such as 2026-10-05T09:00:00+07:00. Use the same counterparty identifier consistently. Descriptions may be empty; quote fields containing commas. Keep earlier transactions to establish the baseline.</p></details>
        </section>}
        <footer className="footer"><span>Built by Phyo Thant Kyaw · Java / TypeScript / SQL / AI</span><span>Portfolio prototype · Synthetic data only</span></footer>
      </div>
    </main>
    {tokenDialog && <div className="modal-backdrop" onClick={() => setTokenDialog(false)}><section className="modal" role="dialog" aria-modal="true" aria-labelledby="access-title" onClick={e => e.stopPropagation()}><Icon kind="lock"/><h2 id="access-title">Reviewer access</h2><p>Enter the reviewer token configured by the demo owner. It stays in this page's memory and is cleared on reload.</p><label>Reviewer token<input autoFocus type="password" autoComplete="off" value={token} onChange={e => setToken(e.target.value)}/></label>{!meta?.reviewerConfigured && <p className="helper">Saving reviews is disabled until the owner configures reviewer access.</p>}<div className="modal-actions"><button className="button secondary" onClick={() => { setToken(''); setTokenDialog(false); }}>Clear access</button><button className="button primary" onClick={() => setTokenDialog(false)}>Done</button></div></section></div>}
  </div>;
}
