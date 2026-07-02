import * as cheerio from "cheerio";

export function parseMxsw(html: string) {
  const $ = cheerio.load(html);

  const title =
    $("#nr_title").text().trim() ||
    $("title").text().trim();

  const content = $("#nr1")
    .html()
    ?.replace(/<br\s*\/?>/gi, "\n")
    .replace(/&nbsp;/g, " ") ?? "";

  const text = cheerio
    .load(`<div>${content}</div>`)
    .text()
    .replace(/\n{3,}/g, "\n\n")
    .trim();

  return {
    title,
    content: text,
    prev:
      $("#pb_prev").attr("href") ||
      $("#pb_prev1").attr("href") ||
      null,
    next:
      $("#pb_next").attr("href") ||
      $("#pb_next1").attr("href") ||
      null,
  };
}