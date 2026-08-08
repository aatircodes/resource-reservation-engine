const BASE_URL = 'http://localhost:8080';

async function apiFetch(path, { method = 'GET', token, body, headers = {} } = {}) {
  const finalHeaders = {
    'Content-Type': 'application/json',
    ...headers,
  };

  if (token) {
    finalHeaders.Authorization = `Bearer ${token}`;
  }

  let response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: finalHeaders,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw { status: 0, message: 'Could not reach the server. Check your connection.' };
  }

  let data = null;
  const text = await response.text();
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }

  if (!response.ok) {
    throw {
      status: response.status,
      message: data?.error || 'Something went wrong.',
      fields: data?.fields,
      reason: data?.reason,
    };
  }

  return data;
}

export function login(credentials) {
  return apiFetch('/api/auth/login', { method: 'POST', body: credentials });
}

export function register(credentials) {
  return apiFetch('/api/auth/register', { method: 'POST', body: credentials });
}

export function getResources(token) {
  return apiFetch('/api/resources', { token });
}

export function createBooking(token, resourceId, idempotencyKey) {
  return apiFetch('/api/bookings', {
    method: 'POST',
    token,
    body: { resourceId },
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}

export function getMyBookings(token) {
  return apiFetch('/api/bookings/me', { token });
}

export function cancelBooking(token, bookingId) {
  return apiFetch(`/api/bookings/${bookingId}`, { method: 'DELETE', token });
}