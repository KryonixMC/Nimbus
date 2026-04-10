"use client";

import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { apiFetch } from "@/lib/api";
import { cn } from "@/lib/utils";
import { ChevronDownIcon, ExternalLinkIcon, Loader2 } from "@/lib/icons";

interface ChangelogEntry {
  version: string;
  title: string;
  body: string;
}

interface ChangelogResponse {
  entries: ChangelogEntry[];
}

const FULL_CHANGELOG_URL =
  "https://cloud.nimbuspowered.org/docs/project/changelog";

function extractDate(title: string): string | null {
  const match = title.match(/\(([^)]+)\)\s*$/);
  return match ? match[1] : null;
}

/**
 * Minimal markdown renderer — handles ### subheadings, bullet lists,
 * **bold** and [text](url) links. Matches docs/content/docs/project/changelog.mdx.
 */
function renderMarkdown(md: string): React.ReactNode[] {
  const lines = md.split("\n");
  const nodes: React.ReactNode[] = [];
  let listBuffer: React.ReactNode[] = [];
  let key = 0;

  const flushList = () => {
    if (listBuffer.length > 0) {
      nodes.push(
        <ul
          key={`ul-${key++}`}
          className="list-disc space-y-1.5 pl-5 text-sm text-muted-foreground"
        >
          {listBuffer}
        </ul>
      );
      listBuffer = [];
    }
  };

  const renderInline = (text: string, baseKey: number): React.ReactNode[] => {
    const parts: React.ReactNode[] = [];
    const regex = /(\*\*[^*]+\*\*|\[[^\]]+\]\([^)]+\))/g;
    let lastIndex = 0;
    let match: RegExpExecArray | null;
    let i = 0;
    while ((match = regex.exec(text)) !== null) {
      if (match.index > lastIndex) parts.push(text.slice(lastIndex, match.index));
      const token = match[0];
      if (token.startsWith("**")) {
        parts.push(
          <strong
            key={`${baseKey}-b-${i}`}
            className="font-semibold text-foreground"
          >
            {token.slice(2, -2)}
          </strong>
        );
      } else {
        const m = /\[([^\]]+)\]\(([^)]+)\)/.exec(token)!;
        parts.push(
          <a
            key={`${baseKey}-l-${i}`}
            href={m[2]}
            target="_blank"
            rel="noopener noreferrer"
            className="text-primary underline underline-offset-2"
          >
            {m[1]}
          </a>
        );
      }
      lastIndex = match.index + token.length;
      i++;
    }
    if (lastIndex < text.length) parts.push(text.slice(lastIndex));
    return parts;
  };

  for (const rawLine of lines) {
    const line = rawLine.trimEnd();
    if (line.startsWith("### ")) {
      flushList();
      nodes.push(
        <h4
          key={`h-${key++}`}
          className="font-heading mt-5 mb-2 text-xs font-semibold uppercase tracking-wider text-foreground first:mt-0"
        >
          {line.slice(4)}
        </h4>
      );
    } else if (line.startsWith("- ")) {
      listBuffer.push(
        <li key={`li-${key++}`} className="leading-relaxed">
          {renderInline(line.slice(2), key)}
        </li>
      );
    } else if (line.length === 0) {
      flushList();
    } else {
      flushList();
      nodes.push(
        <p
          key={`p-${key++}`}
          className="text-sm leading-relaxed text-muted-foreground"
        >
          {renderInline(line, key)}
        </p>
      );
    }
  }
  flushList();
  return nodes;
}

export function ChangelogCard() {
  const [latest, setLatest] = useState<ChangelogEntry | null>(null);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [open, setOpen] = useState(false);

  // Fetch only on first expand — avoids hitting the GitHub-backed endpoint on
  // every dashboard visit when the user doesn't care about release notes.
  useEffect(() => {
    if (!open || loaded) return;
    setLoading(true);
    apiFetch<ChangelogResponse>("/api/controller/changelog")
      .then((data) => setLatest(data.entries[0] ?? null))
      .catch(() => {})
      .finally(() => {
        setLoading(false);
        setLoaded(true);
      });
  }, [open, loaded]);

  return (
    <Card>
      <Collapsible open={open} onOpenChange={setOpen}>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <CollapsibleTrigger
              render={
                <button
                  type="button"
                  className="flex flex-1 items-center gap-2 text-left cursor-pointer focus-visible:outline-none"
                />
              }
            >
              What&apos;s new
              {latest && (
                <Badge variant="outline" className="font-mono">
                  v{latest.version}
                </Badge>
              )}
              <ChevronDownIcon
                className={cn(
                  "ml-1 size-4 text-muted-foreground transition-transform",
                  open && "rotate-180"
                )}
              />
            </CollapsibleTrigger>
            <Button
              variant="outline"
              size="sm"
              className="ml-auto"
              nativeButton={false}
              render={
                <a
                  href={FULL_CHANGELOG_URL}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Full changelog
                  <ExternalLinkIcon className="ml-1 size-3.5" />
                </a>
              }
            />
          </CardTitle>
        </CardHeader>
        <CollapsibleContent>
          <CardContent>
            {loading ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="size-4 animate-spin" /> Loading latest release...
              </div>
            ) : !latest ? (
              <div className="text-sm text-muted-foreground">
                No release notes found.
              </div>
            ) : (
              <>
                {extractDate(latest.title) && (
                  <p className="mb-4 text-xs text-muted-foreground">
                    Released {extractDate(latest.title)}
                  </p>
                )}
                <div className="space-y-1">{renderMarkdown(latest.body)}</div>
              </>
            )}
          </CardContent>
        </CollapsibleContent>
      </Collapsible>
    </Card>
  );
}
