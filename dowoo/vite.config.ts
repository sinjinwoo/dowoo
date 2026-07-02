import { defineConfig, type Plugin } from 'vite'
import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'
import tailwindcss from '@tailwindcss/vite'
import { fetchViaProxy } from './src/crawl/proxyFetch'

// vite dev 서버에서도 /api/proxy를 실제 Vercel 서버리스 함수(api/proxy.ts)와
// 동일하게 동작시켜서, `vercel dev` 없이 `npm run dev`만으로 크롤링 프록시를 테스트할 수 있게 함
function apiProxyDevMiddleware(): Plugin {
  return {
    name: 'api-proxy-dev-middleware',
    configureServer(server) {
      server.middlewares.use('/api/proxy', async (req, res) => {
        const url = new URL(req.url ?? '', 'http://localhost')
        const targetUrl = url.searchParams.get('url')

        if (!targetUrl) {
          res.statusCode = 400
          res.end('url 쿼리 파라미터가 필요합니다.')
          return
        }

        try {
          const html = await fetchViaProxy(targetUrl)
          res.setHeader('Content-Type', 'text/plain; charset=utf-8')
          res.setHeader('Access-Control-Allow-Origin', '*')
          res.end(html)
        } catch (error) {
          res.statusCode = 400
          res.end(error instanceof Error ? error.message : String(error))
        }
      })
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    babel({ presets: [reactCompilerPreset()] }),
    tailwindcss(),
    apiProxyDevMiddleware(),
  ],
})
