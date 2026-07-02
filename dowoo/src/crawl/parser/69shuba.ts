import * as cheerio from 'cheerio'
import { extractBookinfo } from './shubaFamily'

export function parse69shuba(html: string) {
  const bookinfo = extractBookinfo(html)
  const $ = cheerio.load(html)

  const title = bookinfo.chaptername || $('h1.hide720').first().text().trim()

  // 본문은 전용 컨테이너 없이 .txtnav 안에 광고 div와 뒤섞인 채로 들어있어서,
  // 알려진 비-본문 요소를 제거한 뒤 남은 텍스트만 뽑아낸다.
  const container = $('.txtnav').clone()
  container.find('h1, .txtinfo, #txtright, .contentadv, .bottom-ad, .page1, script, ins').remove()

  const contentHtml = (container.html() ?? '').replace(/<br\s*\/?>/gi, '\n').replace(/&nbsp;/g, ' ')

  const content = cheerio
    .load(`<div>${contentHtml}</div>`)
    .text()
    .replace(/\n{3,}/g, '\n\n')
    .trim()

  return {
    title,
    content,
    prev: bookinfo.previewPage,
    next: bookinfo.nextPage,
  }
}
