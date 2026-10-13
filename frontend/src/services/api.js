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

// Authentication & OTP APIs (Gmail OTP, Dev Quick-Switch, Current User)
export const authService = {
  sendOtp: async (email) => {
    return api.post('/auth/send-otp', { email });
  },

  verifyOtp: async (email, otp) => {
    return api.post('/auth/verify-otp', { email, otp });
  },

  login: async (credentials) => {
    return api.post('/auth/login', credentials);
  },

  getMe: async () => {
    return api.get('/auth/me');
  },
};

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

  getActivity: async (email) => {
    const params = email ? { email } : {};
    return api.get('/user/activity', { params });
  },
};

// P02, P03: Policy Search, Filter, Page & Sort APIs
export const policyService = {
  searchPolicies: async (params = {}) => {
    return api.get('/policies', { params });
  },

  getPolicyById: async (policyId) => {
    return api.get(`/policies/${policyId}`);
  },

  createPolicy: async (policyData) => {
    return api.post('/policies', policyData);
  },

  updatePolicy: async (policyNumber, policyData) => {
    return api.put(`/policies/${policyNumber}`, policyData);
  },

  deletePolicy: async (policyNumber) => {
    return api.delete(`/policies/${policyNumber}`);
  },

  // P06, P07: Status Transition API
  transitionStatus: async (policyNumber, transitionData) => {
    return api.post(`/policies/${policyNumber}/status-transitions`, transitionData);
  },

  // P08 (F06): Endorsement Engine APIs
  applyEndorsement: async (policyNumber, endorsementData) => {
    return api.post(`/policies/${policyNumber}/endorsements`, endorsementData);
  },

  getVersions: async (policyNumber) => {
    return api.get(`/policies/${policyNumber}/versions`);
  },

  getVersion: async (policyNumber, version) => {
    return api.get(`/policies/${policyNumber}/versions/${version}`);
  },

  compareVersions: async (policyNumber, v1, v2) => {
    return api.get(`/policies/${policyNumber}/versions/compare`, {
      params: { v1, v2 },
    });
  },

  // P09 (F07): Transaction Ledger History API
  getHistory: async (policyNumber) => {
    return api.get(`/policies/${policyNumber}/history`);
  },
};

// P04: Nested Location in Policy APIs
export const policyLocationService = {
  getLocations: async (policyId) => {
    return api.get(`/policies/${policyId}/locations`);
  },

  getLocationById: async (policyId, locationId) => {
    return api.get(`/policies/${policyId}/locations/${locationId}`);
  },

  addLocation: async (policyId, locationData) => {
    return api.post(`/policies/${policyId}/locations`, locationData);
  },

  updateLocation: async (policyId, locationId, locationData) => {
    return api.put(`/policies/${policyId}/locations/${locationId}`, locationData);
  },

  removeLocation: async (policyId, locationId) => {
    return api.delete(`/policies/${policyId}/locations/${locationId}`);
  },
};

// P05: Nested Coverage in Location APIs
export const policyCoverageService = {
  getCoverages: async (policyId, locationId) => {
    return api.get(`/policies/${policyId}/locations/${locationId}/coverages`);
  },

  getCoverageByCode: async (policyId, locationId, coverageCode) => {
    return api.get(`/policies/${policyId}/locations/${locationId}/coverages/${coverageCode}`);
  },

  addCoverage: async (policyId, locationId, coverageData) => {
    return api.post(`/policies/${policyId}/locations/${locationId}/coverages`, coverageData);
  },

  updateCoverage: async (policyId, locationId, coverageCode, coverageData) => {
    return api.put(`/policies/${policyId}/locations/${locationId}/coverages/${coverageCode}`, coverageData);
  },

  removeCoverage: async (policyId, locationId, coverageCode) => {
    return api.delete(`/policies/${policyId}/locations/${locationId}/coverages/${coverageCode}`);
  },
};

// P08: Endorsement Engine & History Versioning APIs
export const endorsementService = {
  createEndorsement: async (policyNumber, endorsementData) => {
    return api.post(`/policies/${policyNumber}/endorsements`, endorsementData);
  },

  getVersions: async (policyNumber) => {
    return api.get(`/policies/${policyNumber}/versions`);
  },

  getVersion: async (policyNumber, version) => {
    return api.get(`/policies/${policyNumber}/versions/${version}`);
  },

  getHistory: async (policyNumber) => {
    return api.get(`/policies/${policyNumber}/history`);
  },

  compareVersions: async (policyNumber, v1, v2) => {
    return api.get(`/policies/${policyNumber}/versions/compare`, {
      params: { v1, v2 },
    });
  },
};

// P10: Document Generation Engine (PDF Certificate & Policy Summary APIs)
export const documentService = {
  getDocuments: async (policyNumber) => {
    return api.get(`/policies/${policyNumber}/documents`);
  },

  downloadCertificate: async (policyNumber, version) => {
    const params = version ? { version } : {};
    const response = await axios.get(`/policies/${policyNumber}/documents/certificate`, {
      params,
      responseType: 'blob',
    });
    const blob = new Blob([response.data], { type: 'application/pdf' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `Certificate_${policyNumber}${version ? '_v' + version : ''}.pdf`);
    document.body.appendChild(link);
    link.click();
    link.parentNode.removeChild(link);
    window.URL.revokeObjectURL(url);
  },

  downloadSummary: async (policyNumber, version) => {
    const params = version ? { version } : {};
    const response = await axios.get(`/policies/${policyNumber}/documents/summary`, {
      params,
      responseType: 'blob',
    });
    const blob = new Blob([response.data], { type: 'application/pdf' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `Policy_Summary_${policyNumber}${version ? '_v' + version : ''}.pdf`);
    document.body.appendChild(link);
    link.click();
    link.parentNode.removeChild(link);
    window.URL.revokeObjectURL(url);
  },
};

export default api;
