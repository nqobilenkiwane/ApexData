import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import AuthGate from './AuthGate.jsx'
import { ClerkProvider, Show, SignIn } from '@clerk/react'
import './index.css'

const PUBLISHABLE_KEY = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY

if (!PUBLISHABLE_KEY) {
  throw new Error("Missing Publishable Key")
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ClerkProvider publishableKey={PUBLISHABLE_KEY}>

      {/* If the user is NOT logged in, show the Clerk Login UI */}
      <Show when="signed-out">
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', backgroundColor: '#000' }}>
           <SignIn />
        </div>
      </Show>

      {/* If the user IS logged in, pass them to the Whitelist Gate */}
      <Show when="signed-in">
        <AuthGate>
          <App />
        </AuthGate>
      </Show>

    </ClerkProvider>
  </React.StrictMode>,
)