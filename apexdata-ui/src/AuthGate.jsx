import { useUser, SignOutButton } from "@clerk/react";

// Add the emails of paying members here
const WHITELIST_EMAILS = [
  "nqobilenkiwane01@gmail.com",
  "sibongumusasimelane4@gmail.com"
];

function AuthGate({ children }) {
  const { isLoaded, user } = useUser();

  if (!isLoaded) return <div style={styles.loading}>Loading secure environment...</div>;

  const userEmail = user?.primaryEmailAddress?.emailAddress;
  const isWhitelisted = WHITELIST_EMAILS.includes(userEmail);

  // If approved, render the ApexData Dashboard
  if (isWhitelisted) {
    return (
      <>
         <div style={styles.navBar}>
            <span style={{ color: '#888', fontSize: '0.8rem' }}>Logged in as {userEmail}</span>
            <SignOutButton>
                <button style={styles.logoutBtn}>Sign Out</button>
            </SignOutButton>
         </div>
         {children}
      </>
    );
  }

  // If NOT approved, show the pending screen
  return (
    <div style={styles.unauthorized}>
       <h2 style={{ color: '#ff3366' }}>Access Pending Approval</h2>
       <p>Your email <strong>({userEmail})</strong> has not been whitelisted yet.</p>
       <p style={{ color: '#888', marginTop: '10px' }}>
           If you just made a payment, please allow up to 12 hours for your account to be activated.
       </p>
       <div style={{ marginTop: '30px' }}>
         <SignOutButton>
            <button style={styles.logoutBtn}>Sign Out & Try Again Later</button>
         </SignOutButton>
       </div>
    </div>
  );
}

const styles = {
  loading: { backgroundColor: '#000000', height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#2563EB' },
  navBar: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 20px', backgroundColor: '#111', borderBottom: '1px solid #222' },
  logoutBtn: { backgroundColor: '#222', color: '#FFF', border: '1px solid #444', padding: '5px 15px', borderRadius: '4px', cursor: 'pointer' },
  unauthorized: { backgroundColor: '#000', color: '#FFF', height: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', textAlign: 'center', fontFamily: 'sans-serif' }
};

export default AuthGate;