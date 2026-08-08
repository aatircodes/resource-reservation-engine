import { useState } from "react";
import AuthScreen from './components/AuthScreen';
import ResourcesView from './components/ResourcesView';
import MyBookingsView from './components/MyBookingsView';
import ConcurrencyDemoView from './components/ConcurrencyDemoView';

function App() {
  const [auth, setAuth] = useState(null); // { token, email, role } | null
  const [activeTab, setActiveTab] = useState("resources");

  function handleLoginSuccess(loginResponse) {
    setAuth(loginResponse);
  }

  function handleLogout() {
    setAuth(null);
    setActiveTab("resources");
  }

  if (!auth) {
    return <AuthScreen onLoginSuccess={handleLoginSuccess} />;
  }

  return (
    <div>
      <div className="navbar">
        <div className="nav-left">
          <span className="brand">Reservations</span>

          <a
            className={`nav-link${activeTab === "resources" ? " active" : ""}`}
            onClick={() => setActiveTab("resources")}
          >
            Resources
          </a>

          <a
            className={`nav-link${activeTab === "my-bookings" ? " active" : ""}`}
            onClick={() => setActiveTab("my-bookings")}
          >
            My bookings
          </a>

          <a
            className={`nav-link${activeTab === "demo" ? " active" : ""}`}
            onClick={() => setActiveTab("demo")}
          >
            <svg
              width="14"
              height="14"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"
              />
            </svg>
            Demo
          </a>
        </div>
        <div className="user-chip">
          <span className="user-email">{auth.email}</span>
          <button className="secondary" onClick={handleLogout}>
            Log out
          </button>
        </div>
      </div>

     <div className="page-content">
        {activeTab === 'resources' && <ResourcesView token={auth.token} />}
        {activeTab === 'my-bookings' && <MyBookingsView token={auth.token} />}
        {activeTab === 'demo' && <ConcurrencyDemoView token={auth.token} />}
      </div>
    </div>
  );
}

export default App;
