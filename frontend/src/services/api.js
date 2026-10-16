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
    if (status === 403) {
      const err403 = new Error('Bạn không có quyền thực hiện thao tác này (yêu cầu quyền quản trị viên).');
      err403.status = 403;
      return Promise.reject(err403);
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
  /** Hợp đồng của chính user đang đăng nhập (backend filter theo JWT, không lộ data người khác) */
  listMine: (params = {}) => api.get('/policies/mine', { params }),
  getPolicyByNumber: (policyNumber) => api.get(`/policies/${encodeURIComponent(policyNumber)}`),
  createPolicy: (data) => api.post('/policies', data),
  updatePolicy: (policyNumber, data) => api.put(`/policies/${encodeURIComponent(policyNumber)}`, data),
  deletePolicy: (policyNumber) => api.delete(`/policies/${encodeURIComponent(policyNumber)}`),
  /** Chuyển trạng thái vòng đời — backend: POST /policies/{n}/status-transitions */
  transition: (policyNumber, toStatus, extra = {}) =>
    api.post(`/policies/${encodeURIComponent(policyNumber)}/status-transitions`, { targetStatus: toStatus, ...extra }),
  /** Yêu cầu điều chỉnh (endorsement) — backend: POST /policies/{n}/endorsements */
  endorse: (policyNumber, payload) => api.post(`/policies/${encodeURIComponent(policyNumber)}/endorsements`, payload),
  getHistory: (policyNumber) => api.get(`/policies/${encodeURIComponent(policyNumber)}/history`),
  getVersions: (policyNumber) => api.get(`/policies/${encodeURIComponent(policyNumber)}/versions`),
  getVersion: (policyNumber, v) => api.get(`/policies/${encodeURIComponent(policyNumber)}/versions/${encodeURIComponent(v)}`),
};

// ---------------------------------------------------------------- Locations (nested)
export const policyLocationService = {
  getLocations: (policyNumber) => api.get(`/policies/${encodeURIComponent(policyNumber)}/locations`),
  getLocationById: (policyNumber, locationId) =>
    api.get(`/policies/${encodeURIComponent(policyNumber)}/locations/${encodeURIComponent(locationId)}`),
  addLocation: (policyNumber, data) => api.post(`/policies/${encodeURIComponent(policyNumber)}/locations`, data),
  updateLocation: (policyNumber, locationId, data) =>
    api.put(`/policies/${encodeURIComponent(policyNumber)}/locations/${encodeURIComponent(locationId)}`, data),
  removeLocation: (policyNumber, locationId) =>
    api.delete(`/policies/${encodeURIComponent(policyNumber)}/locations/${encodeURIComponent(locationId)}`),
};

// ---------------------------------------------------------------- Coverages (nested, keyed by coverageCode)
export const policyCoverageService = {
  getCoverages: (policyNumber, locationId) =>
    api.get(`/policies/${encodeURIComponent(policyNumber)}/locations/${encodeURIComponent(locationId)}/coverages`),
  addCoverage: (policyNumber, locationId, data) =>
    api.post(`/policies/${encodeURIComponent(policyNumber)}/locations/${encodeURIComponent(locationId)}/coverages`, data),
  updateCoverage: (policyNumber, locationId, coverageCode, data) =>
    api.put(`/policies/${encodeURIComponent(policyNumber)}/locations/${encodeURIComponent(locationId)}/coverages/${encodeURIComponent(coverageCode)}`, data),
  removeCoverage: (policyNumber, locationId, coverageCode) =>
    api.delete(`/policies/${encodeURIComponent(policyNumber)}/locations/${encodeURIComponent(locationId)}/coverages/${encodeURIComponent(coverageCode)}`),
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
    api.get(`/excel/policy/${encodeURIComponent(policyNumber)}/download`, { responseType: 'blob' }),
  getRates: () => api.get('/excel/rates'),
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
  stats: () => api.get('/benchmark/stats'),
};

// ---------------------------------------------------------------- Policy documents
export const documentService = {
  list: (policyNumber) => api.get(`/policies/${encodeURIComponent(policyNumber)}/documents`),
  downloadCertificate: (policyNumber, version) =>
    api.get(`/policies/${encodeURIComponent(policyNumber)}/documents/certificate`, {
      params: version ? { version } : {},
      responseType: 'blob',
    }),
  downloadSummary: (policyNumber, version) =>
    api.get(`/policies/${encodeURIComponent(policyNumber)}/documents/summary`, {
      params: version ? { version } : {},
      responseType: 'blob',
    }),
};

// ---------------------------------------------------------------- Index audit (admin)
export const indexAuditService = {
  audit: () => api.get('/admin/indexes/audit'),
  sync: () => api.post('/admin/indexes/sync'),
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
