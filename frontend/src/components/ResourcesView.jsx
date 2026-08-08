import { useEffect, useState } from 'react';
import { getResources, createBooking } from '../api/client';

function ResourcesView({ token }) {
  const [resources, setResources] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [bookingId, setBookingId] = useState(null); // resourceId currently submitting
  const [results, setResults] = useState({}); // { [resourceId]: { status, waitlistPosition } | { error } }

  async function loadResources() {
    setLoading(true);
    setError('');
    try {
      const data = await getResources(token);
      setResources(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadResources();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleBook(resourceId) {
    setBookingId(resourceId);
    setResults((prev) => ({ ...prev, [resourceId]: null }));
    try {
      const booking = await createBooking(token, resourceId, crypto.randomUUID());
      setResults((prev) => ({
        ...prev,
        [resourceId]: { status: booking.status, waitlistPosition: booking.waitlistPosition },
      }));
      await loadResources();
    } catch (err) {
      setResults((prev) => ({ ...prev, [resourceId]: { error: err.message } }));
    } finally {
      setBookingId(null);
    }
  }

  if (loading) {
    return <p className="page-sub">Loading resources…</p>;
  }

  if (error) {
    return <p className="status-line full">{error}</p>;
  }

  return (
    <div>
      <div className="section-label">
        <span>{resources.length} resources</span>
      </div>

      <div className="grid">
        {resources.map((resource) => {
          const full = resource.availableSlots <= 0;
          const result = results[resource.id];
          const isBooking = bookingId === resource.id;

          return (
            <div className="card resource-card" key={resource.id}>
              <div className="icon-row">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M5 21V5a2 2 0 012-2h6a2 2 0 012 2v16M9 9h.01M9 13h.01M9 17h.01M15 21v-6a2 2 0 00-2-2H9" />
                </svg>
                <p className="name">{resource.name}</p>
              </div>
              <p className="capacity">Capacity {resource.capacity}</p>
              <p className={`status-line ${full ? 'full' : 'ok'}`}>
                {full ? 'Full · joins waitlist' : `${resource.availableSlots} ${resource.availableSlots === 1 ? 'slot' : 'slots'} left`}
              </p>

              <button
                className={`full-width${full ? ' secondary' : ''}`}
                onClick={() => handleBook(resource.id)}
                disabled={isBooking}
              >
                {isBooking ? 'Submitting…' : full ? 'Join waitlist' : 'Book'}
              </button>

              {result?.error && <p className="status-line full">{result.error}</p>}
              {result && !result.error && (
                <p className={`status-line ${result.status === 'CONFIRMED' ? 'ok' : 'full'}`}>
                  {result.status === 'CONFIRMED'
                    ? 'Confirmed'
                    : `Waitlisted · #${result.waitlistPosition}`}
                </p>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default ResourcesView;