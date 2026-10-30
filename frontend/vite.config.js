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
const PAGE_PREFIXES = ['/hop-dong/', '/admin/'];

function isPageRoute(urlPath) {
  if (PAGE_ROUTES.includes(urlPath)) return true;
  return PAGE_PREFIXES.some((p) => urlPath.startsWith(p));
}

const API_PREFIXES = ['/auth', '/user', '/policies', '/excel', '/reports', '/benchmark', '/api', '/oauth2', '/login/oauth2', '/endorsements', '/history', '/versions', '/admin'];

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
      '/excel': { target: 'http://localhost:8080', changeOrigin: true },
      '/reports': { target: 'http://localhost:8080', changeOrigin: true },
      '/benchmark': { target: 'http://localhost:8080', changeOrigin: true },
      '/oauth2': { target: 'http://localhost:8080', changeOrigin: true },
      '/login/oauth2': { target: 'http://localhost:8080', changeOrigin: true },
      '/endorsements': { target: 'http://localhost:8080', changeOrigin: true },
      '/history': { target: 'http://localhost:8080', changeOrigin: true },
      '/versions': { target: 'http://localhost:8080', changeOrigin: true },
      '/admin': { target: 'http://localhost:8080', changeOrigin: true },
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  preview: {
    port: 5173,
  },
});
