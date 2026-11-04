/**
 * Dấu đại lý qua link (SaaS Phase 2 Track C): khi khách vào /a/<slug>, FE
 * lưu slug vào localStorage để giữ qua bước đăng nhập — mua hàng yêu cầu
 * đăng nhập nên không có bước này thì đăng nhập xong là mất dấu đại lý.
 *
 * Quy ước vòng đời (đã kiểm chứng bằng đọc code AuthContext):
 * - ĐĂNG NHẬP KHÔNG xóa agentRef: AuthContext.persist() chỉ chạm 'itoken'
 *   và 'iuser', mọi hàm login (loginPassword/loginOtp/devLogin) đều đi qua
 *   persist() nên agentRef sống qua đăng nhập.
 * - ĐĂNG XUẤT XÓA agentRef: AuthContext.logout() gọi clearAgentRef() để
 *   người dùng kế tiếp trên cùng thiết bị không bị gán nhầm đại lý của
 *   người trước. 401 toàn cục cũng đi qua logout() nên cũng xóa.
 * - Hết phiên mà không logout tường minh thì agentRef vẫn còn — server vẫn
 *   validate lại slug (ACTIVE mới gắn) nên slug cũ/hết hiệu lực không gây
 *   gán nhầm, chỉ gây lỗi 404/410 rõ ràng lúc tạo policy.
 */
export const AGENT_REF_KEY = 'agentRef';

export function getAgentRef() {
  try {
    const v = localStorage.getItem(AGENT_REF_KEY);
    return v ? v.trim().toLowerCase() : '';
  } catch {
    return '';
  }
}

export function setAgentRef(slug) {
  try {
    const v = String(slug ?? '').trim().toLowerCase();
    if (v) localStorage.setItem(AGENT_REF_KEY, v);
  } catch { /* private mode: server vẫn báo 400 rõ ràng nếu thiếu slug */ }
}

export function clearAgentRef() {
  try {
    localStorage.removeItem(AGENT_REF_KEY);
  } catch { /* ignore */ }
}
