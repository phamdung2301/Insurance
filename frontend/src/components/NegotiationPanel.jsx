import { useCallback, useEffect, useState } from 'react';
import { negotiationService } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { formatMoney, formatDateTime } from '../utils/format';

const STATUS_LABEL = {
  NOT_STARTED: 'Chưa bắt đầu',
  IN_NEGOTIATION: 'Đang thương lượng',
  PENDING_CONFIRMATION: 'Chờ xác nhận chốt giá',
  AGREED_LOCKED: 'Đã chốt giá',
};

const SIDE_LABEL = { CUSTOMER: 'Khách hàng', AGENT: 'Nhân viên tư vấn' };

const OFFER_STATUS_LABEL = {
  PENDING: 'Đang chờ phản hồi',
  ACCEPTED: 'Đã chấp nhận',
  REJECTED: 'Đã từ chối',
  COUNTERED: 'Đã có giá mới',
};

const btnPrimary = {
  padding: '0.55rem 1rem', border: 'none', borderRadius: '8px',
  background: '#2563eb', color: '#fff', cursor: 'pointer', fontWeight: 600,
};

const btnSecondary = {
  padding: '0.55rem 1rem', border: '1px solid #d1d5db', borderRadius: '8px',
  background: '#fff', color: '#111827', cursor: 'pointer', fontWeight: 600,
};

const btnDanger = {
  padding: '0.55rem 1rem', border: '1px solid #fca5a5', borderRadius: '8px',
  background: '#fff', color: '#b91c1c', cursor: 'pointer', fontWeight: 600,
};

const inputStyle = {
  padding: '0.55rem 0.75rem', border: '1px solid #d1d5db',
  borderRadius: '8px', fontSize: '0.95rem', width: '100%', boxSizing: 'border-box',
};

/**
 * Tab thương lượng giá cho 1 hợp đồng (demo_thuong_luong_gia).
 * Mọi luật (giá sàn, lượt gửi giá, xác nhận chốt) đều do backend giữ;
 * UI chỉ ẩn/hiện theo viewerSide + negotiationStatus trả về.
 */
