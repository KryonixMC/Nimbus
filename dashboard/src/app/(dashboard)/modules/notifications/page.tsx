"use client";

import { useEffect, useState, useCallback } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { apiFetch } from "@/lib/api";
import { BellIcon, Send, RotateCw, Plus, Trash2, Pencil, X } from "@/lib/icons";
import { PageHeader } from "@/components/page-header";
import { StatCard } from "@/components/stat-card";
import { EmptyState } from "@/components/empty-state";
import { SectionLabel } from "@/components/section-label";

// ── Types ────────────────────────────────────────────────────────────────────

interface Webhook {
  id: string;
  type: string;
  events: string[];
  minSeverity: string;
  batchWindowMs: number;
  rateLimitPerMinute: number;
}

interface WebhooksResponse {
  enabled: boolean;
  webhooks: Webhook[];
  totalSent: number;
  totalFailed: number;
}

const ALL_EVENTS = [
  "ServiceCrashed",
  "ServiceReady",
  "ScaleUp",
  "ScaleDown",
  "MaintenanceEnabled",
  "MaintenanceDisabled",
  "ANOMALY_WARNING",
  "ANOMALY_CRITICAL",
  "BACKUP_CREATED",
  "BACKUP_FAILED",
];

const EMPTY_FORM = {
  id: "",
  type: "discord",
  url: "",
  events: ["ServiceCrashed", "ScaleUp", "ScaleDown"] as string[],
  minSeverity: "info",
  batchWindowMs: 5000,
  rateLimitPerMinute: 30,
};

// ── Page ─────────────────────────────────────────────────────────────────────

