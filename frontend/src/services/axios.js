import axios from 'axios';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const instance = axios.create({
    baseURL: API_URL,
    headers: { 'Content-Type': 'application/json' },
});

export const clearSession = () => {
    sessionStorage.removeItem('accessToken');
    sessionStorage.removeItem('role');
    localStorage.removeItem('user');
    localStorage.removeItem('account');
    window.dispatchEvent(new Event('session-expired'));
};

instance.interceptors.request.use((config) => {
    const token = sessionStorage.getItem('accessToken');
    if (token && config.url !== '/api/accounts/login') {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// An old response must not invalidate a newer login. Never retry a money request.
instance.interceptors.response.use(
    (response) => response.data,
    (error) => {
        const token = sessionStorage.getItem('accessToken');
        if (error.response?.status === 401 && token && error.config?.headers?.Authorization === `Bearer ${token}`) {
            clearSession();
        }
        error.message = error.response?.status === 403
            ? 'Bạn không có quyền thực hiện thao tác này.'
            : error.response?.data?.message || error.message || 'Something went wrong';
        return Promise.reject(error);
    }
);

export default instance;
