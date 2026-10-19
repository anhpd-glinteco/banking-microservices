import axios from './axios';

// --- Generic Helpers ---
export const get = (url, config = {}) => axios.get(url, config);
export const post = (url, data, config = {}) => axios.post(url, data, config);

// --- Specific API Methods ---

// Account APIs
// Account APIs
export const fetchAccount = (id) => get(`/api/accounts/${id}`);
export const fetchAccountByNumber = (accountNumber) => get(`/api/accounts/number/${accountNumber}`);
export const login = (data) => post('/api/accounts/login', data);
export const createAccount = (data) => post('/api/accounts', data);
export const createUser = (data) => post('/api/accounts/users', data);

// OTP & Advanced Register
export const generateOtp = (email) => post(`/api/accounts/otp/generate?email=${email}`);
export const registerWithOtp = (data) => post('/api/accounts/users/register-with-otp', data);
export const checkUserExists = (email, phone, identificationNumber) => {
    const params = new URLSearchParams();
    if (email) params.append('email', email);
    if (phone) params.append('phone', phone);
    if (identificationNumber) params.append('identificationNumber', identificationNumber);
    return get(`/api/accounts/check-exists?${params.toString()}`);
};

// Transfer APIs
export const initiateTransfer = (data) => post('/api/transfers', data);
export const fetchTransferStatus = (id) => get(`/api/transfers/${id}/status`);

// History APIs
export const fetchHistory = (accountId) => get(`/api/history/${accountId}`);

export default {
    get,
    post,
    fetchAccount,
    fetchAccountByNumber,
    login,
    createAccount,
    createUser,
    generateOtp,
    registerWithOtp,
    initiateTransfer,
    fetchTransferStatus,
    fetchHistory
};
