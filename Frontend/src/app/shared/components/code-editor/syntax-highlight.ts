/** Minimal, dependency-free syntax highlighting for Python/pytest and Robot Framework. Output is HTML-escaped. */

export type HighlightLanguage = 'PYTEST' | 'ROBOT';

const PY_KEYWORDS = new Set(['False', 'None', 'True', 'and', 'as', 'assert', 'async', 'await', 'break', 'class', 'continue',
  'def', 'del', 'elif', 'else', 'except', 'finally', 'for', 'from', 'global', 'if', 'import', 'in', 'is', 'lambda',
  'nonlocal', 'not', 'or', 'pass', 'raise', 'return', 'try', 'while', 'with', 'yield']);
const PY_BUILTINS = new Set(['print', 'len', 'range', 'str', 'int', 'float', 'list', 'dict', 'set', 'tuple', 'bool',
  'any', 'all', 'min', 'max', 'sum', 'sorted', 'enumerate', 'zip', 'isinstance', 'self', 'device']);

export function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function span(cls: string, text: string): string {
  return `<span class="${cls}">${escapeHtml(text)}</span>`;
}

const PY_TOKEN = new RegExp([
  '(#[^\\n]*)',                                                                       // 1 comment
  '((?:[rRbBuUfF]{1,2})?(?:"""[\\s\\S]*?(?:"""|$)|\'\'\'[\\s\\S]*?(?:\'\'\'|$)|"(?:\\\\.|[^"\\\\\\n])*"?|\'(?:\\\\.|[^\'\\\\\\n])*\'?))', // 2 string
  '(@[A-Za-z_][\\w.]*)',                                                              // 3 decorator
  '(\\b\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b)',                                 // 4 number
  '([A-Za-z_]\\w*)'                                                                   // 5 identifier
].join('|'), 'g');

export function highlightPython(code: string): string {
  let out = '';
  let last = 0;
  let afterDef = false;
  PY_TOKEN.lastIndex = 0;
  for (let m = PY_TOKEN.exec(code); m; m = PY_TOKEN.exec(code)) {
    if (m[0] === '') { PY_TOKEN.lastIndex++; continue; }
    out += escapeHtml(code.slice(last, m.index));
    last = m.index + m[0].length;
    if (m[1]) out += span('tok-comment', m[1]);
    else if (m[2]) out += span('tok-string', m[2]);
    else if (m[3]) out += span('tok-decorator', m[3]);
    else if (m[4]) out += span('tok-number', m[4]);
    else if (m[5]) {
      const word = m[5];
      if (afterDef) { out += span('tok-function', word); afterDef = false; continue; }
      if (PY_KEYWORDS.has(word)) { out += span('tok-keyword', word); afterDef = word === 'def' || word === 'class'; continue; }
      out += PY_BUILTINS.has(word) ? span('tok-builtin', word) : escapeHtml(word);
    }
  }
  return out + escapeHtml(code.slice(last));
}

const ROBOT_VARIABLE = /([$@&%]\{[^}\n]*\}?)/g;

function robotCell(text: string, cls: string | null): string {
  let out = '';
  let last = 0;
  ROBOT_VARIABLE.lastIndex = 0;
  for (let m = ROBOT_VARIABLE.exec(text); m; m = ROBOT_VARIABLE.exec(text)) {
    if (m.index > 0 && text[m.index - 1] === '\\') continue;   // escaped: literal text
    const before = text.slice(last, m.index);
    out += cls ? (before ? span(cls, before) : '') : escapeHtml(before);
    out += span('tok-variable', m[0]);
    last = m.index + m[0].length;
  }
  const rest = text.slice(last);
  return out + (cls ? (rest ? span(cls, rest) : '') : escapeHtml(rest));
}

export function highlightRobot(code: string): string {
  let section = '';
  return code.split('\n').map(line => {
    const header = /^\s*\*{1,3}\s*([^*]+?)\s*\*{0,3}\s*$/.exec(line);
    if (header && line.trimStart().startsWith('*')) {
      section = header[1].toLowerCase();
      return span('tok-section', line);
    }
    if (/^\s*#/.test(line)) return span('tok-comment', line);
    // Cells are separated by two or more spaces (or a tab)
    const parts = line.split(/( {2,}|\t+)/);
    const indented = parts[0] === '' && parts.length > 1;
    let out = '';
    let cellIndex = 0;
    for (let i = 0; i < parts.length; i++) {
      const part = parts[i];
      if (/^( {2,}|\t+)$/.test(part)) { out += part; continue; }
      if (part === '') continue;
      if (part.startsWith('#')) return out + span('tok-comment', parts.slice(i).join(''));
      let cls: string | null = null;
      if (!indented && cellIndex === 0) {
        cls = section.startsWith('setting') ? 'tok-keyword' : section.startsWith('test case') || section.startsWith('task')
          || section.startsWith('keyword') ? 'tok-function' : null;
      } else if (indented && cellIndex === 0) {
        cls = /^\[.*\]$/.test(part) ? 'tok-keyword' : /^(FOR|END|IF|ELSE|ELSE IF|WHILE|TRY|EXCEPT|FINALLY|BREAK|CONTINUE|RETURN|IN|IN RANGE)$/.test(part)
          ? 'tok-keyword' : 'tok-builtin';
      } else if (/^-?\d+(\.\d+)?$/.test(part)) {
        cls = 'tok-number';
      } else if (/^\w+=/.test(part)) {
        const eq = part.indexOf('=');
        out += span('tok-decorator', part.slice(0, eq + 1)) + robotCell(part.slice(eq + 1), 'tok-string');
        cellIndex++;
        continue;
      }
      out += robotCell(part, cls ?? (cellIndex > 0 ? 'tok-string' : null));
      cellIndex++;
    }
    return out;
  }).join('\n');
}

export function highlight(code: string, language: HighlightLanguage): string {
  return language === 'ROBOT' ? highlightRobot(code) : highlightPython(code);
}