export function NegotiationPanel({ policyNumber }) {
  const { user } = useAuth();
  const toast = useToast();
  const [nego, setNego] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [busy, setBusy] = useState(false);
  const [price, setPrice] = useState('');
  const [offerMessage, setOfferMessage] = useState('');
  const [floor, setFloor] = useState('');
  const [chatText, setChatText] = useState('');

  const myEmail = user?.email || '';

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const res = await negotiationService.getNegotiation(policyNumber);
      setNego(res?.data ?? null);
    } catch (err) {
      setLoadError(err.message || 'Không tải được thông tin thương lượng.');
    } finally {
      setLoading(false);
    }
  }, [policyNumber]);

  useEffect(() => { load(); }, [load]);

  const act = async (fn, okMsg) => {
    setBusy(true);
    try {
      const res = await fn();
      setNego(res?.data ?? null);
      if (okMsg) toast.success(okMsg);
    } catch (err) {
      toast.error(err.message || 'Thao tác thất bại.');
    } finally {
      setBusy(false);
    }
  };

  if (loading) return <div className="glass-card">Đang tải thương lượng…</div>;
  if (loadError || !nego) {
    return (
      <div className="glass-card">
        <p>{loadError || 'Không có thông tin thương lượng.'}</p>
        <button type="button" style={btnSecondary} onClick={load}>Tải lại</button>
      </div>
    );
  }

  const status = nego.negotiationStatus;
  const locked = status === 'AGREED_LOCKED';
  const isAgent = nego.viewerSide === 'AGENT';
  const quoted = nego.policyStatus === 'QUOTED';
  const pendingOffer = nego.pendingOffer || null;
  const acceptedOffer = (nego.offers || []).find((o) => o.status === 'ACCEPTED') || null;
  const myPending = Boolean(pendingOffer && pendingOffer.senderEmail === myEmail);
  const displayPrice = locked
    ? nego.lockedPrice
    : pendingOffer?.price ?? acceptedOffer?.price ?? nego.currentPrice;
  const canOffer = quoted && !locked && status !== 'PENDING_CONFIRMATION' && !myPending;

  const submitPrice = () => {
    const p = Number(price);
    if (!Number.isFinite(p) || p <= 0) {
      toast.error('Vui lòng nhập giá lớn hơn 0.');
      return;
    }
    act(
      () => negotiationService.submitOffer(policyNumber, { price: p, message: offerMessage.trim() }),
      'Đã gửi giá.',
    );
    setPrice('');
    setOfferMessage('');
  };

  const submitFloor = () => {
    const f = Number(floor);
    if (!Number.isFinite(f) || f <= 0) {
      toast.error('Vui lòng nhập giá sàn lớn hơn 0.');
      return;
    }
    act(() => negotiationService.setFloorPrice(policyNumber, f), 'Đã đặt giá sàn.');
    setFloor('');
  };

  const sendChat = () => {
    const content = chatText.trim();
    if (!content) return;
    act(() => negotiationService.sendMessage(policyNumber, content));
    setChatText('');
  };

  return (
    <div>
      {/* Trạng thái tổng quan */}
      <div className="glass-card" style={{ marginBottom: '1rem' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1.5rem', alignItems: 'center' }}>
          <div>
            <div style={{ fontSize: '0.85rem', color: '#6b7280' }}>Trạng thái thương lượng</div>
            <strong>{STATUS_LABEL[status] || status}</strong>
          </div>
          <div>
            <div style={{ fontSize: '0.85rem', color: '#6b7280' }}>
              {locked ? 'Giá đã chốt' : 'Giá hiện tại'}
            </div>
            <strong style={{ fontSize: '1.25rem' }}>{formatMoney(displayPrice)}</strong>
          </div>
          <div>
            <div style={{ fontSize: '0.85rem', color: '#6b7280' }}>Xác nhận chốt giá</div>
            <strong>{nego.confirmations ?? 0}/2</strong>
          </div>
          {isAgent && nego.floorPrice != null && (
            <div>
              <div style={{ fontSize: '0.85rem', color: '#6b7280' }}>Giá sàn (chỉ nhân viên thấy)</div>
              <strong>{formatMoney(nego.floorPrice)}</strong>
            </div>
          )}
        </div>
        <div style={{ marginTop: '0.75rem', fontSize: '0.9rem', color: '#4b5563' }}>
          Bạn đang xem với vai trò <strong>{SIDE_LABEL[nego.viewerSide] || nego.viewerSide}</strong>
          {nego.customerEmail && <> · Khách hàng: {nego.customerEmail}</>}
          {nego.agentEmail && <> · Nhân viên: {nego.agentEmail}</>}
        </div>
        {locked && (
          <div style={{ marginTop: '0.75rem', background: '#ecfdf5', border: '1px solid #a7f3d0', borderRadius: '8px', padding: '0.75rem 1rem', color: '#065f46' }}>
            Giá đã chốt ở {formatMoney(nego.lockedPrice)} sau khi cả 2 bên xác nhận. Lịch sử giá và chat đã đóng băng, không gửi thêm được nữa.
          </div>
        )}
        {!locked && !quoted && (
          <div style={{ marginTop: '0.75rem', background: '#fffbeb', border: '1px solid #fde68a', borderRadius: '8px', padding: '0.75rem 1rem', color: '#92400e' }}>
            Chỉ thương lượng được khi hợp đồng ở trạng thái QUOTED (hiện tại: {nego.policyStatus}).
          </div>
        )}
      </div>

      {/* Đặt giá sàn — chỉ bên agent, chỉ trước khi bắt đầu */}
      {isAgent && status === 'NOT_STARTED' && (
        <div className="glass-card" style={{ marginBottom: '1rem' }}>
          <h3 style={{ marginTop: 0 }}>Đặt giá sàn</h3>
          <p style={{ fontSize: '0.9rem', color: '#4b5563' }}>
            Giá sàn chỉ nhân viên thấy. Mọi giá gửi sau đó không được thấp hơn giá sàn, và chỉ đặt được trước khi bắt đầu thương lượng.
          </p>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <input
              type="number" min="0" value={floor} onChange={(e) => setFloor(e.target.value)}
              placeholder="Nhập giá sàn (VND)" style={inputStyle} disabled={busy}
            />
            <button type="button" style={btnPrimary} onClick={submitFloor} disabled={busy}>Đặt giá sàn</button>
          </div>
        </div>
      )}

      {/* Offer đang chờ — chỉ bên đối diện mới có nút nhận/từ chối */}
      {pendingOffer && (
        <div className="glass-card" style={{ marginBottom: '1rem', borderColor: '#93c5fd' }}>
          <h3 style={{ marginTop: 0 }}>Giá đang chờ phản hồi</h3>
          <p style={{ fontSize: '1.1rem' }}>
            <strong>{formatMoney(pendingOffer.price)}</strong>
            {' '}— {SIDE_LABEL[pendingOffer.side] || pendingOffer.side} ({pendingOffer.senderEmail})
          </p>
          {pendingOffer.message && <p style={{ color: '#374151' }}>“{pendingOffer.message}”</p>}
          <p style={{ fontSize: '0.85rem', color: '#6b7280' }}>{formatDateTime(pendingOffer.createdAt)}</p>
          {myPending && (
            <p style={{ color: '#4b5563' }}>Đang chờ bên kia phản hồi giá của bạn — chưa gửi giá mới được.</p>
          )}
          {!myPending && (
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <button
                type="button" style={btnPrimary} disabled={busy}
                onClick={() => act(() => negotiationService.acceptOffer(policyNumber, pendingOffer.offerId), 'Đã chấp nhận giá.')}
              >
                Chấp nhận giá
              </button>
              <button
                type="button" style={btnDanger} disabled={busy}
                onClick={() => act(() => negotiationService.rejectOffer(policyNumber, pendingOffer.offerId), 'Đã từ chối giá.')}
              >
                Từ chối
              </button>
            </div>
          )}
        </div>
      )}

      {/* Chờ xác nhận chốt — chỉ người đề xuất mới được xác nhận/từ chối */}
      {status === 'PENDING_CONFIRMATION' && acceptedOffer && (
        <div className="glass-card" style={{ marginBottom: '1rem', borderColor: '#fcd34d' }}>
          <h3 style={{ marginTop: 0 }}>Chờ xác nhận chốt giá ({nego.confirmations ?? 1}/2)</h3>
          <p>
            Giá <strong>{formatMoney(acceptedOffer.price)}</strong> đã được một bên chấp nhận.
            {acceptedOffer.senderEmail === myEmail
              ? ' Bạn là người đề xuất giá này — bạn xác nhận thì giá mới khóa.'
              : ' Đang chờ bên đề xuất xác nhận chốt.'}
          </p>
          {acceptedOffer.senderEmail === myEmail && (
            <div style={{ display: 'flex', gap: '0.5rem' }}>
              <button
                type="button" style={btnPrimary} disabled={busy}
                onClick={() => act(() => negotiationService.confirmLock(policyNumber), 'Đã chốt giá.')}
              >
                Xác nhận chốt giá
              </button>
              <button
                type="button" style={btnSecondary} disabled={busy}
                onClick={() => act(() => negotiationService.declineConfirmation(policyNumber), 'Đã mở lại thương lượng.')}
              >
                Từ chối, mở lại thương lượng
              </button>
            </div>
          )}
        </div>
      )}

      {/* Gửi giá */}
      {canOffer && (
        <div className="glass-card" style={{ marginBottom: '1rem' }}>
          <h3 style={{ marginTop: 0 }}>{status === 'NOT_STARTED' ? 'Gửi giá mở đầu' : 'Gửi giá mới (trả giá)'}</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <input
              type="number" min="0" value={price} onChange={(e) => setPrice(e.target.value)}
              placeholder="Nhập giá (VND)" style={inputStyle} disabled={busy}
            />
            <input
              value={offerMessage} onChange={(e) => setOfferMessage(e.target.value)}
              placeholder="Lời nhắn kèm giá (tối đa 500 ký tự, không bắt buộc)" style={inputStyle} disabled={busy}
            />
            <div>
              <button type="button" style={btnPrimary} onClick={submitPrice} disabled={busy}>Gửi giá</button>
            </div>
          </div>
          {isAgent && nego.floorPrice != null && (
            <p style={{ fontSize: '0.85rem', color: '#6b7280' }}>Giá gửi không được thấp hơn giá sàn {formatMoney(nego.floorPrice)}.</p>
          )}
        </div>
      )}

      {/* Lịch sử giá */}
      <div className="glass-card" style={{ marginBottom: '1rem' }}>
        <h3 style={{ marginTop: 0 }}>Lịch sử giá</h3>
        {(nego.offers || []).length === 0 && <p style={{ color: '#6b7280' }}>Chưa có giá nào được gửi.</p>}
        <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          {[...(nego.offers || [])].reverse().map((o) => (
            <li key={o.offerId} style={{ border: '1px solid #e5e7eb', borderRadius: '8px', padding: '0.6rem 0.8rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: '1rem' }}>
                <strong>{formatMoney(o.price)}</strong>
                <span style={{ fontSize: '0.85rem', color: '#6b7280' }}>{OFFER_STATUS_LABEL[o.status] || o.status}</span>
              </div>
              <div style={{ fontSize: '0.9rem', color: '#4b5563' }}>
                {SIDE_LABEL[o.side] || o.side} ({o.senderEmail}) · {formatDateTime(o.createdAt)}
              </div>
              {o.message && <div style={{ color: '#374151' }}>“{o.message}”</div>}
            </li>
          ))}
        </ul>
      </div>

      {/* Chat — nói giá trong chat không tự thành giá chính thức */}
      <div className="glass-card">
        <h3 style={{ marginTop: 0 }}>Trao đổi</h3>
        <p style={{ fontSize: '0.85rem', color: '#6b7280' }}>
          Lưu ý: giá nói trong chat chỉ thành giá chính thức khi đi qua luồng gửi giá → chấp nhận → xác nhận chốt.
        </p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginBottom: '0.75rem' }}>
          {(nego.messages || []).length === 0 && <p style={{ color: '#6b7280' }}>Chưa có tin nhắn.</p>}
          {(nego.messages || []).map((m) => {
            const mine = m.senderEmail === myEmail;
            return (
              <div
                key={m.messageId}
                style={{
                  alignSelf: mine ? 'flex-end' : 'flex-start', maxWidth: '80%',
                  background: mine ? '#dbeafe' : '#f3f4f6', borderRadius: '10px', padding: '0.5rem 0.75rem',
                }}
              >
                <div style={{ fontSize: '0.8rem', color: '#4b5563' }}>
                  {mine ? 'Bạn' : `${SIDE_LABEL[m.side] || m.side} (${m.senderEmail})`} · {formatDateTime(m.createdAt)}
                </div>
                <div>{m.content}</div>
              </div>
            );
          })}
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          <input
            value={chatText} onChange={(e) => setChatText(e.target.value)}
            placeholder="Nhập tin nhắn…" style={inputStyle}
            disabled={busy || locked || !quoted}
            onKeyDown={(e) => { if (e.key === 'Enter') sendChat(); }}
          />
          <button type="button" style={btnPrimary} onClick={sendChat} disabled={busy || locked || !quoted}>Gửi</button>
        </div>
      </div>
    </div>
  );
}
