import axios from './axios';

const config = { baseURL: import.meta.env.VITE_LOAN_API_URL || import.meta.env.VITE_API_URL || 'http://localhost:8081' };

export const applyForLoan = (data) => axios.post('/api/loans/apply', data, config);
export const getLoansByAccount = (id) => axios.get(`/api/loans/account/${id}`, config);
export const approveLoan = (id) => axios.post(`/api/loans/${id}/approve`, undefined, config);
export const makeRepayment = (id, amount) => axios.post(`/api/loans/${id}/repay`, { amount }, config);
