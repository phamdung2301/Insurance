import axios from 'axios';

// Central Axios instance — uses Vite proxy (see vite.config.js)
const api = axios.create({
  baseURL: '',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
});

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('itoken');
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
  },
  (error) => Promise.reject(error)
);

api.interceptors.response.use(
  (response) => response.data, // backend envelope: { success, message, data }
  (error) => {
    const status = error.response?.status ?? null;
    if (status === 401) {
      localStorage.removeItem('itoken');
      localStorage.removeItem('iuser');
      window.dispatchEvent(new CustomEvent('auth:unauthorized'));
    }
    const serverMessage = error.response?.data?.message;
    const validationErrors = error.response?.data?.data;
    let message = serverMessage || error.message || 'Đã xảy ra lỗi không mong đợi';
    if (validationErrors && typeof validationErrors === 'object') {
      const fields = Object.values(validationErrors).join(', ');
      if (fields) message = `${message}: ${fields}`;
    }
    const err = new Error(message);
    err.status = status;
    return Promise.reject(err);
  }
);

/** Lấy JWT + user từ AuthResponse — backend trả user fields phẳng (email, fullName, roles). */
export function extractAuth(envelopeData) {
  const d = envelopeData || {};
  const token = d.token ?? d.accessToken ?? null;
  let user = d.user ?? null;
  if (!user && d.email) {
    user = { email: d.email, fullName: d.fullName, roles: d.roles ?? [] };
  }
  return { token, user };
}

// ---------------------------------------------------------------- Auth
export const authService = {
  sendOtp: (email) => api.post('/auth/send-otp', { email }),
  verifyOtp: (email, otp) => api.post('/auth/verify-otp', { email, otp }),
  devLogin: (email, fullName, role) => api.post('/auth/login', { email, fullName, role }),
  getMe: () => api.get('/auth/me'),
};

// ---------------------------------------------------------------- User
export const userService = {
  getProfile: () => api.get('/user/profile'),
  updateProfile: (data) => api.put('/user/profile', data),
  changePassword: (data) => api.post('/user/change-password', data),
};

// ---------------------------------------------------------------- Policy (SRS §6)
export const policyService = {
  searchPolicies: (params = {}) => api.get('/policies', { params }),
  getPolicyByNumber: (policyNumber) => api.get(`/policies/${policyNumber}`),
  createPolicy: (data) => api.post('/policies', data),
  updatePolicy: (policyNumber, data) => api.put(`/policies/${policyNumber}`, data),
  deletePolicy: (policyNumber) => api.delete(`/policies/${policyNumber}`),
  /** Chuyển trạng thái vòng đời — backend: POST /policies/{n}/status-transitions */
  transition: (policyNumber, toStatus, extra = {}) =>
    api.post(`/policies/${policyNumber}/status-transitions`, { toStatus, ...extra }),
  /** Yêu cầu điều chỉnh (endorsement) — backend: POST /policies/{n}/endorsements */
  endorse: (policyNumber, payload) => api.post(`/policies/${policyNumber}/endorsements`, payload),
  getHistory: (policyNumber) => api.get(`/policies/${policyNumber}/history`),
  getVersions: (policyNumber) => api.get(`/policies/${policyNumber}/versions`),
  getVersion: (policyNumber, v) => api.get(`/policies/${policyNumber}/versions/${v}`),
};

// ---------------------------------------------------------------- Locations (nested)
export const policyLocationService = {
  getLocations: (policyNumber) => api.get(`/policies/${policyNumber}/locations`),
  getLocationById: (policyNumber, locationId) =>
    api.get(`/policies/${policyNumber}/locations/${locationId}`),
  addLocation: (policyNumber, data) => api.post(`/policies/${policyNumber}/locations`, data),
  updateLocation: (policyNumber, locationId, data) =>
    api.put(`/policies/${policyNumber}/locations/${locationId}`, data),
  removeLocation: (policyNumber, locationId) =>
    api.delete(`/policies/${policyNumber}/locations/${locationId}`),
};

// ---------------------------------------------------------------- Coverages (nested, keyed by coverageCode)
export const policyCoverageService = {
  getCoverages: (policyNumber, locationId) =>
    api.get(`/policies/${policyNumber}/locations/${locationId}/coverages`),
  addCoverage: (policyNumber, locationId, data) =>
    api.post(`/policies/${policyNumber}/locations/${locationId}/coverages`, data),
  updateCoverage: (policyNumber, locationId, coverageCode, data) =>
    api.put(`/policies/${policyNumber}/locations/${locationId}/coverages/${coverageCode}`, data),
  removeCoverage: (policyNumber, locationId, coverageCode) =>
    api.delete(`/policies/${policyNumber}/locations/${locationId}/coverages/${coverageCode}`),
};

// ---------------------------------------------------------------- Excel / pricing (SRS FR-CALC)
export const excelService = {
  downloadTemplate: () =>
    api.get('/excel/template', { responseType: 'blob' }),
  importRates: (file) => {
    const fd = new FormData();
    fd.append('file', file);
    return api.post('/excel/import-rates', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  calculate: (payload) => api.post('/excel/calculate', payload),
  downloadBreakdown: (policyNumber) =>
    api.get(`/excel/policy/${policyNumber}/download`, { responseType: 'blob' }),
};

// ---------------------------------------------------------------- Reports (SRS FR-REP)
export const reportService = {
  countByStatus: () => api.get('/reports/policy-count-by-status'),
  premiumByStatus: () => api.get('/reports/total-premium-by-status'),
  topPolicies: () => api.get('/reports/top-premium-policies'),
  premiumByMonth: () => api.get('/reports/premium-by-effective-month'),
};

// ---------------------------------------------------------------- Benchmark (SRS FR-BENCH)
export const benchmarkService = {
  generate50k: () => api.post('/benchmark/generate-50k'),
  run: () => api.get('/benchmark/run'),
};

/** Tải file blob về máy. */
export function downloadBlob(blob, filename) {
  const url = window.URL.createObjectURL(blob instanceof Blob ? blob : new Blob([blob]));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
}

export default api;
