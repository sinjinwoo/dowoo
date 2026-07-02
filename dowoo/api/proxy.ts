import { fetchViaProxy } from '../src/crawl/proxyFetch'

export const config = { runtime: 'edge' }

export default async function handler(request: Request): Promise<Response> {
  const targetUrl = new URL(request.url).searchParams.get('url')

  if (!targetUrl) {
    return new Response('url 쿼리 파라미터가 필요합니다.', { status: 400 })
  }

  try {
    const html = await fetchViaProxy(targetUrl)
    return new Response(html, {
      status: 200,
      headers: {
        'Content-Type': 'text/plain; charset=utf-8',
        'Access-Control-Allow-Origin': '*',
      },
    })
  } catch (error) {
    return new Response(error instanceof Error ? error.message : String(error), { status: 400 })
  }
}
