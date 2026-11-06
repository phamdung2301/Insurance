import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Frontend page routes that must be served as index.html (SPA fallback).
// Prevents Vite dev proxy from forwarding page navigations to the backend
// when a page route shares a prefix with an API route (e.g. /hop-dong/* vs /policies/*).
const PAGE_ROUTES = [
  '/',
  '/login',
  '/mua-bao-hiem',
  '/ho-so',
  '/doi-mat-khau',
  '/admin',
  '/agent/khach-hang',
  '/dev/api-tester',
];
const PAGE_PREFIXES = ['/hop-dong/', '/admin/', '/a/'];

function isPageRoute(urlPath) {
  if (PAGE_ROUTES.includes(urlPath)) return true;
  return PAGE_PREFIXES.some((p) => urlPath.startsWith(p));
}

const API_PREFIXES = ['/auth', '/user', '/policies', '/payments', '/pending-endorsements', '/underwriter', '/excel', '/reports', '/benchmark', '/api', '/oauth2', '/login/oauth2', '/endorsements', '/history', '/versions', '/admin', '/agents'];

function spaBypassMiddleware() {
  return {
    name: 'spa-bypass',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const urlPath = (req.url || '/').split('?')[0];
        // Let real API calls and static assets pass through to proxy / file handling
        if (API_PREFIXES.some((p) => urlPath === p || urlPath.startsWith(p + '/'))) return next();
        if (/\.[a-zA-Z0-9]+$/.test(urlPath)) return next();
        if (isPageRoute(urlPath)) {
          // In dev, let Vite transform index.html properly
          req.url = '/index.html';
          return next();
        }
        return next();
      });
    },
  };
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), spaBypassMiddleware()],
  server: {
    port: 5173,
    proxy: {
      '/auth': { target: 'http://localhost:8080', changeOrigin: true },
      '/user': { target: 'http://localhost:8080', changeOrigin: true },
      '/policies': { target: 'http://localhost:8080', changeOrigin: true },
      // Thanh toán SePay (GET /payments/status, POST /payments/{txnRef}/simulate):
      // từng thiếu ở đây nên ở dev POST simulate rơi vào Vite 404 (request
      // không tới backend, toast chỉ hiện "Request failed with status code
      // 404"), còn GET status rơi về SPA fallback (trả index.html 200) và
      // polling nhận HTML thay JSON nên modal không bao giờ tự đóng — cùng
      // họ lỗi từng gặp ở /underwriter (thiếu proxy → list rỗng lặng).
      '/payments': { target: 'http://localhost:8080', changeOrigin: true },
      // Hàng đợi endorsement chờ duyệt (GET /pending-endorsements): cùng họ
      // lỗi — không có entry này thì ở dev request trả index.html 200 và
      // trang hiện rỗng lặng.
      '/pending-endorsements': { target: 'http://localhost:8080', changeOrigin: true },
      // Hàng đợi thương lượng UW (GET /underwriter/negotiations): từng thiếu
      // ở đây nên ở dev request rơi về SPA fallback (trả index.html 200) và
      // tab /admin/thuong-luong hiện rỗng dù DB đã có bản ghi.
      '/underwriter': { target: 'http://localhost:8080', changeOrigin: true },
      '/excel': { target: 'http://localhost:8080', changeOrigin: true },
      '/reports': { target: 'http://localhost:8080', changeOrigin: true },
      '/benchmark': { target: 'http://localhost:8080', changeOrigin: true },
      '/oauth2': { target: 'http://localhost:8080', changeOrigin: true },
      '/login/oauth2': { target: 'http://localhost:8080', changeOrigin: true },
      '/endorsements': { target: 'http://localhost:8080', changeOrigin: true },
      '/history': { target: 'http://localhost:8080', changeOrigin: true },
      '/versions': { target: 'http://localhost:8080', changeOrigin: true },
      '/admin': { target: 'http://localhost:8080', changeOrigin: true },
      // Track C: endpoint public resolve slug (GET /agents/public/slug/:slug).
      // Không có entry này thì request dev rơi về SPA fallback (trả index.html
      // 200) và storefront không bao giờ resolve được — cùng họ lỗi từng gặp
      // ở /underwriter (thiếu proxy → list rỗng lặng).
      '/agents': { target: 'http://localhost:8080', changeOrigin: true },
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  preview: {
    port: 5173,
  },
});
