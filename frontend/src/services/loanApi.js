import axios from 'axios';

const API_BASE_URL = 'http://localhost:8081';

// Loan API functions
export const applyForLoan = async (loanData) => {
    const response = await axios.post(`${API_BASE_URL}/api/loans/apply`, loanData);
    return response.data;
};

export const getLoansByAccount = async (accountId) => {
    const response = await axios.get(`${API_BASE_URL}/api/loans/account/${accountId}`);
    return response.data;
};

export const approveLoan = async (loanId) => {
    const response = await axios.post(`${API_BASE_URL}/api/loans/${loanId}/approve`);
    return response.data;
};

export const makeRepayment = async (loanId, amount) => {
    const response = await axios.post(`${API_BASE_URL}/api/loans/${loanId}/repay`, { amount });
    return response.data;
};
