/**
 * Shiki transformer that colorizes Nimbus CLI output in code blocks
 * with title containing "Nimbus". Uses postprocess hook to ensure
 * it runs after all other transformations.
 */
import type { ShikiTransformer } from 'shiki';

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

type Segment = { text: string; color?: string };

function colorizeLine(line: string): Segment[] {
  const segments: Segment[] = [];
  let rest = line;

  function push(text: string, color?: string) {
    if (text) segments.push({ text, color });
  }

  // Timestamp: [HH:MM:SS]
  const tsMatch = rest.match(/^(\[[\d:]+\])/);
  if (tsMatch) {
    push(tsMatch[1], C.dim);
    rest = rest.slice(tsMatch[1].length);
  }

  // Whitespace
  const wsMatch = rest.match(/^(\s+)/);
  if (wsMatch) {
    push(wsMatch[1]);
    rest = rest.slice(wsMatch[1].length);
  }

  const patterns: [RegExp, (m: RegExpMatchArray) => void][] = [
    [/^(● READY)(.*)$/, (m) => { push(m[1], C.green); rest = m[2]; }],
    [/^(▲ STARTING)(.*)$/, (m) => { push(m[1], C.yellow); rest = m[2]; }],
    [/^(▼ STOPPING)(.*)$/, (m) => { push(m[1], C.yellow); rest = m[2]; }],
    [/^(● STARTING)(.*)$/, (m) => { push(m[1], C.yellow); rest = m[2]; }],
    [/^(○ STOPPED)(.*)$/, (m) => { push(m[1], C.dim); rest = m[2]; }],
    [/^(✖ CRASHED)(.*)$/, (m) => { push(m[1], C.red); rest = m[2]; }],
    [/^(◉ DRAINING)(.*)$/, (m) => { push(m[1], C.magenta); rest = m[2]; }],
    [/^(↑ SCALE UP)(.*)$/, (m) => { push(m[1], C.green); rest = m[2]; }],
    [/^(↓ SCALE DOWN)(.*)$/, (m) => { push(m[1], C.yellow); rest = m[2]; }],
    [/^(nimbus)( »)(.*)$/, (m) => { push(m[1], C.brightCyan); push(m[2], C.cyan); rest = m[3]; }],
    [/^(──.+)$/, (m) => { push(m[1], C.cyan); rest = ''; }],
    [/^(─{4,})$/, (m) => { push(m[1], C.dim); rest = ''; }],
    [/^(▸)(.*)$/, (m) => { push(m[1], C.cyan); rest = m[2]; }],
    [/^(✓)(.*)$/, (m) => { push(m[1], C.green); rest = m[2]; }],
    [/^(✗)(.*)$/, (m) => { push(m[1], C.red); rest = m[2]; }],
    [/^(ℹ)(.*)$/, (m) => { push(m[1], C.cyan); rest = m[2]; }],
    [/^(⚠)(.*)$/, (m) => { push(m[1], C.yellow); rest = m[2]; }],
    [/^(!)(.+)$/, (m) => { push(m[1], C.yellow); rest = m[2]; }],
    [/^(↑)(.*)$/, (m) => { push(m[1], C.green); rest = m[2]; }],
    [/^(↓)(.*)$/, (m) => { push(m[1], C.cyan); rest = m[2]; }],
    [/^(⚡)(.*)$/, (m) => { push(m[1], C.magenta); rest = m[2]; }],
    [/^(↻)(.*)$/, (m) => { push(m[1], C.cyan); rest = m[2]; }],
    [/^(◆)(.*)$/, (m) => { push(m[1], C.brightCyan); rest = m[2]; }],
    [/^(◇)(.*)$/, (m) => { push(m[1], C.cyan); rest = m[2]; }],
    [/^(◈)(.*)$/, (m) => { push(m[1], C.cyan); rest = m[2]; }],
    [/^(\+)(.*)$/, (m) => { push(m[1], C.green); rest = m[2]; }],
    [/^(█+)(░*)(.*)$/, (m) => { push(m[1], C.green); if (m[2]) push(m[2], C.dim); rest = m[3]; }],
  ];

  let matched = false;
  for (const [pattern, handler] of patterns) {
    const m = rest.match(pattern);
    if (m) { handler(m); matched = true; break; }
  }

  // Remaining: colorize (parenthesized) as dim
  if (rest) {
    let pos = 0;
    const re = /\([^)]+\)/g;
    let pm;
    while ((pm = re.exec(rest)) !== null) {
      if (pm.index > pos) push(rest.slice(pos, pm.index));
      push(pm[0], C.dim);
      pos = pm.index + pm[0].length;
    }
    if (pos < rest.length) push(rest.slice(pos));
  }

  return segments.length > 0 ? segments : [{ text: line }];
}

function getTextContent(node: any): string {
  if (node.type === 'text') return node.value ?? '';
  if (node.children) return node.children.map(getTextContent).join('');
  return '';
}

function visitElement(node: any, fn: (el: any) => void) {
  if (node.type === 'element') fn(node);
  if (node.children) {
    for (const child of node.children) visitElement(child, fn);
  }
}

export function transformerNimbus(): ShikiTransformer {
  return {
    name: 'nimbus-colorizer',
    // Use root hook to operate on the final HAST tree
    root(root) {
      const meta: any = this.options.meta;
      const title: string = meta?.title ?? '';
      if (!title.startsWith('Nimbus')) return;

      // Find all .line spans and colorize their text content
      visitElement(root, (el) => {
        const cls = el.properties?.class;
        if (!cls || !String(cls).includes('line')) return;
        if (el.tagName !== 'span') return;

        const text = getTextContent(el);
        if (!text) return;

        const segments = colorizeLine(text);
        if (!segments.some((s) => s.color)) return;

        el.children = segments.map((seg) =>
          seg.color
            ? {
                type: 'element',
                tagName: 'span',
                properties: { style: `color:${seg.color}` },
                children: [{ type: 'text', value: seg.text }],
              }
            : { type: 'text', value: seg.text },
        );
      });
    },
  };
}
