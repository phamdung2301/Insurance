import React, { useState, useEffect, useRef } from 'react';
import { getUserMessage } from '../utils/messages';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import {
  ShieldAlert, ArrowLeft, Upload, Trash2, Image,
  AlertCircle, CheckCircle2, FileText, Loader2
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { claimApi } from '../services/claimApi';
import { policyService } from '../services/api';
import { formatMoney } from '../utils/format';

const MAX_FILES = 10;
const MAX_FILE_SIZE_MB = 10;
const MAX_TOTAL_SIZE_MB = 50;
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

export function CreateClaimPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { user, hasRole } = useAuth();

  const [policyNumber, setPolicyNumber] = useState('');
  const [claimedAmount, setClaimedAmount] = useState('');
  const [damageDescription, setDamageDescription] = useState('');
  const [photos, setPhotos] = useState([]);
  const [photoPreviews, setPhotoPreviews] = useState([]);
  const [errors, setErrors] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  // Hợp đồng ACTIVE của chính user — tái dùng GET /policies/mine
  // (BE ép insuredEmail từ JWT + status=ACTIVE; không dùng applySearchScope
  // chung, và FE chỉ gửi status/page/size nên không có điều kiện AND thừa).
  const [activePolicies, setActivePolicies] = useState([]);
  const [policiesLoading, setPoliciesLoading] = useState(true);
  const [policiesFailed, setPoliciesFailed] = useState(false);

  const fileInputRef = useRef(null);

  // Tải hợp đồng ACTIVE của user; preselect ?policyNumber= CHỈ khi số đó
  // nằm trong list (khớp policyNumber hoặc id) — không preselect mù.
  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const res = await policyService.listMine({ status: 'ACTIVE', page: 0, size: 100 });
        const raw = res?.data?.content ?? res?.data ?? [];
        const list = (Array.isArray(raw) ? raw : []).filter((p) => p.status === 'ACTIVE');
        if (!active) return;
        setActivePolicies(list);
        const param = searchParams.get('policyNumber');
        if (param) {
          const match = list.find((p) => p.policyNumber === param || p.id === param);
          if (match) setPolicyNumber(match.policyNumber);
        }
      } catch (err) {
        console.error(err);
        if (active) setPoliciesFailed(true);
      } finally {
        if (active) setPoliciesLoading(false);
      }
    })();
    return () => { active = false; };
  }, [searchParams]);

  // Xử lý tạo ObjectURL preview khi files thay đổi
  useEffect(() => {
    const urls = photos.map(file => URL.createObjectURL(file));
    setPhotoPreviews(urls);
    return () => {
      urls.forEach(url => URL.revokeObjectURL(url));
    };
  }, [photos]);

  // Kiểm tra file vi phạm
  const validateFiles = (newPhotos) => {
    const validationErrors = [];
    if (newPhotos.length > MAX_FILES) {
      validationErrors.push(`Tối đa ${MAX_FILES} ảnh cho mỗi yêu cầu bồi thường (hiện có ${newPhotos.length} ảnh).`);
    }

    let totalSizeBytes = 0;
    newPhotos.forEach((file, index) => {
      totalSizeBytes += file.size;
      const sizeMB = file.size / (1024 * 1024);
      if (sizeMB > MAX_FILE_SIZE_MB) {
        validationErrors.push(`Ảnh "${file.name}" vượt quá dung lượng cho phép (${sizeMB.toFixed(2)} MB / tối đa ${MAX_FILE_SIZE_MB} MB).`);
      }
      if (!ALLOWED_TYPES.includes(file.type)) {
        validationErrors.push(`Ảnh "${file.name}" có định dạng không được hỗ trợ (chỉ nhận JPG, PNG, WEBP).`);
      }
    });

    const totalMB = totalSizeBytes / (1024 * 1024);
    if (totalMB > MAX_TOTAL_SIZE_MB) {
      validationErrors.push(`Tổng dung lượng ảnh vượt quá giới hạn (${totalMB.toFixed(2)} MB / tối đa ${MAX_TOTAL_SIZE_MB} MB).`);
    }

    setErrors(validationErrors);
    return validationErrors.length === 0;
  };

  const handleAddFiles = (filesToAdd) => {
    const validArray = Array.from(filesToAdd).filter(f => f.type.startsWith('image/'));
    const combined = [...photos, ...validArray];
    if (combined.length > MAX_FILES) {
      toast.error(`Chỉ được chọn tối đa ${MAX_FILES} ảnh.`);
    }
    const truncated = combined.slice(0, MAX_FILES);
    setPhotos(truncated);
    validateFiles(truncated);
  };

  const handleFileChange = (e) => {
    if (e.target.files && e.target.files.length > 0) {
      handleAddFiles(e.target.files);
    }
  };

  const handleRemovePhoto = (index) => {
    const updated = photos.filter((_, i) => i !== index);
    setPhotos(updated);
    validateFiles(updated);
  };

  // Drag and drop handlers
  const handleDragOver = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      handleAddFiles(e.dataTransfer.files);
    }
  };

  // Paste from clipboard handler
  const handlePaste = (e) => {
    const clipboardData = e.clipboardData;
    if (clipboardData && clipboardData.items) {
      const pastedFiles = [];
      for (let i = 0; i < clipboardData.items.length; i++) {
        const item = clipboardData.items[i];
        if (item.type.indexOf('image') !== -1) {
          const blob = item.getAsFile();
          if (blob) {
            const file = new File([blob], `pasted_image_${Date.now()}_${i}.png`, { type: blob.type });
            pastedFiles.push(file);
          }
        }
      }
      if (pastedFiles.length > 0) {
        handleAddFiles(pastedFiles);
        toast.info(`Đã dán ${pastedFiles.length} ảnh từ clipboard.`);
      }
    }
  };

  const totalSizeBytes = photos.reduce((acc, f) => acc + f.size, 0);
  const totalSizeMB = (totalSizeBytes / (1024 * 1024)).toFixed(2);

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!policyNumber.trim()) {
      toast.error('Vui lòng chọn hợp đồng bảo hiểm từ danh sách.');
      return;
    }
    const amountNum = parseFloat(claimedAmount);
    if (isNaN(amountNum) || amountNum <= 0) {
      toast.error('Vui lòng nhập số tiền yêu cầu bồi thường hợp lệ (> 0).');
      return;
    }
    if (!damageDescription.trim()) {
      toast.error('Vui lòng nhập mô tả chi tiết thiệt hại.');
      return;
    }
    if (damageDescription.trim().length > 5000) {
      toast.error('Mô tả thiệt hại tối đa 5000 ký tự.');
      return;
    }
    if (!validateFiles(photos)) {
      toast.error('Vui lòng loại bỏ các ảnh vi phạm quy định.');
      return;
    }

    try {
      setSubmitting(true);
      const formData = new FormData();
      formData.append('policyNumber', policyNumber.trim());
      formData.append('claimedAmount', amountNum);
      formData.append('damageDescription', damageDescription.trim());

      photos.forEach((photo) => {
        formData.append('photos', photo);
      });

      const res = await claimApi.createClaim(formData);
      toast.success('Gửi yêu cầu bồi thường thành công!');
      navigate(`/claims/${res.id || (res.data && res.data.id)}`);
    } catch (err) {
      console.error(err);
      toast.error(getUserMessage(err, 'Không thể tạo yêu cầu bồi thường. Vui lòng kiểm tra lại quyền hạn hoặc trạng thái hợp đồng.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="container" style={{ maxWidth: '850px', margin: '0 auto', padding: '1.5rem 1rem' }} onPaste={handlePaste}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', marginBottom: '1.5rem' }}>
        <button
          type="button"
          className="btn btn-secondary"
          onClick={() => navigate(-1)}
          style={{ padding: '0.4rem 0.6rem' }}
        >
          <ArrowLeft size={18} />
        </button>
        <div>
          <h1 style={{ fontSize: '1.6rem', fontWeight: 600, margin: 0, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <ShieldAlert color="#ea580c" size={26} />
            Yêu cầu bồi thường hợp đồng
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', margin: '0.25rem 0 0' }}>
            Khai báo tổn thất, mô tả chi tiết thiệt hại và đính kèm tối đa 10 ảnh bằng chứng hiện trường.
          </p>
        </div>
      </div>

      <div className="glass-card" style={{ padding: '1.75rem' }}>
        <form onSubmit={handleSubmit}>
          {/* Số hợp đồng — dropdown hợp đồng ACTIVE của chính user */}
          <div className="form-group" style={{ marginBottom: '1.25rem' }}>
            <label className="form-label" style={{ fontWeight: 600, display: 'block', marginBottom: '0.4rem' }}>
              Số hợp đồng bảo hiểm (ACTIVE) <span style={{ color: 'red' }}>*</span>
            </label>
            {policiesLoading ? (
              <div style={{ color: 'var(--text-secondary)', fontSize: '0.9rem', padding: '0.65rem 0' }}>
                <Loader2 size={16} className="spin" style={{ verticalAlign: 'middle' }} /> Đang tải danh sách hợp đồng của bạn...
              </div>
            ) : policiesFailed ? (
              <div style={{ color: '#dc2626', fontSize: '0.9rem', padding: '0.65rem 0' }}>
                Không tải được danh sách hợp đồng. Vui lòng tải lại trang và thử lại.
              </div>
            ) : activePolicies.length === 0 ? (
              <div style={{
                backgroundColor: '#fef3c7',
                border: '1px solid #fde68a',
                borderRadius: '6px',
                padding: '0.85rem 1rem',
                fontSize: '0.9rem',
                color: '#92400e'
              }}>
                Bạn chưa có hợp đồng nào đang có hiệu lực (ACTIVE) để yêu cầu bồi thường.
                Vui lòng <Link to="/mua-bao-hiem" style={{ color: '#1663d9', fontWeight: 600 }}>mua bảo hiểm</Link> và
                chờ hợp đồng được kích hoạt trước khi gửi yêu cầu.
              </div>
            ) : (
              <select
                className="form-control"
                value={policyNumber}
                onChange={(e) => setPolicyNumber(e.target.value)}
                required
                style={{ width: '100%', padding: '0.65rem 0.85rem' }}
              >
                <option value="">-- Chọn hợp đồng bảo hiểm --</option>
                {activePolicies.map((p) => (
                  <option key={p.policyNumber || p.id} value={p.policyNumber}>
                    {p.policyNumber}{p.insured?.name ? ` — ${p.insured.name}` : ''}
                  </option>
                ))}
              </select>
            )}
            <small style={{ color: 'var(--text-secondary)', display: 'block', marginTop: '0.3rem' }}>
              Chỉ liệt kê hợp đồng thuộc quyền sở hữu của bạn và đang ở trạng thái có hiệu lực (ACTIVE).
            </small>
          </div>

          {/* Số tiền yêu cầu bồi thường */}
          <div className="form-group" style={{ marginBottom: '1.25rem' }}>
            <label className="form-label" style={{ fontWeight: 600, display: 'block', marginBottom: '0.4rem' }}>
              Số tiền yêu cầu bồi thường (VNĐ) <span style={{ color: 'red' }}>*</span>
            </label>
            <input
              type="number"
              className="form-control"
              placeholder="VD: 5000000"
              min="1"
              step="any"
              value={claimedAmount}
              onChange={(e) => setClaimedAmount(e.target.value)}
              required
              style={{ width: '100%', padding: '0.65rem 0.85rem' }}
            />
            {claimedAmount > 0 && (
              <small style={{ color: '#1663d9', display: 'block', marginTop: '0.3rem', fontWeight: 500 }}>
                Ước tính: {formatMoney(parseFloat(claimedAmount))}
              </small>
            )}
          </div>

          {/* Mô tả chi tiết thiệt hại */}
          <div className="form-group" style={{ marginBottom: '1.5rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
              <label className="form-label" style={{ fontWeight: 600, margin: 0 }}>
                Mô tả chi tiết thiệt hại & nguyên nhân <span style={{ color: 'red' }}>*</span>
              </label>
              <span style={{
                fontSize: '0.8rem',
                color: damageDescription.length > 5000 ? 'red' : 'var(--text-secondary)'
              }}>
                {damageDescription.length} / 5000 ký tự
              </span>
            </div>
            <textarea
              className="form-control"
              rows={5}
              placeholder="Mô tả cụ thể thời gian, địa điểm xảy ra sự kiện bảo hiểm, nguyên nhân, phạm vi thiệt hại và các tài sản liên quan..."
              value={damageDescription}
              onChange={(e) => setDamageDescription(e.target.value)}
              maxLength={5000}
              required
              style={{ width: '100%', padding: '0.65rem 0.85rem' }}
            />
          </div>

          {/* Khu vực Upload ảnh bằng chứng */}
          <div className="form-group" style={{ marginBottom: '1.5rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
              <label className="form-label" style={{ fontWeight: 600, margin: 0, display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                <Image size={18} color="#1663d9" />
                Ảnh chụp bằng chứng hiện trường / hóa đơn tổn thất
              </label>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', display: 'flex', gap: '1rem' }}>
                <span>Đã chọn: <strong style={{ color: photos.length > MAX_FILES ? 'red' : 'inherit' }}>{photos.length}</strong> / {MAX_FILES} ảnh</span>
                <span>Dung lượng: <strong style={{ color: totalSizeBytes > MAX_TOTAL_SIZE_MB * 1024 * 1024 ? 'red' : 'inherit' }}>{totalSizeMB}</strong> / {MAX_TOTAL_SIZE_MB} MB</span>
              </div>
            </div>

            {/* Dropzone */}
            <div
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              style={{
                border: `2px dashed ${isDragging ? '#1663d9' : 'var(--border-color, #cbd5e1)'}`,
                backgroundColor: isDragging ? 'rgba(22, 99, 217, 0.05)' : 'var(--bg-secondary, #f8fafc)',
                borderRadius: '8px',
                padding: '1.75rem 1rem',
                textAlign: 'center',
                cursor: 'pointer',
                transition: 'all 0.2s ease',
                marginBottom: '1rem'
              }}
            >
              <input
                ref={fileInputRef}
                type="file"
                multiple
                accept="image/jpeg,image/png,image/webp"
                style={{ display: 'none' }}
                onChange={handleFileChange}
              />
              <Upload size={32} color="#64748b" style={{ margin: '0 auto 0.5rem' }} />
              <div style={{ fontWeight: 500, fontSize: '0.95rem' }}>
                Kéo thả ảnh vào đây, hoặc <span style={{ color: '#1663d9', textDecoration: 'underline' }}>chọn file từ máy tính</span>
              </div>
              <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', marginTop: '0.25rem' }}>
                (Hỗ trợ định dạng JPG, PNG, WEBP. Tối đa 10 ảnh, mỗi ảnh $\le$ 10MB, tổng dung lượng $\le$ 50MB. Có thể bấm Ctrl+V để dán ảnh trực tiếp)
              </div>
            </div>

            {/* Hiển thị lỗi vi phạm */}
            {errors.length > 0 && (
              <div style={{
                backgroundColor: '#fef2f2',
                border: '1px solid #fecaca',
                borderRadius: '6px',
                padding: '0.75rem 1rem',
                marginBottom: '1rem'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#dc2626', fontWeight: 600, fontSize: '0.85rem' }}>
                  <AlertCircle size={16} /> Các file chưa đạt yêu cầu:
                </div>
                <ul style={{ margin: '0.4rem 0 0', paddingLeft: '1.5rem', color: '#b91c1c', fontSize: '0.85rem' }}>
                  {errors.map((err, i) => (
                    <li key={i}>{err}</li>
                  ))}
                </ul>
              </div>
            )}

            {/* Danh sách ảnh đã chọn */}
            {photos.length > 0 && (
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(130px, 1fr))',
                gap: '0.75rem',
                marginTop: '0.5rem'
              }}>
                {photos.map((file, idx) => (
                  <div
                    key={idx}
                    style={{
                      position: 'relative',
                      border: '1px solid var(--border-color, #e2e8f0)',
                      borderRadius: '6px',
                      overflow: 'hidden',
                      backgroundColor: '#fff',
                      boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
                    }}
                  >
                    <img
                      src={photoPreviews[idx]}
                      alt={file.name}
                      style={{
                        width: '100%',
                        height: '90px',
                        objectFit: 'cover',
                        display: 'block'
                      }}
                    />
                    <div style={{ padding: '0.4rem', fontSize: '0.75rem' }}>
                      <div style={{
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                        fontWeight: 500
                      }}>
                        {file.name}
                      </div>
                      <div style={{ color: 'var(--text-secondary)' }}>
                        {(file.size / (1024 * 1024)).toFixed(2)} MB
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleRemovePhoto(idx);
                      }}
                      style={{
                        position: 'absolute',
                        top: '4px',
                        right: '4px',
                        background: 'rgba(239, 68, 68, 0.85)',
                        color: 'white',
                        border: 'none',
                        borderRadius: '50%',
                        width: '24px',
                        height: '24px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        cursor: 'pointer'
                      }}
                      title="Xóa ảnh"
                    >
                      <Trash2 size={13} />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Action buttons */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.5rem', borderTop: '1px solid var(--border-color, #e2e8f0)', paddingTop: '1.25rem' }}>
            <Link to="/claims" className="btn btn-secondary">
              Hủy
            </Link>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={submitting || errors.length > 0 || policiesLoading || policiesFailed || activePolicies.length === 0 || !policyNumber}
              style={{
                backgroundColor: '#ea580c',
                borderColor: '#ea580c',
                color: '#fff',
                display: 'flex',
                alignItems: 'center',
                gap: '0.4rem',
                minWidth: '150px',
                justifyContent: 'center'
              }}
            >
              {submitting ? (
                <>
                  <Loader2 size={16} className="spin" /> Đang gửi...
                </>
              ) : (
                <>
                  <CheckCircle2 size={16} /> Gửi yêu cầu bồi thường
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default CreateClaimPage;

