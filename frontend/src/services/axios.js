import axios from 'axios';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const instance = axios.create({
    baseURL: API_URL,
    headers: {
        'Content-Type': 'application/json',
    },
});

// Response interceptor for global error handling
instance.interceptors.response.use(
    (response) => {
        return response.data;
    },
    (error) => {
        const message = error.response?.data?.message || 'Something went wrong';
        // You could convert this to a custom error object if defined in utils
        return Promise.reject(new Error(message));
    }
);

export default instance;
