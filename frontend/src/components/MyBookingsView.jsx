import { useEffect, useState } from 'react';
import { getMyBookings, cancelBooking } from '../api/client';

function MyBookingsView({ token }) {
  const [bookings, setBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [cancellingId, setCancellingId] = useState(null);
  const [rowErrors, setRowErrors] = useState({}); // { [bookingId]: message }

  async function loadBookings() {
    setLoading(true);
    setError('');
    try {
      const data = await getMyBookings(token);
      setBookings(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadBookings();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleCancel(bookingId) {
    setCancellingId(bookingId);
    setRowErrors((prev) => ({ ...prev, [bookingId]: null }));
    try {
      await cancelBooking(token, bookingId);
      await loadBookings();
    } catch (err) {
      setRowErrors((prev) => ({ ...prev, [bookingId]: err.message }));
    } finally {
      setCancellingId(null);
    }
  }

  if (loading) {
    return <p className="page-sub">Loading your bookings…</p>;
  }

  if (error) {
    return <p className="status-line full">{error}</p>;
  }

  if (bookings.length === 0) {
    return <p className="page-sub">You don't have any bookings yet.</p>;
  }

  return (
    <div>
      <div className="section-label">
        <span style={{ fontSize: '16px', fontWeight: 600, color: 'var(--text)' }}>My bookings</span>
      </div>

      <div className="list-card">
        {bookings.map((booking) => (
          <div className="list-row" key={booking.id}>
            <div className="list-row-left">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path strokeLinecap="round" strokeLinejoin="round" d="M5 21V5a2 2 0 012-2h6a2 2 0 012 2v16M9 9h.01M9 13h.01M9 17h.01M15 21v-6a2 2 0 00-2-2H9" />
              </svg>
              <div>
                <p className="list-row-name">{booking.resourceName}</p>
                <p className="list-row-meta">
                  {booking.status === 'WAITLISTED'
                    ? `Waitlist position ${booking.waitlistPosition}`
                    : new Date(booking.createdAt).toLocaleString()}
                </p>
                {rowErrors[booking.id] && (
                  <p className="status-line full">{rowErrors[booking.id]}</p>
                )}
              </div>
            </div>
            <div className="row-actions">
              <span
                className={`badge ${
                  booking.status === 'CONFIRMED'
                    ? 'confirmed'
                    : booking.status === 'CANCELLED'
                    ? 'cancelled'
                    : 'waitlisted'
                }`}
              >
                {booking.status === 'CONFIRMED'
                  ? 'Confirmed'
                  : booking.status === 'CANCELLED'
                  ? 'Cancelled'
                  : 'Waitlisted'}
              </span>
              {booking.status !== 'CANCELLED' && (
                <button
                  className="icon-btn"
                  aria-label="Cancel booking"
                  onClick={() => handleCancel(booking.id)}
                  disabled={cancellingId === booking.id}
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M18 6L6 18M6 6l12 12" />
                  </svg>
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default MyBookingsView;