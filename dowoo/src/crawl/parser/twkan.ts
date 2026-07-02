import * as cheerio from 'cheerio'
import { extractBookinfo } from './shubaFamily'

export function parseTwkan(html: string) {
  const bookinfo = extractBookinfo(html)
  const $ = cheerio.load(html)

  const contentContainer = $('#txtcontent0').length > 0 ? $('#txtcontent0') : $('#txtcontent1')
  const container = contentContainer.clone()
  container.find('script, ins, .contentadv, .bottom-ad').remove()

  const contentHtml = (container.html() ?? '').replace(/<br\s*\/?>/gi, '\n').replace(/&nbsp;/g, ' ')

  const content = cheerio
    .load(`<div>${contentHtml}</div>`)
    .text()
    .replace(/\n{3,}/g, '\n\n')
    .trim()

  return {
    title: bookinfo.chaptername,
    content,
    prev: bookinfo.previewPage,
    next: bookinfo.nextPage,
  }
}
