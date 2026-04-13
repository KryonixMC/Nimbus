"use client";

import { useEffect, useState, useCallback } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import { apiFetch } from "@/lib/api";
import { PageHeader } from "@/components/page-header";
import { StatCard } from "@/components/stat-card";
import { EmptyState } from "@/components/empty-state";
import { SectionLabel } from "@/components/section-label";
import { Activity, Server, Clock, RefreshCw } from "@/lib/icons";

// ── Types ────────────────────────────────────────────────────────────────────

interface Anomaly {
  id: string;
  detectedAt: string;
  serviceName: string;
  groupName?: string;
  metric: string;
  anomalyType: string;
  value: number;
  baseline: number;
  zscore: number;
  severity: "warning" | "critical";
  resolved: boolean;
  resolvedAt?: string;
}

interface AnomalyStats {
  activeCount: number;
  resolvedToday: number;
  totalCount: number;
  mostAffectedGroup?: string;
  topServices: { service: string; count: number }[];
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function formatMetric(raw: string): string {
  switch (raw) {
    case "memory_used_mb":
      return "Memory (MB)";
    case "player_count":
      return "Players";
    default:
      // snake_case → Title Case
      return raw
        .split("_")
        .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
        .join(" ");
  }
}

function formatRelative(iso: string): string {
  try {
    const diff = Date.now() - new Date(iso).getTime();
    const secs = Math.floor(diff / 1000);
    if (secs < 60) return `${secs}s ago`;
    const mins = Math.floor(secs / 60);
    if (mins < 60) return `${mins}m ago`;
    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    return `${days}d ago`;
  } catch {
    return iso;
  }
}

function SeverityBadge({ severity }: { severity: "warning" | "critical" }) {
  if (severity === "critical") {
    return (
      <Badge variant="destructive" className="text-xs">
        Critical
      </Badge>
    );
  }
  return (
    <Badge
      variant="outline"
      className="text-xs border-amber-500/50 bg-amber-500/10 text-amber-600 dark:text-amber-400"
    >
      Warning
    </Badge>
  );
}

function AnomalyTypeBadge({ type }: { type: string }) {
  if (type === "peer_outlier") {
    return (
      <Badge variant="secondary" className="text-xs">
        Peer
      </Badge>
    );
  }
  return (
    <Badge variant="outline" className="text-xs">
      Self
    </Badge>
  );
}

// ── Anomaly Table ─────────────────────────────────────────────────────────────

interface AnomalyTableProps {
  rows: Anomaly[];
  showResolved?: boolean;
}

function AnomalyTable({ rows, showResolved = false }: AnomalyTableProps) {
  if (rows.length === 0) {
    return (
      <EmptyState
        icon={Activity}
        title="All services healthy"
        description="No anomalies have been detected. Statistical baselines are within normal range."
      />
    );
  }

  return (
    <Card>
      <CardContent className="p-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="pl-6">Service</TableHead>
              <TableHead>Group</TableHead>
              <TableHead>Metric</TableHead>
              <TableHead>Type</TableHead>
              <TableHead className="text-right">Value</TableHead>
              <TableHead className="text-right">Baseline</TableHead>
              <TableHead className="text-right">Z-Score</TableHead>
              <TableHead>Severity</TableHead>
              {showResolved && <TableHead>Status</TableHead>}
              <TableHead className="pr-6 text-right">Detected</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((a) => (
              <TableRow key={a.id}>
                <TableCell className="pl-6 font-medium">{a.serviceName}</TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {a.groupName ?? <span className="text-muted-foreground/50">—</span>}
                </TableCell>
                <TableCell className="text-sm">{formatMetric(a.metric)}</TableCell>
                <TableCell>
                  <AnomalyTypeBadge type={a.anomalyType} />
                </TableCell>
                <TableCell className="text-right text-sm tabular-nums">
                  {a.value.toLocaleString()}
                </TableCell>
                <TableCell className="text-right text-sm tabular-nums text-muted-foreground">
                  {a.baseline.toLocaleString()}
                </TableCell>
                <TableCell className="text-right text-sm tabular-nums font-mono">
                  {a.zscore.toFixed(2)}
                </TableCell>
                <TableCell>
                  <SeverityBadge severity={a.severity} />
                </TableCell>
                {showResolved && (
                  <TableCell>
                    {a.resolved ? (
                      <Badge
                        variant="outline"
                        className="text-xs border-green-500/50 bg-green-500/10 text-green-600 dark:text-green-400"
                      >
                        Resolved
                      </Badge>
                    ) : (
                      <Badge variant="secondary" className="text-xs">
                        Active
                      </Badge>
                    )}
                  </TableCell>
                )}
                <TableCell className="pr-6 text-right text-xs text-muted-foreground">
                  {formatRelative(a.detectedAt)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function AnomalyPage() {
  const [current, setCurrent] = useState<Anomaly[]>([]);
  const [history, setHistory] = useState<Anomaly[]>([]);
  const [stats, setStats] = useState<AnomalyStats | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    try {
      const [c, h, s] = await Promise.all([
        apiFetch<{ anomalies: Anomaly[] }>("/api/anomaly/current").catch(
          () => ({ anomalies: [] })
        ),
        apiFetch<{ anomalies: Anomaly[] }>("/api/anomaly/history?limit=50").catch(
          () => ({ anomalies: [] })
        ),
        apiFetch<AnomalyStats>("/api/anomaly/stats").catch(() => null),
      ]);
      setCurrent(c.anomalies);
      setHistory(h.anomalies);
      setStats(s);
    } finally {
      setLoading(false);
    }
  }, []);

  // Initial load
  useEffect(() => {
    load();
  }, [load]);

  // Auto-refresh every 15 seconds
  useEffect(() => {
    const id = setInterval(load, 15_000);
    return () => clearInterval(id);
  }, [load]);

  const refreshButton = (
    <Button variant="outline" size="sm" onClick={load}>
      <RefreshCw className="mr-2 size-4" />
      Refresh
    </Button>
  );

  const topService = stats?.topServices?.[0];

  return (
    <>
      <PageHeader
        title="Anomaly Detection"
        description="Statistical anomaly detection for service RAM and player metrics."
        actions={refreshButton}
      />

      {loading ? (
        <Skeleton className="h-96 rounded-xl" />
      ) : (
        <div className="space-y-6">
          {/* Stats row */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard
              label="Active Anomalies"
              icon={Activity}
              tone={stats && stats.activeCount > 0 ? "destructive" : "default"}
              value={stats?.activeCount ?? 0}
              hint="currently unresolved"
            />
            <StatCard
              label="Resolved Today"
              icon={Clock}
              tone="primary"
              value={stats?.resolvedToday ?? 0}
              hint="since midnight"
            />
            <StatCard
              label="Most Affected Group"
              icon={Server}
              value={stats?.mostAffectedGroup ?? "—"}
              hint="by anomaly count"
            />
            <StatCard
              label="Top Service"
              icon={Server}
              value={topService?.service ?? "—"}
              hint={
                topService
                  ? `${topService.count} anomal${topService.count === 1 ? "y" : "ies"} total`
                  : "no data yet"
              }
            />
          </div>

          {/* Active anomalies section */}
          <div className="space-y-3">
            <SectionLabel
              right={
                <span className="text-xs text-muted-foreground">
                  {current.length} active
                </span>
              }
            >
              Active Anomalies
            </SectionLabel>
            <AnomalyTable rows={current} showResolved={false} />
          </div>

          {/* History section */}
          <div className="space-y-3">
            <SectionLabel
              right={
                <span className="text-xs text-muted-foreground">
                  last 50 entries
                </span>
              }
            >
              Recent History
            </SectionLabel>
            <AnomalyTable rows={history} showResolved={true} />
          </div>
        </div>
      )}
    </>
  );
}
