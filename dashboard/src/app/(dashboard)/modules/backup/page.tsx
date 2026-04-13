"use client";

import { useEffect, useState, useCallback, useRef } from "react";
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
import { apiFetch, getApiUrl, getToken } from "@/lib/api";
import { PageHeader } from "@/components/page-header";
import { StatCard } from "@/components/stat-card";
import { EmptyState } from "@/components/empty-state";
import { ArchiveIcon, Download, Trash2, RotateCw, Plus } from "@/lib/icons";

// ── Types ────────────────────────────────────────────────────────────────────

interface BackupEntry {
  id: string;
  createdAt: string;
  targetId: string;
  targetType: string;
  archivePath: string;
  sizeBytes: number;
  status: "ok" | "failed" | "pruned";
  errorMessage?: string;
  durationMs: number;
}

interface BackupStatus {
  activeJobs: string[];
  totalBackupCount: number;
  totalSizeBytes: number;
  backupDir: string;
}

interface BackupsResponse {
  entries: BackupEntry[];
  count: number;
}

interface CreateBackupResponse {
  success: boolean;
  backupId?: string;
  sizeBytes?: number;
  durationMs?: number;
  error?: string;
}

// ── Formatters ───────────────────────────────────────────────────────────────

function formatBytes(bytes: number): string {
  if (bytes === 0) return "0 B";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(1)}s`;
}

function formatDate(iso: string): string {
  try {
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}

// ── Status badge ─────────────────────────────────────────────────────────────

function StatusBadge({ status, errorMessage }: { status: BackupEntry["status"]; errorMessage?: string }) {
  if (status === "ok") {
    return (
      <Badge variant="outline" className="border-emerald-500/40 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
        ok
      </Badge>
    );
  }
  if (status === "failed") {
    return (
      <Badge
        variant="outline"
        className="border-red-500/40 bg-red-500/10 text-red-600 dark:text-red-400"
        title={errorMessage}
      >
        failed
      </Badge>
    );
  }
  // pruned
  return (
    <Badge variant="outline" className="border-muted-foreground/30 bg-muted/50 text-muted-foreground">
      pruned
    </Badge>
  );
}

// ── Page ─────────────────────────────────────────────────────────────────────

export default function BackupModulePage() {
  const [backups, setBackups] = useState<BackupEntry[]>([]);
  const [backupStatus, setBackupStatus] = useState<BackupStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [actionTarget, setActionTarget] = useState<string | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const load = useCallback(async () => {
    try {
      const [r, s] = await Promise.all([
        apiFetch<BackupsResponse>("/api/backups").catch(() => ({ entries: [], count: 0 })),
        apiFetch<BackupStatus>("/api/backups/status").catch(() => null),
      ]);
      setBackups(r.entries ?? []);
      setBackupStatus(s);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    intervalRef.current = setInterval(load, 15_000);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [load]);

  // Create backup — uses first available target from status, or prompts for one
  const handleCreateBackup = useCallback(async () => {
    const targetId = window.prompt("Enter target ID to back up:");
    if (!targetId?.trim()) return;

    setCreating(true);
    try {
      const result = await apiFetch<CreateBackupResponse>("/api/backups", {
        method: "POST",
        body: JSON.stringify({ targetId: targetId.trim() }),
      });
      if (!result.success) {
        window.alert(`Backup failed: ${result.error ?? "unknown error"}`);
      }
      await load();
    } catch (err) {
      window.alert(`Backup failed: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setCreating(false);
    }
  }, [load]);

  // Restore a backup
  const handleRestore = useCallback(async (id: string, targetId: string) => {
    const confirmed = window.confirm(
      `Restore backup for "${targetId}"?\n\nThis will overwrite the current service data.`
    );
    if (!confirmed) return;

    setActionTarget(id);
    try {
      await apiFetch(`/api/backups/${encodeURIComponent(id)}/restore`, {
        method: "POST",
      });
      await load();
    } catch (err) {
      window.alert(`Restore failed: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setActionTarget(null);
    }
  }, [load]);

  // Delete a backup
  const handleDelete = useCallback(async (id: string, targetId: string) => {
    const confirmed = window.confirm(
      `Delete backup for "${targetId}"?\n\nThis action cannot be undone.`
    );
    if (!confirmed) return;

    setActionTarget(id);
    try {
      await apiFetch(`/api/backups/${encodeURIComponent(id)}`, {
        method: "DELETE",
      });
      setBackups((prev) => prev.filter((b) => b.id !== id));
    } catch (err) {
      window.alert(`Delete failed: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setActionTarget(null);
    }
  }, []);

  // Download — open authenticated URL in a new tab
  const handleDownload = useCallback((id: string) => {
    const apiUrl = getApiUrl();
    const token = getToken();
    // Build a temporary anchor with the bearer token embedded as a query param
    // (the controller accepts ?token= for file download endpoints)
    const url = `${apiUrl}/api/backups/${encodeURIComponent(id)}/download?token=${encodeURIComponent(token)}`;
    window.open(url, "_blank", "noopener,noreferrer");
  }, []);

  const createButton = (
    <Button onClick={handleCreateBackup} disabled={creating} size="sm">
      <Plus className="mr-1.5 size-4" />
      {creating ? "Creating…" : "Create Backup"}
    </Button>
  );

  return (
    <>
      <PageHeader
        title="Backups"
        description="Scheduled and on-demand backups of service data and templates."
        actions={createButton}
      />

      {loading ? (
        <Skeleton className="h-96 rounded-xl" />
      ) : (
        <div className="space-y-6">
          {/* Stats row */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard
              label="Active Jobs"
              icon={ArchiveIcon}
              tone={backupStatus && backupStatus.activeJobs.length > 0 ? "primary" : "default"}
              value={backupStatus?.activeJobs.length ?? 0}
              hint="currently running"
            />
            <StatCard
              label="Total Backups"
              icon={ArchiveIcon}
              value={backupStatus?.totalBackupCount ?? backups.length}
              hint="stored entries"
            />
            <StatCard
              label="Total Size"
              icon={ArchiveIcon}
              value={formatBytes(backupStatus?.totalSizeBytes ?? 0)}
              hint="across all backups"
            />
            <StatCard
              label="Backup Directory"
              icon={ArchiveIcon}
              value={
                <span
                  className="truncate text-base font-mono"
                  title={backupStatus?.backupDir ?? "—"}
                >
                  {backupStatus?.backupDir ?? "—"}
                </span>
              }
            />
          </div>

          {/* Backups table */}
          {backups.length === 0 ? (
            <EmptyState
              icon={ArchiveIcon}
              title="No backups yet"
              description="Create a backup using the button above, or wait for a scheduled backup to run."
              action={createButton}
            />
          ) : (
            <Card>
              <CardContent className="p-0">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="pl-6">Target</TableHead>
                      <TableHead>Type</TableHead>
                      <TableHead>Size</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Duration</TableHead>
                      <TableHead>Created</TableHead>
                      <TableHead className="pr-6 text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {backups.map((backup) => {
                      const busy = actionTarget === backup.id;
                      return (
                        <TableRow key={backup.id}>
                          <TableCell className="pl-6 font-medium">
                            {backup.targetId}
                          </TableCell>
                          <TableCell>
                            <Badge variant="secondary" className="text-xs capitalize">
                              {backup.targetType}
                            </Badge>
                          </TableCell>
                          <TableCell className="text-sm text-muted-foreground">
                            {formatBytes(backup.sizeBytes)}
                          </TableCell>
                          <TableCell>
                            <StatusBadge
                              status={backup.status}
                              errorMessage={backup.errorMessage}
                            />
                          </TableCell>
                          <TableCell className="text-sm text-muted-foreground">
                            {formatDuration(backup.durationMs)}
                          </TableCell>
                          <TableCell className="text-sm text-muted-foreground">
                            {formatDate(backup.createdAt)}
                          </TableCell>
                          <TableCell className="pr-6 text-right">
                            <div className="flex items-center justify-end gap-1">
                              <Button
                                variant="ghost"
                                size="icon"
                                className="size-8"
                                title="Download backup"
                                disabled={busy || backup.status !== "ok"}
                                onClick={() => handleDownload(backup.id)}
                              >
                                <Download className="size-4" />
                              </Button>
                              <Button
                                variant="ghost"
                                size="icon"
                                className="size-8"
                                title="Restore backup"
                                disabled={busy || backup.status !== "ok"}
                                onClick={() => handleRestore(backup.id, backup.targetId)}
                              >
                                <RotateCw className={`size-4 ${busy ? "animate-spin" : ""}`} />
                              </Button>
                              <Button
                                variant="ghost"
                                size="icon"
                                className="size-8 text-destructive hover:text-destructive"
                                title="Delete backup"
                                disabled={busy}
                                onClick={() => handleDelete(backup.id, backup.targetId)}
                              >
                                <Trash2 className="size-4" />
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )}
        </div>
      )}
    </>
  );
}
