import React, { useState, useEffect } from 'react';
import { useToast } from '../context/ToastContext';
import { policyLocationService } from '../services/api';
import {
  MapPin,
  Plus,
  Trash2,
  Edit2,
  Shield,
  DollarSign,
  RefreshCw,
  Search,
  Building,
  AlertCircle,
  CheckCircle2,
  Layers,
  X,
  Save,
} from 'lucide-react';

export const PolicyLocationsPage = () => {
  const { showToast } = useToast();

  const [policyNumber, setPolicyNumber] = useState('POL-2026-001');
  const [loading, setLoading] = useState(false);
  const [policyData, setPolicyData] = useState(null);
  const [locations, setLocations] = useState([]);

  // Modal State for Add / Edit Location
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingLocation, setEditingLocation] = useState(null);
  const [savingLocation, setSavingLocation] = useState(false);

  // Form State
  const [locationForm, setLocationForm] = useState({
    locationId: '',
    address: '',
    coverages: [],
  });

  const [newCoverage, setNewCoverage] = useState({
    coverageCode: '',
    coverageName: '',
    coverageType: 'STANDARD',
    limit: 100000000,
    deductible: 5000000,
    termMonths: 12,
    premium: 500000,
  });

  const fetchLocations = async (policyIdToSearch) => {
    if (!policyIdToSearch) return;
    try {
      setLoading(true);
      const res = await policyLocationService.getLocations(policyIdToSearch);
      if (res && res.data) {
        setLocations(res.data);
        showToast(`Đã tải ${res.data.length} địa điểm cho đơn bảo hiểm ${policyIdToSearch}!`, 'success');
      }
    } catch (err) {
      showToast(err.message || 'Không tìm thấy hợp đồng bảo hiểm', 'error');
      setLocations([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLocations(policyNumber);
  }, []);

  const handleOpenAddModal = () => {
    setEditingLocation(null);
    setLocationForm({
      locationId: '',
      address: '',
      coverages: [
        {
          coverageCode: 'FIRE-01',
          coverageName: 'Bảo hiểm Hỏa hoạn & Cháy nổ',
          coverageType: 'STANDARD',
          limit: 300000000,
          deductible: 10000000,
          termMonths: 12,
          premium: 450000,
        },
      ],
    });
    setIsModalOpen(true);
  };

  const handleOpenEditModal = (loc) => {
    setEditingLocation(loc);
    setLocationForm({
      locationId: loc.locationId,
      address: loc.address || '',
      coverages: loc.coverages ? [...loc.coverages] : [],
    });
    setIsModalOpen(true);
  };

  const handleAddCoverageToForm = () => {
    if (!newCoverage.coverageCode.trim()) {
      showToast('Vui lòng nhập mã gói bảo hiểm (Coverage Code)', 'error');
      return;
    }
    setLocationForm((prev) => ({
      ...prev,
      coverages: [...prev.coverages, { ...newCoverage }],
    }));
    setNewCoverage({
      coverageCode: '',
      coverageName: '',
      coverageType: 'STANDARD',
      limit: 100000000,
      deductible: 5000000,
      termMonths: 12,
      premium: 500000,
    });
  };

  const handleRemoveCoverageFromForm = (idx) => {
    setLocationForm((prev) => ({
      ...prev,
      coverages: prev.coverages.filter((_, i) => i !== idx),
    }));
  };

  const handleSaveLocation = async (e) => {
    e.preventDefault();
    if (!locationForm.address.trim()) {
      showToast('Địa chỉ địa điểm không được để trống', 'error');
      return;
    }

    try {
      setSavingLocation(true);
      const payload = {
        locationId: locationForm.locationId ? parseInt(locationForm.locationId) : null,
        address: locationForm.address,
        coverages: locationForm.coverages,
      };

      let res;
      if (editingLocation) {
        res = await policyLocationService.updateLocation(
          policyNumber,
          editingLocation.locationId,
          payload
        );
        showToast(`Cập nhật thành công địa điểm #${editingLocation.locationId} (PUT)!`, 'success');
      } else {
        res = await policyLocationService.addLocation(policyNumber, payload);
        showToast('Thêm mới địa điểm vào hợp đồng thành công (POST)!', 'success');
      }

      if (res && res.data) {
        setPolicyData(res.data);
        if (res.data.locations) {
          setLocations(res.data.locations);
        }
      }
      setIsModalOpen(false);
    } catch (err) {
      showToast(err.message || 'Lỗi khi lưu địa điểm', 'error');
    } finally {
      setSavingLocation(false);
    }
  };

  const handleDeleteLocation = async (locationId) => {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa địa điểm #${locationId} khỏi đơn bảo hiểm này?`)) {
      return;
    }

    try {
      setLoading(true);
      const res = await policyLocationService.removeLocation(policyNumber, locationId);
      showToast(`Đã xóa địa điểm #${locationId} thành công (DELETE)!`, 'success');
      if (res && res.data && res.data.locations) {
        setLocations(res.data.locations);
      } else {
        setLocations((prev) => prev.filter((l) => l.locationId !== locationId));
      }
    } catch (err) {
      showToast(err.message || 'Không thể xóa địa điểm', 'error');
    } finally {
      setLoading(false);
    }
  };

  // Compute total premium of loaded locations
  const totalPremium = locations.reduce((sum, loc) => {
    const locSum = loc.coverages ? loc.coverages.reduce((cSum, c) => cSum + (c.premium || 0), 0) : 0;
    return sum + locSum;
  }, 0);

  return (
    <div className="page-wrapper">
      {/* Header Banner */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          flexWrap: 'wrap',
          gap: '1rem',
          marginBottom: '2rem',
        }}
      >
        <div>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
            <span className="badge badge-primary">
              <Layers size={12} /> P04: Nested Location API
            </span>
          </div>
          <h1 style={{ fontSize: '1.85rem' }}>Quản lý Địa điểm Hợp đồng (Nested Locations)</h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.95rem' }}>
            Thêm, sửa, xóa các đối tượng địa điểm và quyền lợi bảo hiểm (Coverages) lồng ghép trong Policy Document.
          </p>
        </div>

        <button onClick={handleOpenAddModal} className="btn btn-primary">
          <Plus size={18} />
          <span>Thêm địa điểm mới (POST)</span>
        </button>
      </div>

      {/* Policy Search & Stats Header */}
      <div
        className="glass-card"
        style={{
          marginBottom: '2rem',
          padding: '1.25rem 1.5rem',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: '1.5rem',
          background: 'rgba(30, 41, 59, 0.4)',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flex: 1, minWidth: '280px' }}>
          <Building size={20} color="var(--primary)" />
          <span style={{ fontSize: '0.9rem', fontWeight: 600 }}>Mã hợp đồng (Policy Number):</span>
          <div style={{ display: 'flex', gap: '0.5rem', flex: 1, maxWidth: '300px' }}>
            <input
              type="text"
              value={policyNumber}
              onChange={(e) => setPolicyNumber(e.target.value)}
              placeholder="Ví dụ: POL-2026-001"
              className="form-control"
              style={{ padding: '0.45rem 0.75rem', fontSize: '0.85rem', fontFamily: 'var(--font-mono)' }}
            />
            <button
              onClick={() => fetchLocations(policyNumber)}
              disabled={loading}
              className="btn btn-secondary"
              style={{ padding: '0.45rem 0.85rem', fontSize: '0.85rem' }}
            >
              <Search size={14} className={loading ? 'spinner' : ''} />
              <span>Tra cứu</span>
            </button>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '1.5rem', alignItems: 'center' }}>
          <div>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'block' }}>TỔNG ĐỊA ĐIỂM</span>
            <span style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-primary)' }}>
              {locations.length}
            </span>
          </div>

          <div style={{ width: '1px', height: '32px', background: 'var(--border-subtle)' }} />

          <div>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'block' }}>TỔNG PHÍ BẢO HIỂM</span>
            <span style={{ fontSize: '1.25rem', fontWeight: 700, color: '#10b981' }}>
              {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(totalPremium)}
            </span>
          </div>
        </div>
      </div>

      {/* Locations List / Grid */}
      {locations.length === 0 ? (
        <div
          className="glass-card"
          style={{
            textAlign: 'center',
            padding: '3.5rem 2rem',
            borderStyle: 'dashed',
          }}
        >
          <MapPin size={40} color="var(--text-muted)" style={{ margin: '0 auto 1rem' }} />
          <h3 style={{ fontSize: '1.2rem', marginBottom: '0.5rem' }}>Chưa có địa điểm nào trong hợp đồng này</h3>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', marginBottom: '1.5rem', maxWidth: '450px', margin: '0 auto 1.5rem' }}>
            Hợp đồng chưa đăng ký địa điểm bảo hiểm. Nhấn "Thêm địa điểm mới" để thêm tài sản vào hợp đồng.
          </p>
          <button onClick={handleOpenAddModal} className="btn btn-primary">
            <Plus size={16} />
            <span>Thêm địa điểm đầu tiên</span>
          </button>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          {locations.map((loc) => {
            const locPremium = loc.coverages
              ? loc.coverages.reduce((sum, c) => sum + (c.premium || 0), 0)
              : 0;

            return (
              <div
                key={loc.locationId}
                className="glass-card"
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '1rem',
                  borderLeft: '4px solid var(--primary)',
                }}
              >
                {/* Location Card Header */}
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'flex-start',
                    flexWrap: 'wrap',
                    gap: '0.75rem',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'flex-start', gap: '0.75rem' }}>
                    <div
                      style={{
                        padding: '0.4rem 0.65rem',
                        borderRadius: 'var(--radius-sm)',
                        background: 'rgba(99, 102, 241, 0.2)',
                        color: '#a5b4fc',
                        fontFamily: 'var(--font-mono)',
                        fontWeight: 700,
                        fontSize: '0.85rem',
                        marginTop: '2px',
                      }}
                    >
                      #{loc.locationId}
                    </div>
                    <div>
                      <h3 style={{ fontSize: '1.15rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        <MapPin size={18} color="var(--primary)" />
                        {loc.address}
                      </h3>
                      <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                        Số lượng gói bảo hiểm: <b>{loc.coverages?.length || 0}</b> | Phí địa điểm:{' '}
                        <b style={{ color: '#10b981' }}>
                          {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(locPremium)}
                        </b>
                      </span>
                    </div>
                  </div>

                  <div style={{ display: 'flex', gap: '0.5rem' }}>
                    <button
                      onClick={() => handleOpenEditModal(loc)}
                      className="btn btn-secondary"
                      style={{ padding: '0.4rem 0.75rem', fontSize: '0.8rem' }}
                      title="Chỉnh sửa địa điểm"
                    >
                      <Edit2 size={14} />
                      <span>Sửa (PUT)</span>
                    </button>
                    <button
                      onClick={() => handleDeleteLocation(loc.locationId)}
                      className="btn btn-danger"
                      style={{ padding: '0.4rem 0.75rem', fontSize: '0.8rem' }}
                      title="Xóa địa điểm"
                    >
                      <Trash2 size={14} />
                      <span>Xóa (DELETE)</span>
                    </button>
                  </div>
                </div>

                {/* Coverages Table */}
                {loc.coverages && loc.coverages.length > 0 && (
                  <div
                    style={{
                      background: 'rgba(15, 23, 42, 0.5)',
                      borderRadius: 'var(--radius-md)',
                      padding: '0.75rem 1rem',
                      border: '1px solid var(--border-subtle)',
                      overflowX: 'auto',
                    }}
                  >
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.825rem' }}>
                      <thead>
                        <tr style={{ color: 'var(--text-muted)', textAlign: 'left', borderBottom: '1px solid var(--border-subtle)' }}>
                          <th style={{ padding: '0.4rem 0.5rem' }}>MÃ GÓI</th>
                          <th style={{ padding: '0.4rem 0.5rem' }}>TÊN QUYỀN LỢI</th>
                          <th style={{ padding: '0.4rem 0.5rem' }}>LOẠI</th>
                          <th style={{ padding: '0.4rem 0.5rem', textAlign: 'right' }}>HẠN MỨC BẢO VỆ</th>
                          <th style={{ padding: '0.4rem 0.5rem', textAlign: 'right' }}>MỨC KHẤU TRỪ</th>
                          <th style={{ padding: '0.4rem 0.5rem', textAlign: 'right' }}>PHÍ BẢO HIỂM</th>
                        </tr>
                      </thead>
                      <tbody>
                        {loc.coverages.map((cov, cIdx) => (
                          <tr key={cIdx} style={{ borderBottom: '1px solid rgba(255,255,255,0.03)' }}>
                            <td style={{ padding: '0.5rem', fontFamily: 'var(--font-mono)', fontWeight: 600, color: '#38bdf8' }}>
                              {cov.coverageCode}
                            </td>
                            <td style={{ padding: '0.5rem', color: 'var(--text-primary)' }}>
                              {cov.coverageName || '—'}
                            </td>
                            <td style={{ padding: '0.5rem' }}>
                              <span className="badge badge-primary">{cov.coverageType || 'STANDARD'}</span>
                            </td>
                            <td style={{ padding: '0.5rem', textAlign: 'right', fontWeight: 600 }}>
                              {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(cov.limit || 0)}
                            </td>
                            <td style={{ padding: '0.5rem', textAlign: 'right', color: 'var(--text-muted)' }}>
                              {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(cov.deductible || 0)}
                            </td>
                            <td style={{ padding: '0.5rem', textAlign: 'right', fontWeight: 700, color: '#10b981' }}>
                              {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(cov.premium || 0)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Modal Dialog for Add / Edit Location */}
      {isModalOpen && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(0, 0, 0, 0.75)',
            backdropFilter: 'blur(8px)',
            zIndex: 1000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '1rem',
          }}
        >
          <div
            className="glass-card"
            style={{
              width: '100%',
              maxWidth: '680px',
              maxHeight: '90vh',
              overflowY: 'auto',
              background: '#0f172a',
              border: '1px solid rgba(99, 102, 241, 0.3)',
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
              <h2 style={{ fontSize: '1.3rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <MapPin size={20} color="var(--primary)" />
                {editingLocation ? `Chỉnh sửa Địa điểm #${editingLocation.locationId}` : 'Thêm Địa điểm mới (POST)'}
              </h2>
              <button
                onClick={() => setIsModalOpen(false)}
                style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}
              >
                <X size={20} />
              </button>
            </div>

            <form onSubmit={handleSaveLocation}>
              <div className="form-group">
                <label className="form-label">Location ID (Tùy chọn - Để trống hệ thống sẽ tự sinh ID)</label>
                <input
                  type="number"
                  value={locationForm.locationId}
                  onChange={(e) => setLocationForm((p) => ({ ...p, locationId: e.target.value }))}
                  disabled={Boolean(editingLocation)}
                  placeholder="Ví dụ: 3 (Hoặc để trống để tự sinh số thứ tự tiếp theo)"
                  className="form-control"
                  style={{ fontFamily: 'var(--font-mono)' }}
                />
              </div>

              <div className="form-group">
                <label className="form-label">Địa chỉ tài sản bảo hiểm *</label>
                <textarea
                  value={locationForm.address}
                  onChange={(e) => setLocationForm((p) => ({ ...p, address: e.target.value }))}
                  placeholder="Ví dụ: Tòa nhà Bitexco, 2 Hải Triều, Bến Nghé, Quận 1, TP.HCM"
                  rows={2}
                  required
                  className="form-control"
                />
              </div>

              {/* Coverages Section */}
              <div style={{ marginTop: '1.5rem', marginBottom: '1.5rem' }}>
                <label className="form-label" style={{ fontWeight: 700, fontSize: '0.9rem' }}>
                  Danh sách quyền lợi bảo hiểm tại địa điểm này:
                </label>

                {locationForm.coverages.map((cov, idx) => (
                  <div
                    key={idx}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      background: 'rgba(30, 41, 59, 0.5)',
                      padding: '0.5rem 0.75rem',
                      borderRadius: 'var(--radius-sm)',
                      marginBottom: '0.5rem',
                      fontSize: '0.825rem',
                    }}
                  >
                    <div>
                      <b style={{ color: '#38bdf8' }}>{cov.coverageCode}</b> - {cov.coverageName || 'Gói bảo hiểm'} (
                      {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(cov.premium || 0)})
                    </div>
                    <button
                      type="button"
                      onClick={() => handleRemoveCoverageFromForm(idx)}
                      style={{ background: 'transparent', border: 'none', color: '#f43f5e', cursor: 'pointer' }}
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                ))}

                {/* Add Coverage Subform */}
                <div
                  style={{
                    background: 'rgba(15, 23, 42, 0.7)',
                    padding: '0.75rem',
                    borderRadius: 'var(--radius-sm)',
                    border: '1px dashed var(--border-subtle)',
                    marginTop: '0.75rem',
                  }}
                >
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600, display: 'block', marginBottom: '0.4rem' }}>
                    + Thêm quyền lợi bảo hiểm mới:
                  </span>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem', marginBottom: '0.5rem' }}>
                    <input
                      type="text"
                      placeholder="Mã gói (VD: THEFT-01)"
                      value={newCoverage.coverageCode}
                      onChange={(e) => setNewCoverage((p) => ({ ...p, coverageCode: e.target.value }))}
                      className="form-control"
                      style={{ fontSize: '0.8rem', padding: '0.4rem 0.6rem' }}
                    />
                    <input
                      type="text"
                      placeholder="Tên gói (VD: Trộm cắp)"
                      value={newCoverage.coverageName}
                      onChange={(e) => setNewCoverage((p) => ({ ...p, coverageName: e.target.value }))}
                      className="form-control"
                      style={{ fontSize: '0.8rem', padding: '0.4rem 0.6rem' }}
                    />
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '0.5rem', marginBottom: '0.5rem' }}>
                    <input
                      type="number"
                      placeholder="Hạn mức (VND)"
                      value={newCoverage.limit}
                      onChange={(e) => setNewCoverage((p) => ({ ...p, limit: parseFloat(e.target.value) || 0 }))}
                      className="form-control"
                      style={{ fontSize: '0.8rem', padding: '0.4rem 0.6rem' }}
                    />
                    <input
                      type="number"
                      placeholder="Khấu trừ (VND)"
                      value={newCoverage.deductible}
                      onChange={(e) => setNewCoverage((p) => ({ ...p, deductible: parseFloat(e.target.value) || 0 }))}
                      className="form-control"
                      style={{ fontSize: '0.8rem', padding: '0.4rem 0.6rem' }}
                    />
                    <input
                      type="number"
                      placeholder="Phí (VND)"
                      value={newCoverage.premium}
                      onChange={(e) => setNewCoverage((p) => ({ ...p, premium: parseFloat(e.target.value) || 0 }))}
                      className="form-control"
                      style={{ fontSize: '0.8rem', padding: '0.4rem 0.6rem' }}
                    />
                  </div>
                  <button
                    type="button"
                    onClick={handleAddCoverageToForm}
                    className="btn btn-secondary"
                    style={{ width: '100%', fontSize: '0.75rem', padding: '0.35rem' }}
                  >
                    + Gắn gói bảo hiểm này vào địa điểm
                  </button>
                </div>
              </div>

              <div
                style={{
                  display: 'flex',
                  justifyContent: 'flex-end',
                  gap: '0.75rem',
                  borderTop: '1px solid var(--border-subtle)',
                  paddingTop: '1rem',
                }}
              >
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="btn btn-secondary"
                  disabled={savingLocation}
                >
                  Hủy
                </button>
                <button type="submit" className="btn btn-primary" disabled={savingLocation}>
                  {savingLocation ? (
                    <>
                      <div className="spinner" />
                      <span>Đang lưu...</span>
                    </>
                  ) : (
                    <>
                      <Save size={16} />
                      <span>Lưu địa điểm ({editingLocation ? 'PUT' : 'POST'})</span>
                    </>
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
