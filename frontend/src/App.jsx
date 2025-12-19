import { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import toast, { Toaster } from 'react-hot-toast';
import Login from '@/Page/Login/Login';
import Register from '@/Page/Login/Register';
import ForgotPassword from '@/Page/Login/ForgotPassword';
import Home from '@/Page/Home/Home';
import { login, initiateTransfer, fetchAccount, fetchTransferStatus } from '@/services/api';
import '@/styles/global.scss';

function App() {
  const { t } = useTranslation();
  const [user, setUser] = useState(() => {
    const saved = localStorage.getItem('user');
    return saved ? JSON.parse(saved) : null;
  });
  const [account, setAccount] = useState(() => {
    const saved = localStorage.getItem('account');
    return saved ? JSON.parse(saved) : null;
  });

  const [isVerifying, setIsVerifying] = useState(!!user);

  // Persist user and account to localStorage
  useEffect(() => {
    if (user && account) {
      localStorage.setItem('user', JSON.stringify(user));
      localStorage.setItem('account', JSON.stringify(account));
    } else {
      localStorage.removeItem('user');
      localStorage.removeItem('account');
    }
  }, [user, account]);

  // Verify session on mount
  useEffect(() => {
    const verifySession = async () => {
      if (!user) {
        setIsVerifying(false);
        return;
      }

      try {
        // We use the stored user ID (or account ID) to verify
        // Ideally this endpoint should also validate the token cookie/header
        const data = await fetchAccount(account?.id || user.id); // account.id is safer if user.id is not consistent
        setAccount(data);
        // Optional: Update user name if it changed
        if (data.accountName) {
          setUser(prev => ({ ...prev, name: data.accountName, id: data.userId }));
        }
      } catch (error) {
        console.error('Session verification failed', error);
        // Session invalid, log out
        setUser(null);
        setAccount(null);
        localStorage.removeItem('user');
        localStorage.removeItem('account');
        toast.error('Phiên đăng nhập hết hạn');
      } finally {
        setIsVerifying(false);
      }
    };

    verifySession();
  }, []); // Run once on mount

  const handleLogin = async (username, password) => {
    try {
      const data = await login({ username, password });
      // Data is AccountDTO which contains userId. 
      // Ideally we want User details too. 
      // For MVP, AccountDTO has what we need? Account name, Balance.
      // User Name is not in AccountDTO directly (only ID).
      // We might want to fetch User details separately or enrich AccountDTO.
      // Let's assume AccountName is enough or "User {id}".
      // Or update AccountDTO to include fullName?

      const userData = { id: data.userId, name: data.accountName };
      setUser(userData);
      setAccount(data);
      toast.success(t('loginSuccess'));
    } catch (e) {
      console.error(e);
      // Display the actual error message from the server
      toast.error(e.message || t('loginFailed'));
    }
  };

  const handleTransfer = async ({ to, amount, description }) => {
    if (parseFloat(amount) > account.balance) {
      toast.error(t('invalidAmount'));
      return;
    }

    const toastId = toast.loading(t('transferring'));
    try {
      const body = {
        fromAccountNumber: account.accountNumber,
        toAccountNumber: to,
        amount: parseFloat(amount),
        description: description
      }

      const transaction = await initiateTransfer(body);

      // Poll transaction status until it's completed or failed
      const maxAttempts = 30; // 30 seconds max
      let attempts = 0;

      const checkStatus = async () => {
        while (attempts < maxAttempts) {
          attempts++;
          await new Promise(resolve => setTimeout(resolve, 1000)); // Wait 1 second

          try {
            // Use axios wrapper which handles headers/base URL
            const status = await fetchTransferStatus(transaction.id);

            if (status.status === 'SUCCESS') {
              toast.success(t('transferSuccess'), { id: toastId });
              return;
            } else if (status.status === 'FAILED') {
              toast.error(t('transferFailed') + ": " + (status.failureReason || 'Unknown error'), { id: toastId });
              return;
            }
            // If still PROCESSING, continue polling
          } catch (error) {
            console.error('Error checking status:', error);
            // If it's a 4xx or 5xx error, we probably shouldn't retry indefinitely or at all
            // But let's verify if it's an axios error
            if (error.response && (error.response.status === 401 || error.response.status === 403 || error.response.status === 404)) {
                 toast.error(t('transferFailed') + ": Cannot verify status", { id: toastId });
                 return; // Stop polling on auth/not found errors
            }
          }
        }

        // Timeout
        toast.error('Giao dịch đang xử lý. Vui lòng kiểm tra lại sau.', { id: toastId });
      };

      await checkStatus();

    } catch (e) {
      toast.error(t('transferFailed') + ": " + e.message, { id: toastId });
    }
  };

  if (isVerifying) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', flexDirection: 'column' }}>
        <div className="spinner" style={{ border: '4px solid #f3f3f3', borderTop: '4px solid #3498db', borderRadius: '50%', width: '40px', height: '40px', animation: 'spin 1s linear infinite' }}></div>
        <p style={{ marginTop: '20px', color: '#666' }}>{t('pleaseWait')}...</p>
        <style>{`@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }`}</style>
      </div>
    );
  }

  return (
    <BrowserRouter>
      <div className="app">
        <Toaster
          position="top-center"
          reverseOrder={false}
          gutter={12}
          toastOptions={{
            duration: 4000,
            style: {
              background: '#fff',
              color: '#1f2937',
              padding: '18px 24px',
              borderRadius: '16px',
              fontSize: '14px',
              fontWeight: '600',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.15), 0 10px 10px -5px rgba(0, 0, 0, 0.08)',
              maxWidth: '500px',
              border: '2px solid transparent',
              minWidth: '320px',
            },
            success: {
              duration: 3000,
              iconTheme: {
                primary: '#10b981',
                secondary: '#fff',
              },
              style: {
                background: '#fff',
                color: '#1f2937',
                border: '1px solid #e5e7eb',
                fontWeight: '600',
              },
            },
            error: {
              duration: 5000,
              iconTheme: {
                primary: '#ef4444',
                secondary: '#fff',
              },
              style: {
                background: 'linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%)',
                color: '#991b1b',
                border: '2px solid #ef4444',
                fontWeight: '700',
              },
            },
            loading: {
              iconTheme: {
                primary: '#4f46e5',
                secondary: '#fff',
              },
              style: {
                background: '#fff',
                color: '#1f2937',
                border: '1px solid #e5e7eb',
                fontWeight: '600',
              },
            },
          }}
        />

        <Routes>
          <Route
            path="/login"
            element={
              !user ? (
                <Login onLogin={handleLogin} />
              ) : (
                <Navigate to="/dashboard" replace />
              )
            }
          />
          <Route
            path="/register"
            element={
              !user ? (
                <Register />
              ) : (
                <Navigate to="/dashboard" replace />
              )
            }
          />
          <Route
            path="/forgot-password"
            element={
              !user ? (
                <ForgotPassword />
              ) : (
                <Navigate to="/dashboard" replace />
              )
            }
          />
          <Route
            path="/dashboard/*"
            element={
              user ? (
                <Home
                  user={user}
                  account={account}
                  setAccount={setAccount}
                  onLogout={() => {
                    setUser(null);
                    setAccount(null);
                    localStorage.removeItem('user');
                    localStorage.removeItem('account');
                    toast(t('loggedOut'), { icon: '👋' });
                  }}
                  onTransfer={handleTransfer}
                />
              ) : (
                <Navigate to="/login" replace />
              )
            }
          />
          <Route
            path="/"
            element={<Navigate to={user ? "/dashboard" : "/login"} replace />}
          />
        </Routes>
      </div>
    </BrowserRouter>
  );
}

export default App;
