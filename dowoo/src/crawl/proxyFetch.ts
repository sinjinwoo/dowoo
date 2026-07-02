const ALLOWED_HOSTS = ['ixdzs8.com', 'm.xsw.tw', '69shuba.com', 'www.69shuba.com', 'twkan.com']

function isAllowedHost(hostname: string): boolean {
  return ALLOWED_HOSTS.some((host) => hostname === host || hostname.endsWith(`.${host}`))
}

export async function fetchViaProxy(targetUrl: string): Promise<string> {
  let parsed: URL
  try {
    parsed = new URL(targetUrl)
  } catch {
    throw new Error('올바른 URL 형식이 아닙니다.')
  }

  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error('http/https 주소만 지원합니다.')
  }

  if (!isAllowedHost(parsed.hostname)) {
    throw new Error(`지원하지 않는 사이트입니다: ${parsed.hostname}`)
  }

  const response = await fetch(parsed.toString(), {
    headers: {
      'User-Agent':
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36',
      Accept: 'text/html,application/xhtml+xml',
    },
  })

  if (!response.ok) {
    throw new Error(`대상 사이트 응답 오류: ${response.status}`)
  }

  return response.text()
}
