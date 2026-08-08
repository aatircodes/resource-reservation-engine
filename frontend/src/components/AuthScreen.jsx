import { useState } from 'react';
import { login, register } from '../api/client';

function AuthScreen({ onLoginSuccess }) {
  const [mode, setMode] = useState('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  function resetFields() {
    setEmail('');
    setPassword('');
    setConfirmPassword('');
    setError('');
  }

  function switchMode(nextMode) {
    setMode(nextMode);
    resetFields();
    setNotice('');
  }

  async function handleLogin(e) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const response = await login({ email, password });
      onLoginSuccess(response);
    } catch (err) {
      setError(err.fields ? Object.values(err.fields)[0] : err.message);
    } finally {
      setLoading(false);
    }
  }

  async function handleRegister(e) {
    e.preventDefault();
    setError('');

    if (password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    setLoading(true);
    try {
      await register({ email, password });
      switchMode('login');
      setNotice('Account created — sign in below.');
    } catch (err) {
      setError(err.fields ? Object.values(err.fields)[0] : err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="shell">
      <div className="auth-wrap">
        {mode === 'login' ? (
          <div className="auth-card">
            <p className="auth-title">Sign in</p>
            <p className="auth-sub">Book resources and check your reservations.</p>

            {notice && <p className="status-line ok">{notice}</p>}
            {error && <p className="status-line full">{error}</p>}

            <form onSubmit={handleLogin}>
              <div className="auth-field">
                <label className="field-label">Email</label>
                <input
                  type="email"
                  placeholder="name@company.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>
              <div className="auth-field">
                <label className="field-label">Password</label>
                <input
                  type="password"
                  placeholder="Enter your password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </div>

              <button type="submit" className="full-width" disabled={loading}>
                {loading ? 'Signing in…' : 'Sign in'}
              </button>
            </form>

            <p className="auth-switch">
              Don't have an account?{' '}
              <a href="#" onClick={(e) => { e.preventDefault(); switchMode('register'); }}>
                Register
              </a>
            </p>
          </div>
        ) : (
          <div className="auth-card">
            <p className="auth-title">Create account</p>
            <p className="auth-sub">Registers a standard user account.</p>

            {error && <p className="status-line full">{error}</p>}

            <form onSubmit={handleRegister}>
              <div className="auth-field">
                <label className="field-label">Email</label>
                <input
                  type="email"
                  placeholder="name@company.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>
              <div className="auth-field">
                <label className="field-label">Password</label>
                <input
                  type="password"
                  placeholder="At least 8 characters"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </div>
              <div className="auth-field">
                <label className="field-label">Confirm password</label>
                <input
                  type="password"
                  placeholder="Re-enter your password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  required
                />
              </div>

              <button type="submit" className="full-width" disabled={loading}>
                {loading ? 'Creating account…' : 'Create account'}
              </button>
            </form>

            <p className="auth-switch">
              Already have an account?{' '}
              <a href="#" onClick={(e) => { e.preventDefault(); switchMode('login'); }}>
                Sign in
              </a>
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

export default AuthScreen;