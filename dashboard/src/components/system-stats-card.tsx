"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { Cpu, MemoryStick, Server } from "@/lib/icons";

export interface SystemInfo {
  hostname: string;
  osName: string;
  osVersion: string;
  osArch: string;
  cpuModel: string;
  availableProcessors: number;
  systemCpuLoad: number; // 0.0–1.0, -1 if unavailable
  processCpuLoad: number; // 0.0–1.0, -1 if unavailable
  systemMemoryUsedMb: number;
  systemMemoryTotalMb: number;
  javaVersion: string;
  javaVendor: string;
}

function formatMb(mb: number): string {
  if (mb >= 1024) return `${(mb / 1024).toFixed(1)} GB`;
  return `${mb} MB`;
}

function formatLoad(load: number): string {
  if (load < 0 || !Number.isFinite(load)) return "n/a";
  return `${Math.round(load * 100)}%`;
}

function barColor(pct: number): string {
  if (pct > 90) return "bg-destructive";
  if (pct > 75) return "bg-yellow-500";
  return "bg-primary";
}

function Bar({ pct, className }: { pct: number; className?: string }) {
  return (
    <div className={cn("h-2 rounded-full bg-muted overflow-hidden", className)}>
      <div
        className={cn("h-full transition-all", barColor(pct))}
        style={{ width: `${Math.max(0, Math.min(100, pct))}%` }}
      />
    </div>
  );
}

interface SystemStatsCardProps {
  /** Card title shown in the header. */
  title: string;
  /** Optional subtitle (e.g. hostname, node id). */
  subtitle?: string;
  system: SystemInfo;
  /** Optional right-side header content (e.g. a version badge). */
  headerRight?: React.ReactNode;
}

/**
 * Compact system telemetry card — CPU, memory, specs. Used for both the controller
 * host on the Overview page and each remote agent node on the Nodes page.
 */
export function SystemStatsCard({
  title,
  subtitle,
  system,
  headerRight,
}: SystemStatsCardProps) {
  const cpuPct =
    system.systemCpuLoad >= 0 ? Math.round(system.systemCpuLoad * 100) : -1;
  const memPct =
    system.systemMemoryTotalMb > 0
      ? Math.round((system.systemMemoryUsedMb / system.systemMemoryTotalMb) * 100)
      : 0;

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-2">
        <div className="space-y-1">
          <CardTitle className="flex items-center gap-2">
            <Server className="size-4 text-muted-foreground" />
            {title}
          </CardTitle>
          {subtitle && (
            <p className="text-xs text-muted-foreground font-mono">{subtitle}</p>
          )}
        </div>
        {headerRight}
      </CardHeader>
      <CardContent className="space-y-5">
        {/* CPU */}
        <div>
          <div className="flex items-center justify-between text-sm mb-1">
            <span className="flex items-center gap-1.5 text-muted-foreground">
              <Cpu className="size-3.5" /> CPU
            </span>
            <span className="font-mono text-xs">
              {cpuPct < 0 ? "n/a" : `${cpuPct}%`}
              {system.processCpuLoad >= 0 && (
                <span className="text-muted-foreground ml-2">
                  (proc {formatLoad(system.processCpuLoad)})
                </span>
              )}
            </span>
          </div>
          <Bar pct={cpuPct < 0 ? 0 : cpuPct} />
          <div className="text-xs text-muted-foreground mt-1 truncate">
            {system.cpuModel || "Unknown CPU"} · {system.availableProcessors}{" "}
            core{system.availableProcessors === 1 ? "" : "s"}
          </div>
        </div>

        {/* Memory */}
        <div>
          <div className="flex items-center justify-between text-sm mb-1">
            <span className="flex items-center gap-1.5 text-muted-foreground">
              <MemoryStick className="size-3.5" /> Memory
            </span>
            <span className="font-mono text-xs">
              {formatMb(system.systemMemoryUsedMb)} /{" "}
              {formatMb(system.systemMemoryTotalMb)}
            </span>
          </div>
          <Bar pct={memPct} />
          <div className="text-xs text-muted-foreground mt-1">
            {memPct}% used
          </div>
        </div>

        {/* Specs grid */}
        <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-xs pt-1 border-t">
          <Spec label="Hostname" value={system.hostname || "—"} mono />
          <Spec
            label="OS"
            value={
              system.osName
                ? `${system.osName}${
                    system.osVersion ? ` ${system.osVersion}` : ""
                  }`
                : "—"
            }
          />
          <Spec label="Architecture" value={system.osArch || "—"} mono />
          <Spec
            label="Java"
            value={
              system.javaVersion
                ? `${system.javaVersion}${
                    system.javaVendor ? ` (${shortenVendor(system.javaVendor)})` : ""
                  }`
                : "—"
            }
          />
        </div>
      </CardContent>
    </Card>
  );
}

function Spec({
  label,
  value,
  mono,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div>
      <div className="text-muted-foreground">{label}</div>
      <div className={cn("truncate", mono && "font-mono")}>{value}</div>
    </div>
  );
}

function shortenVendor(vendor: string): string {
  // "Eclipse Adoptium" → "Adoptium", "Oracle Corporation" → "Oracle", …
  return vendor
    .replace(/Corporation|Corp\.|Inc\.|Ltd\.|, Inc/gi, "")
    .replace(/Eclipse /i, "")
    .trim();
}

export { Bar as SystemBar };
