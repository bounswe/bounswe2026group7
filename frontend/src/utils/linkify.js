/**
 * Minimal URL auto-linkifier for chat message bodies.
 *
 * Scans `text` for http(s) URLs and returns an array of strings and anchor
 * elements suitable for rendering inside JSX (e.g. {linkify(message)}).
 *
 * Why not a library: the message text is short, the regex below covers the
 * cases that show up in chat (full URLs, optional path/query/fragment), and
 * keeping this in-tree avoids pulling in another dependency.
 */

// Matches http:// or https:// followed by at least one non-whitespace char.
// Trailing punctuation that's commonly NOT part of the URL (`.`, `,`, `)`,
// `!`, `?`, `:`, `;`) gets trimmed and pushed back into the text segment so
// "see https://example.com." doesn't include the period in the link.
const URL_REGEX = /https?:\/\/\S+/gi
const TRAILING_PUNCT = /[.,!?;:)\]]+$/

export function linkify(text) {
  if (!text) return []
  const parts = []
  let lastIndex = 0
  for (const match of text.matchAll(URL_REGEX)) {
    const start = match.index
    let url = match[0]
    let trailing = ''
    const punctMatch = url.match(TRAILING_PUNCT)
    if (punctMatch) {
      trailing = punctMatch[0]
      url = url.slice(0, url.length - trailing.length)
    }
    if (start > lastIndex) {
      parts.push(text.slice(lastIndex, start))
    }
    parts.push({ kind: 'url', url })
    if (trailing) parts.push(trailing)
    lastIndex = start + match[0].length
  }
  if (lastIndex < text.length) {
    parts.push(text.slice(lastIndex))
  }
  return parts
}
