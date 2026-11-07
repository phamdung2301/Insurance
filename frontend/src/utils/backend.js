/**
 * Backend chưa triển khai nhiều endpoint SRS (transitions, endorsements,
 * history, versions, excel, reports, benchmark). Helper này giúp UI phân biệt
 * "backend chưa có" (404) với lỗi thật để hiển thị thông điệp phù hợp.
 */
export function isBackendMissing(err) {
  return err?.status === 404;
}

export const BACKEND_TODO_HINT =
  'Tính năng này hiện chưa khả dụng. Vui lòng thử lại sau hoặc liên hệ bộ phận hỗ trợ.';
