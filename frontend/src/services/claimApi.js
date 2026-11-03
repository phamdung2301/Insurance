import api from './api';

export const claimApi = {
  createClaim: async (formData) => {
    return api.post('/api/claims', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  addPhotos: async (claimId, formData) => {
    return api.post(`/api/claims/${claimId}/photos`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  getClaims: async (params) => {
    return api.get('/api/claims', { params });
  },

  getClaimById: async (claimId) => {
    return api.get(`/api/claims/${claimId}`);
  },

  getPhotoBlobUrl: async (claimId, photoId) => {
    const token = localStorage.getItem('itoken');
    const response = await fetch(`/api/claims/${claimId}/photos/${photoId}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!response.ok) {
      throw new Error('Không thể tải ảnh bằng chứng');
    }
    const blob = await response.blob();
    return URL.createObjectURL(blob);
  },

  reviewClaim: async (claimId) => {
    return api.post(`/api/claims/${claimId}/review`);
  },

  approveClaim: async (claimId, data) => {
    return api.post(`/api/claims/${claimId}/approve`, data);
  },

  rejectClaim: async (claimId, data) => {
    return api.post(`/api/claims/${claimId}/reject`, data);
  },

  payClaim: async (claimId) => {
    return api.post(`/api/claims/${claimId}/pay`);
  },
};

export default claimApi;

