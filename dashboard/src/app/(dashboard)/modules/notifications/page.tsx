"use client";

import { useEffect, useState, useCallback } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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
import { BellIcon, Send, RotateCw, Activity } from "@/lib/icons";
import { PageHeader } from "@/components/page-header";
import { StatCard } from "@/components/stat-card";
import { EmptyState } from "@/components/empty-state";

interface Webhook {
  id: string;
  type: string;
  events: string[];
  minSeverity: string;
  enabled: boolean;
  pendingBatch: number;
  rateLimitTokens: number;
}

interface WebhooksResponse {
  webhooks: Webhook[];
}

interface ReloadResponse {
  success: true;
  webhookCount: number;
}

interface TestResponse {
  success: boolean;
  message?: string;
}

export default function NotificationsModulePage() {
  const [webhooks, setWebhooks] = useState<Webhook[]>([]);
  const [loading, setLoading] = useState(true);
  const [reloading, setReloading] = useState(false);
  const [testingId, setTestingId] = useState<string | null>(null);
  const [testResults, setTestResults] = useState<Record<string, { success: boolean; message?: string }>>({});

  const load = useCallback(async () => {
    try {
      const data = await apiFetch<WebhooksResponse>("/api/notifications/webhooks").catch(
        () => ({ webhooks: [] })
      );
      setWebhooks(data.webhooks ?? []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    const interval = setInterval(() => load(), 30_000);
    return () => clearInterval(interval);
  }, [load]);

  const handleReload = useCallback(async () => {
    setReloading(true);
    try {
      await apiFetch<ReloadResponse>("/api/notifications/reload", { method: "POST" });
      await load();
    } finally {
      setReloading(false);
    }
  }, [load]);

  const handleTest = useCallback(async (id: string) => {
    setTestingId(id);
    try {
      const result = await apiFetch<TestResponse>(
        `/api/notifications/webhooks/${id}/test`,
        { method: "POST" }
      ).catch((err) => ({ success: false, message: String(err) }));
      setTestResults((prev) => ({ ...prev, [id]: result }));
      // Clear the result indicator after 4 seconds
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

  const discordCount = webhooks.filter((w) => w.type === "discord").length;
  const slackCount = webhooks.filter((w) => w.type === "slack").length;

  const reloadButton = (
    <Button
      variant="outline"
      size="sm"
      onClick={handleReload}
      disabled={reloading}
    >
      <RotateCw className={`size-4 mr-2 ${reloading ? "animate-spin" : ""}`} />
      Reload config
    </Button>
  );

  return (
    <>
      <PageHeader
        title="Notifications"
        description="Discord and Slack webhook notifications for Nimbus events."
        actions={reloadButton}
      />

      {loading ? (
        <Skeleton className="h-96 rounded-xl" />
      ) : (
        <div className="space-y-6">
          <div className="grid gap-4 md:grid-cols-3">
            <StatCard
              label="Total webhooks"
              icon={BellIcon}
              tone="primary"
              value={webhooks.length}
              hint="configured"
            />
            <StatCard
              label="Discord"
              icon={Activity}
              value={discordCount}
              hint={discordCount === 1 ? "webhook" : "webhooks"}
            />
            <StatCard
              label="Slack"
              icon={Activity}
              value={slackCount}
              hint={slackCount === 1 ? "webhook" : "webhooks"}
            />
          </div>

          {webhooks.length === 0 ? (
            <EmptyState
              icon={BellIcon}
              title="No webhooks configured"
              description="Add a Discord or Slack webhook to the notifications module config to get started."
            />
          ) : (
            <Card>
              <CardContent className="p-0">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="pl-6">ID</TableHead>
                      <TableHead>Type</TableHead>
                      <TableHead>Events</TableHead>
                      <TableHead>Severity</TableHead>
                      <TableHead>Status</TableHead>
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
                              <Badge className="bg-purple-500/15 text-purple-600 dark:text-purple-400 border-purple-500/30 hover:bg-purple-500/20">
                                Discord
                              </Badge>
                            ) : webhook.type === "slack" ? (
                              <Badge className="bg-green-500/15 text-green-600 dark:text-green-400 border-green-500/30 hover:bg-green-500/20">
                                Slack
                              </Badge>
                            ) : (
                              <Badge variant="secondary">{webhook.type}</Badge>
                            )}
                          </TableCell>
                          <TableCell className="text-sm text-muted-foreground max-w-[280px] truncate">
                            {webhook.events.length > 0
                              ? webhook.events.join(", ")
                              : <span className="italic">all events</span>}
                          </TableCell>
                          <TableCell>
                            <Badge variant="outline" className="text-xs capitalize">
                              {webhook.minSeverity ?? "all"}
                            </Badge>
                          </TableCell>
                          <TableCell>
                            {webhook.enabled ? (
                              <Badge variant="outline" className="border-green-500/40 text-green-600 dark:text-green-400">
                                Enabled
                              </Badge>
                            ) : (
                              <Badge variant="outline" className="border-muted text-muted-foreground">
                                Disabled
                              </Badge>
                            )}
                          </TableCell>
                          <TableCell className="pr-6 text-right">
                            <div className="flex items-center justify-end gap-2">
                              {result && (
                                <span
                                  className={`text-xs ${
                                    result.success
                                      ? "text-green-600 dark:text-green-400"
                                      : "text-red-600 dark:text-red-400"
                                  }`}
                                >
                                  {result.success
                                    ? "Sent"
                                    : result.message ?? "Failed"}
                                </span>
                              )}
                              <Button
                                variant="outline"
                                size="sm"
                                disabled={isTesting || !webhook.enabled}
                                onClick={() => handleTest(webhook.id)}
                              >
                                <Send className={`size-4 mr-1.5 ${isTesting ? "animate-pulse" : ""}`} />
                                Test
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
