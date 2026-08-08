import { useEffect, useState } from 'react';
import { getResources, login, createBooking } from '../api/client';

const DEMO_EMAILS = Array.from({ length: 8 }, (_, i) => `demo.user${i + 1}@test.com`);
// Hardcoded intentionally — these are throwaway seeded demo accounts with no real data, not a security boundary
const DEMO_PASSWORD = 'Demo1234';

function ConcurrencyDemoView({ token }) {
  const [resources, setResources] = useState([]);
  const [selectedResourceId, setSelectedResourceId] = useState('');
  const [running, setRunning] = useState(false);
  const [results, setResults] = useState([]);
  const [error, setError] = useState('');

  useEffect(() => {
    async function loadResources() {
      try {
        const data = await getResources(token);
        setResources(data);
        if (data.length > 0) {
          setSelectedResourceId(data[0].id);
        }
      } catch (err) {
        setError(err.message);
      }
    }
    loadResources();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleFire() {
    setRunning(true);
    setError('');
    setResults([]);

    try {
      const logins = await Promise.all(
        DEMO_EMAILS.map((email) => login({ email, password: DEMO_PASSWORD }))
      );

      const attempts = await Promise.all(
        logins.map(async (session) => {
          const start = performance.now();
          try {
            const booking = await createBooking(
              session.token,
              Number(selectedResourceId),
              crypto.randomUUID()
            );
            const elapsed = performance.now() - start;
            return {
              email: session.email,
              elapsed,
              status: booking.status,
              waitlistPosition: booking.waitlistPosition,
            };
          } catch (err) {
            const elapsed = performance.now() - start;
            return { email: session.email, elapsed, error: err.message };
          }
        })
      );

      setResults(attempts);
    } catch (err) {
      setError(err.message);
    } finally {
      setRunning(false);
    }
  }

  const confirmedCount = results.filter((r) => r.status === 'CONFIRMED').length;
  const waitlistedCount = results.filter((r) => r.status === 'WAITLISTED').length;
  const selectedResource = resources.find((r) => r.id === Number(selectedResourceId));
  const overbookedCount =
    selectedResource && confirmedCount > selectedResource.capacity
      ? confirmedCount - selectedResource.capacity
      : 0;

  return (
    <div>
      <div className="demo-banner">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <path strokeLinecap="round" strokeLinejoin="round" d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
        </svg>
        <span style={{ fontSize: '16px', fontWeight: 600 }}>Concurrency demo</span>
        <span className="badge mode" style={{ marginLeft: '4px' }}>Demo mode</span>
      </div>
      <p className="page-sub">
        Fires simultaneous booking requests from 8 seeded demo users against the selected resource
        — the same race <code className="inline">ConcurrencyTest.java</code> verifies at the backend.
      </p>

      <div className="demo-config">
        <div>
          <label className="field-label">Target resource</label>
          <select
            value={selectedResourceId}
            onChange={(e) => setSelectedResourceId(e.target.value)}
            disabled={running}
          >
            {resources.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name} · capacity {r.capacity}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="field-label">Demo users</label>
          <p className="value">8 seeded accounts</p>
        </div>
      </div>

      {error && <p className="status-line full">{error}</p>}

      <button
        className="full-width"
        style={{ marginBottom: '20px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
        onClick={handleFire}
        disabled={running || !selectedResourceId}
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor">
          <path d="M8 5v14l11-7z" />
        </svg>
        {running ? 'Firing…' : 'Fire simultaneous requests'}
      </button>

      {results.length > 0 && (
        <>
          <div className="section-label">
            <span>Results</span>
          </div>

          <div className="list-card">
            {results.map((r) => (
              <div className="list-row" key={r.email}>
                <span className="list-row-name">{r.email}</span>
                <div className="row-actions">
                  <span className="result-time">{r.elapsed.toFixed(0)}ms</span>
                  {r.error ? (
                    <span className="status-line full">{r.error}</span>
                  ) : (
                    <span className={`badge ${r.status === 'CONFIRMED' ? 'confirmed' : 'waitlisted'}`}>
                      {r.status === 'CONFIRMED' ? 'Confirmed' : 'Waitlisted'}
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>

          <div className="summary-bar">
            <div className="summary-item">
              <div className="label">Confirmed</div>
              <div className="value">{confirmedCount}</div>
            </div>
            <div className="summary-item">
              <div className="label">Waitlisted</div>
              <div className="value">{waitlistedCount}</div>
            </div>
            <div className="summary-item">
              <div className="label">Overbooked</div>
              <div className={`value ${overbookedCount === 0 ? 'zero' : ''}`}>{overbookedCount}</div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

export default ConcurrencyDemoView;