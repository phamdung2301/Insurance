import axios from 'axios';

// Create central Axios instance
const api = axios.create({
  baseURL: '', // Uses Vite proxy config (/user, /api)
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
});

// Request Interceptor: Attach JWT Token and custom headers
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('jwt_token');
    const userEmail = localStorage.getItem('user_email');

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // Attach email as header if available for seamless backend support
    if (userEmail) {
      config.headers['X-User-Email'] = userEmail;
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response Interceptor: Global Error and 401 Unauthorized handling
api.interceptors.response.use(
  (response) => {
    // Return backend payload directly if success
    return response.data;
  },
  (error) => {
    const status = error.response ? error.response.status : null;

    if (status === 401) {
      console.warn('Session expired or unauthorized. Logging out...');
      localStorage.removeItem('jwt_token');
      localStorage.removeItem('user_email');
      localStorage.removeItem('user_data');
      
      // Dispatch custom event for app-wide auth synchronization
      window.dispatchEvent(new CustomEvent('auth:unauthorized'));
    }

    // Standardize error message extraction
    const serverMessage = error.response?.data?.message;
    const validationErrors = error.response?.data?.data;
    
    let errorMessage = serverMessage || error.message || 'An unexpected error occurred';
    
    if (validationErrors && typeof validationErrors === 'object') {
      const fieldErrors = Object.values(validationErrors).join(', ');
      if (fieldErrors) {
        errorMessage = `${errorMessage}: ${fieldErrors}`;
      }
    }

    return Promise.reject(new Error(errorMessage));
  }
);

// User Profile & Password APIs
export const userService = {
  getProfile: async (email) => {
    const params = email ? { email } : {};
    return api.get('/user/profile', { params });
  },

  updateProfile: async (profileData, email) => {
    const params = email ? { email } : {};
    return api.put('/user/profile', profileData, { params });
  },

  changePassword: async (passwordData, email) => {
    const params = email ? { email } : {};
    return api.post('/user/change-password', passwordData, { params });
  },
};

export default api;
