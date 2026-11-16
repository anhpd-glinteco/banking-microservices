import { useState, useEffect } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import toast, { Toaster } from 'react-hot-toast';
import Login from '@/Page/Login/Login';
import Register from '@/Page/Login/Register';
import ForgotPassword from '@/Page/Login/ForgotPassword';
import Home from '@/Page/Home/Home';
import { login, initiateTransfer, fetchAccount, fetchTransferStatus } from '@/services/api';
import { clearSession } from '@/services/axios';
import '@/styles/global.scss';

function App() {
  const { t } = useTranslation();
  const [initialSession] = useState(() => {
    const token = sessionStorage.getItem('accessToken');
    try {
      const savedAccount = JSON.parse(localStorage.getItem('account'));
      const role = sessionStorage.getItem('role');
      if (token && savedAccount?.id && ['USER', 'ADMIN'].includes(role)) {
        return { token, account: savedAccount, user: { id: savedAccount.userId, name: savedAccount.accountName, role } };
      }
    } catch { /* Discard invalid cached account data. */ }
    clearSession();
    return { token: null, account: null, user: null };
  });
  const [user, setUser] = useState(initialSession.user);
  const [account, setAccount] = useState(initialSession.account);
  const [isVerifying, setIsVerifying] = useState(!!initialSession.token);

  useEffect(() => {
    if (user && account && sessionStorage.getItem('accessToken')) {
      localStorage.setItem('user', JSON.stringify(user));
      localStorage.setItem('account', JSON.stringify(account));
    } else {
      localStorage.removeItem('user');
      localStorage.removeItem('account');
    }
  }, [user, account]);

  useEffect(() => {
    const expire = () => {
      setUser(null);
      setAccount(null);
      setIsVerifying(false);
    };
    window.addEventListener('session-expired', expire);
    return () => window.removeEventListener('session-expired', expire);
  }, []);

  useEffect(() => {
    if (!initialSession.token) return;
    let active = true;
    const isCurrent = () => active && sessionStorage.getItem('accessToken') === initialSession.token;
    fetchAccount(initialSession.account.id).then((data) => {
      if (!isCurrent()) return;
      setAccount(data);
      setUser(prev => ({ ...prev, name: data.accountName, id: data.userId }));
    }).catch((error) => {
      if (!isCurrent()) return;
      // A temporary outage or a denial does not log out a valid session.
      toast.error(error.message);
    }).finally(() => {
      if (isCurrent()) setIsVerifying(false);
    });
    return () => { active = false; };
  }, [initialSession]);

  const handleLogin = async (username, password) => {
    try {
      const data = await login({ username, password });
      if (!data.accessToken || data.tokenType !== 'Bearer' || !['USER', 'ADMIN'].includes(data.role) || !data.account?.id) {
        throw new Error(t('loginFailed'));
      }
      sessionStorage.setItem('accessToken', data.accessToken);
      sessionStorage.setItem('role', data.role);
      setUser({ id: data.account.userId, name: data.account.accountName, role: data.role });
      setAccount(data.account);
      setIsVerifying(false);
      toast.success(t('loginSuccess'));
    } catch (e) {
      toast.error(e.message || t('loginFailed'));
    }
  };

  const handleTransfer = async ({ to, amount, description }) => {
    if (!Number.isFinite(Number(amount)) || Number(amount) <= 0 || Number(amount) > account.balance) {
      toast.error(t('invalidAmount'));
      return false;
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
              try {
                setAccount(await fetchAccount(account.id));
              } catch {
                toast.success('Giao dịch thành công. Vui lòng tải lại số dư.', { id: toastId });
                return true;
              }
              toast.success(t('transferSuccess'), { id: toastId });
              return true;
            } else if (status.status === 'FAILED') {
              toast.error(t('transferFailed') + ": " + (status.failureReason || 'Unknown error'), { id: toastId });
              return false;
            }
            // If still PROCESSING, continue polling
          } catch (error) {
            console.error('Error checking status:', error);
            // If it's a 4xx or 5xx error, we probably shouldn't retry indefinitely or at all
            // But let's verify if it's an axios error
            if (error.response && (error.response.status === 401 || error.response.status === 403 || error.response.status === 404)) {
                 toast.error(error.response.status === 403 ? error.message : 'Không thể xác minh trạng thái giao dịch. Vui lòng kiểm tra lại sau.', { id: toastId });
                 return false;
            }
          }
        }

        // Timeout
        toast.error('Giao dịch đang xử lý. Vui lòng kiểm tra lại sau.', { id: toastId });
        return false;
      };

      return await checkStatus();

    } catch (e) {
      toast.error(t('transferFailed') + ": " + e.message, { id: toastId });
      return false;
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
                    clearSession();
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
