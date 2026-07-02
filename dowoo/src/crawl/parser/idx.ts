import * as cheerio from "cheerio";

export function parseIdxzs(html: string) {
  const $ = cheerio.load(html);

  const title =
    $(".page-d-name").first().text().trim() ||
    $("article h3").first().text().trim();

  const content = $("article section")
    .find("p")
    .map((_, el) => $(el).text().trim())
    .get()
    .join("\n\n");

  return {
    title,
    content,
    prev: $(".chapter-pre").attr("href") ?? null,
    next: $(".chapter-next").attr("href") ?? null,
  };
}