export default function NotificationsModulePage() {
  const [webhooks, setWebhooks] = useState<Webhook[]>([]);
  const [globalEnabled, setGlobalEnabled] = useState(true);
  const [totalSent, setTotalSent] = useState(0);
  const [totalFailed, setTotalFailed] = useState(0);
  const [loading, setLoading] = useState(true);
  const [reloading, setReloading] = useState(false);
  const [testingId, setTestingId] = useState<string | null>(null);
  const [testResults, setTestResults] = useState<
    Record<string, { success: boolean; message?: string }>
  >({});

  // Form state
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      const data = await apiFetch<WebhooksResponse>(
        "/api/notifications/webhooks"
      ).catch(() => ({ enabled: true, webhooks: [], totalSent: 0, totalFailed: 0 }));
      setWebhooks(data.webhooks ?? []);
      setGlobalEnabled(data.enabled);
      setTotalSent(data.totalSent);
      setTotalFailed(data.totalFailed);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    const interval = setInterval(load, 30_000);
    return () => clearInterval(interval);
  }, [load]);

  const handleReload = useCallback(async () => {
    setReloading(true);
    try {
      await apiFetch("/api/notifications/reload", { method: "POST" });
      await load();
    } finally {
      setReloading(false);
    }
  }, [load]);

  const handleTest = useCallback(async (id: string) => {
    setTestingId(id);
    try {
      const result = await apiFetch<{ success: boolean; message?: string }>(
        `/api/notifications/webhooks/${encodeURIComponent(id)}/test`,
        { method: "POST" }
      ).catch((err) => ({
        success: false,
        message: err instanceof Error ? err.message : String(err),
      }));
      setTestResults((prev) => ({ ...prev, [id]: result }));
      setTimeout(() => {
        setTestResults((prev) => {
          const next = { ...prev };
          delete next[id];
          return next;
        });
      }, 4_000);
    } finally {
      setTestingId(null);
    }
  }, []);

  const handleDelete = useCallback(
    async (id: string) => {
      if (!window.confirm(`Delete webhook "${id}"?`)) return;
      try {
        await apiFetch(`/api/notifications/webhooks/${encodeURIComponent(id)}`, {
          method: "DELETE",
        });
        await load();
      } catch (err) {
        window.alert(
          `Delete failed: ${err instanceof Error ? err.message : String(err)}`
        );
      }
    },
    [load]
  );

  const openCreate = () => {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setShowForm(true);
  };

  const openEdit = (wh: Webhook) => {
    setEditingId(wh.id);
    setForm({
      id: wh.id,
      type: wh.type,
      url: "",
      events: wh.events,
      minSeverity: wh.minSeverity,
      batchWindowMs: wh.batchWindowMs,
      rateLimitPerMinute: wh.rateLimitPerMinute,
    });
    setShowForm(true);
  };

  const handleSave = useCallback(async () => {
    if (!form.id.trim() || !form.url.trim()) {
      window.alert("ID and URL are required.");
      return;
    }
    setSaving(true);
    try {
      await apiFetch("/api/notifications/webhooks", {
        method: "POST",
        body: JSON.stringify({
          id: form.id.trim(),
          type: form.type,
          url: form.url.trim(),
          events: form.events,
          minSeverity: form.minSeverity,
          batchWindowMs: form.batchWindowMs,
          rateLimitPerMinute: form.rateLimitPerMinute,
        }),
      });
      setShowForm(false);
      setForm(EMPTY_FORM);
      await load();
    } catch (err) {
      window.alert(
        `Save failed: ${err instanceof Error ? err.message : String(err)}`
      );
    } finally {
      setSaving(false);
    }
  }, [form, load]);

  const toggleEvent = (event: string) => {
    setForm((prev) => ({
      ...prev,
      events: prev.events.includes(event)
        ? prev.events.filter((e) => e !== event)
        : [...prev.events, event],
    }));
  };

  const discordCount = webhooks.filter((w) => w.type === "discord").length;
  const slackCount = webhooks.filter((w) => w.type === "slack").length;

  const headerActions = (
    <div className="flex items-center gap-2">
      <Button variant="outline" size="sm" onClick={handleReload} disabled={reloading}>
        <RotateCw className={`size-4 mr-1.5 ${reloading ? "animate-spin" : ""}`} />
        Reload
      </Button>
      <Button size="sm" onClick={openCreate}>
        <Plus className="size-4 mr-1.5" />
        Add Webhook
      </Button>
    </div>
  );

  return (
    <>
      <PageHeader
        title="Notifications"
        description="Discord and Slack webhook notifications for Nimbus events."
        actions={headerActions}
      />

      {loading ? (
        <Skeleton className="h-96 rounded-xl" />
      ) : (
        <div className="space-y-6">
          {/* Stats */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard
              label="Webhooks"
              icon={BellIcon}
              tone="primary"
              value={webhooks.length}
              hint={globalEnabled ? "module enabled" : "module disabled"}
            />
            <StatCard
              label="Discord"
              icon={BellIcon}
              value={discordCount}
              hint={discordCount === 1 ? "webhook" : "webhooks"}
            />
            <StatCard
              label="Slack"
              icon={BellIcon}
              value={slackCount}
              hint={slackCount === 1 ? "webhook" : "webhooks"}
            />
            <StatCard
              label="Delivered"
              icon={Send}
              value={totalSent}
              hint={totalFailed > 0 ? `${totalFailed} failed` : "no failures"}
            />
          </div>

          {/* Create / Edit form */}
          {showForm && (
            <Card>
              <CardContent className="p-6 space-y-4">
                <div className="flex items-center justify-between">
                  <SectionLabel>{editingId ? `Edit: ${editingId}` : "New Webhook"}</SectionLabel>
                  <Button variant="ghost" size="icon" className="size-8" onClick={() => setShowForm(false)}>
                    <X className="size-4" />
                  </Button>
                </div>

                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium text-muted-foreground uppercase tracking-wide">ID</label>
                    <Input
                      value={form.id}
                      onChange={(e) => setForm((p) => ({ ...p, id: e.target.value }))}
                      placeholder="discord-main"
                      disabled={!!editingId}
                    />
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium text-muted-foreground uppercase tracking-wide">Type</label>
                    <select
                      className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                      value={form.type}
                      onChange={(e) => setForm((p) => ({ ...p, type: e.target.value }))}
                    >
                      <option value="discord">Discord</option>
                      <option value="slack">Slack</option>
                    </select>
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-xs font-medium text-muted-foreground uppercase tracking-wide">Severity</label>
                    <select
                      className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                      value={form.minSeverity}
                      onChange={(e) => setForm((p) => ({ ...p, minSeverity: e.target.value }))}
                    >
                      <option value="info">Info (all events)</option>
                      <option value="warn">Warn + Critical only</option>
                      <option value="critical">Critical only</option>
                    </select>
                  </div>
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-muted-foreground uppercase tracking-wide">
                    Webhook URL {editingId && "(leave blank to keep current)"}
                  </label>
                  <Input
                    value={form.url}
                    onChange={(e) => setForm((p) => ({ ...p, url: e.target.value }))}
                    placeholder={form.type === "discord"
                      ? "https://discord.com/api/webhooks/..."
                      : "https://hooks.slack.com/services/..."}
                    type="url"
                  />
                </div>

                <div className="space-y-1.5">
                  <label className="text-xs font-medium text-muted-foreground uppercase tracking-wide">Events</label>
                  <div className="flex flex-wrap gap-1.5">
                    {ALL_EVENTS.map((event) => {
                      const active = form.events.includes(event);
                      return (
                        <Badge
                          key={event}
                          variant={active ? "default" : "outline"}
                          className={`cursor-pointer select-none ${
                            active ? "" : "opacity-50"
                          }`}
                          onClick={() => toggleEvent(event)}
                        >
                          {event}
                        </Badge>
                      );
                    })}
                  </div>
                </div>

                <div className="flex justify-end gap-2 pt-2">
                  <Button variant="outline" onClick={() => setShowForm(false)}>
                    Cancel
                  </Button>
                  <Button onClick={handleSave} disabled={saving}>
                    {saving ? "Saving..." : editingId ? "Update Webhook" : "Create Webhook"}
                  </Button>
                </div>
              </CardContent>
            </Card>
          )}

          {/* Webhook table */}
          {webhooks.length === 0 && !showForm ? (
            <EmptyState
              icon={BellIcon}
              title="No webhooks configured"
              description="Add a Discord or Slack webhook to start receiving notifications."
              action={
                <Button size="sm" onClick={openCreate}>
                  <Plus className="size-4 mr-1.5" />
                  Add Webhook
                </Button>
              }
            />
          ) : webhooks.length > 0 && (
            <Card>
              <CardContent className="p-0">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="pl-6">ID</TableHead>
                      <TableHead>Type</TableHead>
                      <TableHead>Events</TableHead>
                      <TableHead>Severity</TableHead>
                      <TableHead className="pr-6 text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {webhooks.map((webhook) => {
                      const result = testResults[webhook.id];
                      const isTesting = testingId === webhook.id;
                      return (
                        <TableRow key={webhook.id}>
                          <TableCell className="pl-6 font-mono text-sm">
                            {webhook.id}
                          </TableCell>
                          <TableCell>
                            {webhook.type === "discord" ? (
                              <Badge className="bg-purple-500/15 text-purple-600 dark:text-purple-400 border-purple-500/30">
                                Discord
                              </Badge>
                            ) : (
                              <Badge className="bg-green-500/15 text-green-600 dark:text-green-400 border-green-500/30">
                                Slack
                              </Badge>
                            )}
                          </TableCell>
                          <TableCell className="text-sm text-muted-foreground max-w-[280px]">
                            <div className="flex flex-wrap gap-1">
                              {webhook.events.length > 0
                                ? webhook.events.map((e) => (
                                    <Badge key={e} variant="secondary" className="text-xs">
                                      {e}
                                    </Badge>
                                  ))
                                : <span className="italic">all events</span>}
                            </div>
                          </TableCell>
                          <TableCell>
                            <Badge variant="outline" className="text-xs capitalize">
                              {webhook.minSeverity}
                            </Badge>
                          </TableCell>
                          <TableCell className="pr-6 text-right">
                            <div className="flex items-center justify-end gap-1">
                              {result && (
                                <span
                                  className={`text-xs mr-1 ${
                                    result.success
                                      ? "text-green-600 dark:text-green-400"
                                      : "text-red-600 dark:text-red-400"
                                  }`}
                                >
                                  {result.success ? "Sent!" : "Failed"}
                                </span>
                              )}
                              <Button
                                variant="ghost"
                                size="icon"
                                className="size-8"
                                title="Send test notification"
                                disabled={isTesting}
                                onClick={() => handleTest(webhook.id)}
                              >
                                <Send className={`size-4 ${isTesting ? "animate-pulse" : ""}`} />
                              </Button>
                              <Button
                                variant="ghost"
                                size="icon"
                                className="size-8"
                                title="Edit webhook"
                                onClick={() => openEdit(webhook)}
                              >
                                <Pencil className="size-4" />
                              </Button>
                              <Button
                                variant="ghost"
                                size="icon"
                                className="size-8 text-destructive hover:text-destructive"
                                title="Delete webhook"
                                onClick={() => handleDelete(webhook.id)}
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
