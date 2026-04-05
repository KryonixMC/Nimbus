/**
 * Rehype plugin that colorizes Nimbus CLI output.
 * Runs AFTER Shiki/rehypeCode so it operates on the final HAST.
 * Targets <figure> code blocks where data-title starts with "Nimbus".
 */
import type { Root } from 'hast';
import type { Plugin } from 'unified';

const C = {
  green: '#4ade80',
  yellow: '#facc15',
  red: '#f87171',
  cyan: '#22d3ee',
  brightCyan: '#67e8f9',
  blue: '#60a5fa',
  magenta: '#c084fc',
  dim: '#6b7280',
};

type Seg = { text: string; color?: string };

function colorizeLine(line: string): Seg[] {
  const out: Seg[] = [];
  let rest = line;
  const p = (t: string, c?: string) => { if (t) out.push({ text: t, color: c }); };

  // Timestamp
  const ts = rest.match(/^(\[[\d:]+\])/);
  if (ts) { p(ts[1], C.dim); rest = rest.slice(ts[1].length); }

  // Whitespace
  const ws = rest.match(/^(\s+)/);
  if (ws) { p(ws[1]); rest = rest.slice(ws[1].length); }

  const rules: [RegExp, (m: RegExpMatchArray) => void][] = [
    [/^(● READY)(.*)$/, m => { p(m[1], C.green); rest = m[2]; }],
    [/^(▲ STARTING)(.*)$/, m => { p(m[1], C.yellow); rest = m[2]; }],
    [/^(▼ STOPPING)(.*)$/, m => { p(m[1], C.yellow); rest = m[2]; }],
    [/^(● STARTING)(.*)$/, m => { p(m[1], C.yellow); rest = m[2]; }],
    [/^(○ STOPPED)(.*)$/, m => { p(m[1], C.dim); rest = m[2]; }],
    [/^(✖ CRASHED)(.*)$/, m => { p(m[1], C.red); rest = m[2]; }],
    [/^(◉ DRAINING)(.*)$/, m => { p(m[1], C.magenta); rest = m[2]; }],
    [/^(↑ SCALE UP)(.*)$/, m => { p(m[1], C.green); rest = m[2]; }],
    [/^(↓ SCALE DOWN)(.*)$/, m => { p(m[1], C.yellow); rest = m[2]; }],
    [/^(nimbus)( »)(.*)$/, m => { p(m[1], C.brightCyan); p(m[2], C.cyan); rest = m[3]; }],
    [/^(──.+)$/, m => { p(m[1], C.cyan); rest = ''; }],
    [/^(─{4,})$/, m => { p(m[1], C.dim); rest = ''; }],
    [/^(▸)(.*)$/, m => { p(m[1], C.cyan); rest = m[2]; }],
    [/^(✓)(.*)$/, m => { p(m[1], C.green); rest = m[2]; }],
    [/^(✗)(.*)$/, m => { p(m[1], C.red); rest = m[2]; }],
    [/^(ℹ)(.*)$/, m => { p(m[1], C.cyan); rest = m[2]; }],
    [/^(⚠)(.*)$/, m => { p(m[1], C.yellow); rest = m[2]; }],
    [/^(!)(.+)$/, m => { p(m[1], C.yellow); rest = m[2]; }],
    [/^(↑)(.*)$/, m => { p(m[1], C.green); rest = m[2]; }],
    [/^(↓)(.*)$/, m => { p(m[1], C.cyan); rest = m[2]; }],
    [/^(⚡)(.*)$/, m => { p(m[1], C.magenta); rest = m[2]; }],
    [/^(↻)(.*)$/, m => { p(m[1], C.cyan); rest = m[2]; }],
    [/^(◆)(.*)$/, m => { p(m[1], C.brightCyan); rest = m[2]; }],
    [/^(◇)(.*)$/, m => { p(m[1], C.cyan); rest = m[2]; }],
    [/^(◈)(.*)$/, m => { p(m[1], C.cyan); rest = m[2]; }],
    [/^(\+)(.*)$/, m => { p(m[1], C.green); rest = m[2]; }],
    [/^(█+)(░*)(.*)$/, m => { p(m[1], C.green); if (m[2]) p(m[2], C.dim); rest = m[3]; }],
  ];

  for (const [re, fn] of rules) {
    const m = rest.match(re);
    if (m) { fn(m); break; }
  }

  // Remaining: dim (parenthesized)
  if (rest) {
    let pos = 0;
    const re = /\([^)]+\)/g;
    let m;
    while ((m = re.exec(rest)) !== null) {
      if (m.index > pos) p(rest.slice(pos, m.index));
      p(m[0], C.dim);
      pos = m.index + m[0].length;
    }
    if (pos < rest.length) p(rest.slice(pos));
  }

  return out.length ? out : [{ text: line }];
}

function getText(node: any): string {
  if (node.type === 'text') return node.value ?? '';
  if (node.children) return node.children.map(getText).join('');
  return '';
}

function walk(node: any, fn: (n: any) => void) {
  fn(node);
  if (node.children) for (const c of node.children) walk(c, fn);
}

function isNimbusFigure(node: any): boolean {
  if (node.type !== 'element' || node.tagName !== 'figure') return false;
  // Fumadocs CodeBlock renders <figure> with class containing "shiki"
  const cls = String(node.properties?.className ?? node.properties?.class ?? '');
  if (!cls.includes('shiki')) return false;
  // Check if the title (in <figcaption>) starts with "Nimbus"
  let title = '';
  walk(node, (n: any) => {
    if (n.type === 'element' && n.tagName === 'figcaption') {
      title = getText(n);
    }
  });
  return title.startsWith('Nimbus');
}

export const rehypeNimbus: Plugin<[], Root> = () => {
  return (tree) => {
    walk(tree, (node: any) => {
      if (!isNimbusFigure(node)) return;

      // Find all span.line inside this figure and colorize
      walk(node, (el: any) => {
        if (el.type !== 'element' || el.tagName !== 'span') return;
        const cls = String(el.properties?.className ?? el.properties?.class ?? '');
        if (!cls.includes('line')) return;

        const text = getText(el);
        if (!text) return;

        const segs = colorizeLine(text);
        if (!segs.some(s => s.color)) return;

        el.children = segs.map(s =>
          s.color
            ? {
                type: 'element',
                tagName: 'span',
                properties: { style: `color:${s.color}` },
                children: [{ type: 'text', value: s.text }],
              }
            : { type: 'text', value: s.text },
        );
      });
    });
  };
};
