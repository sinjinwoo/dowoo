// 69shuba.com / twkan.com은 동일한 사이트 엔진을 쓰고 있어서
// <script>var bookinfo = {...}</script>에 제목/이전장/다음장이 그대로 들어있다.
// DOM 선택자보다 안정적이라 이걸 우선 사용한다.
export interface Bookinfo {
  articlename: string
  chaptername: string
  previewPage: string | null
  nextPage: string | null
}

function extractField(html: string, key: string): string | null {
  const regex = new RegExp(`${key}\\s*:\\s*(?:'([^']*)'|"([^"]*)")`)
  const match = html.match(regex)
  if (!match) return null
  return match[1] ?? match[2] ?? null
}

export function extractBookinfo(html: string): Bookinfo {
  return {
    articlename: extractField(html, 'articlename') ?? '',
    chaptername: extractField(html, 'chaptername') ?? '',
    previewPage: extractField(html, 'preview_page'),
    nextPage: extractField(html, 'next_page'),
  }
}
