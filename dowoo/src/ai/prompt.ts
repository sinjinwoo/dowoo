export function resolveSystemPrompt(template: string, memo: string): string {
  return template.replaceAll('{{memo}}', memo)
}
