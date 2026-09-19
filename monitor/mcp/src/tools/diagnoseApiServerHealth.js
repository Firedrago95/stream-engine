import { queryPromql } from "../grafana-client.js";

export async function diagnoseApiServerHealth() {
  const jobLabel = process.env.API_SERVER_JOB || "api-server-oci";
  const queryDefinitions = [
    { key: "diskFree", query: `disk_free_bytes{job="${jobLabel}"}`, required: false },
    { key: "diskTotal", query: `disk_total_bytes{job="${jobLabel}"}`, required: false },
    { key: "hikariPending", query: `hikaricp_connections_pending{job="${jobLabel}"}`, required: true },
    { key: "hikariActive", query: `hikaricp_connections_active{job="${jobLabel}"}`, required: true },
    { key: "cpuUsage", query: `system_cpu_usage{job="${jobLabel}"}`, required: true },
    { key: "heapUsedBytes", query: `sum(jvm_memory_used_bytes{area="heap",job="${jobLabel}"})`, required: true },
    { key: "heapMaxBytes", query: `sum(jvm_memory_max_bytes{area="heap",job="${jobLabel}"})`, required: false },
    { key: "apiTotalRps", query: `sum(rate(http_server_requests_seconds_count{job="${jobLabel}"}[5m]))`, required: false },
    { key: "apiP95LatencySec", query: `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job="${jobLabel}"}[5m])))`, required: false },
    { key: "signalRps", query: `sum(rate(http_server_requests_seconds_count{job="${jobLabel}",uri=~".*signals.*"}[5m]))`, required: false },
    { key: "syncRps", query: `sum(rate(http_server_requests_seconds_count{job="${jobLabel}",uri=~".*streams/sync.*"}[5m]))`, required: false },
    { key: "http5xxRate", query: `sum(rate(http_server_requests_seconds_count{job="${jobLabel}",status=~"5.."}[5m]))`, required: false },
    { key: "securityBlockedWebhookCount", query: `sum(increase(http_server_requests_seconds_count{job="${jobLabel}",status="403",uri=~".*signals.*"}[1h]))`, required: false },
    { key: "schedulerFailures", query: `sum(increase(scheduler_execution_total{job="${jobLabel}",status="failure"}[1h]))`, required: false },
    { key: "schedulerZombieSec", query: `max(scheduler_execution_duration_seconds_max{job="${jobLabel}", scheduler="HighlightZombieSessionScheduler"})`, required: false },
    { key: "schedulerCleanupSec", query: `max(scheduler_execution_duration_seconds_max{job="${jobLabel}", scheduler="StreamSessionCleanupScheduler"})`, required: false },
    { key: "schedulerLeaderboardSec", query: `max(scheduler_execution_duration_seconds_max{job="${jobLabel}", scheduler="StreamerLeaderboardScheduler"})`, required: false },
    { key: "schedulerHighlightCleanupSec", query: `max(scheduler_execution_duration_seconds_max{job="${jobLabel}", scheduler="HighlightCleanupScheduler"})`, required: false },
    { key: "schedulerAnalysisCleanupSec", query: `max(scheduler_execution_duration_seconds_max{job="${jobLabel}", scheduler="AnalysisDataCleanupScheduler"})`, required: false },
  ];

  const results = await Promise.allSettled(
    queryDefinitions.map((def) => queryPromql(def.query))
  );

  const parsedMetrics = {};
  const failedMetrics = [];

  queryDefinitions.forEach((def, index) => {
    const res = results[index];
    if (res.status === "fulfilled" && Array.isArray(res.value) && res.value.length > 0 && res.value[0]?.value) {
      parsedMetrics[def.key] = Number(res.value[0].value[1]);
    } else if (res.status === "fulfilled" && Array.isArray(res.value) && res.value.length === 0) {
      if (!def.required) {
        parsedMetrics[def.key] = 0;
      } else {
        failedMetrics.push({ key: def.key, query: def.query, error: "데이터가 비어있습니다 (0건 반환)", required: def.required });
      }
    } else {
      const errorMsg = res.status === "rejected" ? res.reason?.message || "쿼리 거부됨" : "응답 형식 불일치";
      failedMetrics.push({ key: def.key, query: def.query, error: errorMsg, required: def.required });
    }
  });

  const hasRequiredFailure = failedMetrics.some((f) => f.required);
  if (hasRequiredFailure) {
    const failedNames = failedMetrics.map((f) => `${f.key}(${f.error})`).join(", ");
    return {
      status: "UNKNOWN",
      timestamp: new Date().toISOString(),
      metrics: parsedMetrics,
      failedMetrics,
      criticals: [],
      warnings: [],
      summary: `[측정 불가/UNKNOWN] OCI api-server 필수 지표 조회 실패로 상태를 확정할 수 없습니다. (실패 항목: ${failedNames})`,
    };
  }

  const diskFreeGb = parsedMetrics.diskFree ? Number((parsedMetrics.diskFree / (1024 * 1024 * 1024)).toFixed(1)) : null;
  const diskTotalGb = parsedMetrics.diskTotal ? Number((parsedMetrics.diskTotal / (1024 * 1024 * 1024)).toFixed(1)) : null;
  const diskUsagePercent = diskTotalGb && diskTotalGb > 0 && diskFreeGb !== null
    ? Number((((diskTotalGb - diskFreeGb) / diskTotalGb) * 100).toFixed(1))
    : null;

  const hikariPending = Number(parsedMetrics.hikariPending ?? 0);
  const hikariActive = Number(parsedMetrics.hikariActive ?? 0);
  const cpuPercent = Number(((parsedMetrics.cpuUsage ?? 0) * 100).toFixed(1));
  const heapUsedMb = parsedMetrics.heapUsedBytes ? Number((parsedMetrics.heapUsedBytes / (1024 * 1024)).toFixed(1)) : 0;
  const heapMaxMb = parsedMetrics.heapMaxBytes ? Number((parsedMetrics.heapMaxBytes / (1024 * 1024)).toFixed(1)) : 0;
  const heapUsagePercent = heapMaxMb > 0 ? Number(((heapUsedMb / heapMaxMb) * 100).toFixed(1)) : 0;
  const apiTotalRps = Number((parsedMetrics.apiTotalRps ?? 0).toFixed(2));
  const apiP95LatencyMs = parsedMetrics.apiP95LatencySec ? Number((parsedMetrics.apiP95LatencySec * 1000).toFixed(1)) : null;
  const signalRps = Number((parsedMetrics.signalRps ?? 0).toFixed(2));
  const syncRps = Number((parsedMetrics.syncRps ?? 0).toFixed(2));
  const http5xxRate = Number((parsedMetrics.http5xxRate ?? 0).toFixed(2));
  const securityBlockedWebhookCount = Number(parsedMetrics.securityBlockedWebhookCount ?? 0);
  const schedulerFailures = Number(parsedMetrics.schedulerFailures ?? 0);

  const schedulerZombieSec = parsedMetrics.schedulerZombieSec !== undefined && parsedMetrics.schedulerZombieSec !== 0
    ? Number(Number(parsedMetrics.schedulerZombieSec).toFixed(3))
    : null;
  const schedulerCleanupSec = parsedMetrics.schedulerCleanupSec !== undefined && parsedMetrics.schedulerCleanupSec !== 0
    ? Number(Number(parsedMetrics.schedulerCleanupSec).toFixed(3))
    : null;
  const schedulerLeaderboardSec = parsedMetrics.schedulerLeaderboardSec !== undefined && parsedMetrics.schedulerLeaderboardSec !== 0
    ? Number(Number(parsedMetrics.schedulerLeaderboardSec).toFixed(2))
    : null;
  const schedulerHighlightCleanupSec = parsedMetrics.schedulerHighlightCleanupSec !== undefined && parsedMetrics.schedulerHighlightCleanupSec !== 0
    ? Number(Number(parsedMetrics.schedulerHighlightCleanupSec).toFixed(2))
    : null;
  const schedulerAnalysisCleanupSec = parsedMetrics.schedulerAnalysisCleanupSec !== undefined && parsedMetrics.schedulerAnalysisCleanupSec !== 0
    ? Number(Number(parsedMetrics.schedulerAnalysisCleanupSec).toFixed(2))
    : null;

  const warnings = [];
  const criticals = [];

  if (failedMetrics.length > 0) {
    warnings.push(`일부 보조 지표 조회 실패: ${failedMetrics.map((f) => f.key).join(", ")}`);
  }

  // 1. 디스크 사용량
  if (diskUsagePercent !== null && diskUsagePercent >= 90) {
    criticals.push(`디스크 사용량이 위험 수준입니다 (${diskUsagePercent}% 사용 중, 여유 ${diskFreeGb}GB).`);
  } else if (diskUsagePercent !== null && diskUsagePercent >= 85) {
    warnings.push(`디스크 사용량이 경고 수준입니다 (${diskUsagePercent}% 사용 중).`);
  }

  // 2. DB 커넥션 풀 (HikariCP)
  if (hikariPending > 0) {
    criticals.push(`HikariCP 커넥션 풀 대기(Pending) 발생 (${hikariPending}건) - DB 락 또는 슬로우 쿼리 의심.`);
  }

  // 3. HTTP 5xx 에러
  if (http5xxRate > 0.5) {
    criticals.push(`API 서버 5xx 에러율이 감지되었습니다 (${http5xxRate} RPS).`);
  }

  // 4. 스케줄러 실패
  if (schedulerFailures > 0) {
    criticals.push(`최근 1시간 동안 API 서버 스케줄러 작업 실패가 감지되었습니다 (${schedulerFailures}건).`);
  }

  // 5. CPU & Heap
  if (cpuPercent >= 90) {
    criticals.push(`API 서버 CPU 사용률 과부하 (${cpuPercent}%).`);
  } else if (cpuPercent >= 75) {
    warnings.push(`API 서버 CPU 사용률 경고 (${cpuPercent}%).`);
  }

  if (heapUsagePercent >= 85) {
    criticals.push(`API 서버 JVM 힙 메모리 고갈 위험 (${heapUsagePercent}%).`);
  }

  // 6. API P95 응답 지연
  if (apiP95LatencyMs !== null) {
    if (apiP95LatencyMs >= 3000) {
      criticals.push(`API 엔드포인트 P95 응답 지연이 심각합니다 (${apiP95LatencyMs}ms).`);
    } else if (apiP95LatencyMs >= 1000) {
      warnings.push(`API 엔드포인트 P95 응답 지연이 경고 수준입니다 (${apiP95LatencyMs}ms).`);
    }
  }

  // 7. 보안 비인가 차단 감지
  if (securityBlockedWebhookCount > 10) {
    warnings.push(`비인가 웹훅 접근 시도가 증가하고 있습니다 (최근 1시간 ${securityBlockedWebhookCount}건 차단).`);
  }

  // 8. 스케줄러 소요 시간 지연 및 병목 (SLA)
  if (schedulerZombieSec !== null) {
    if (schedulerZombieSec >= 8) {
      criticals.push(`[스케줄러 병목] HighlightZombieSessionScheduler 소요 시간 위험 (${schedulerZombieSec}s / 상한 10s).`);
    } else if (schedulerZombieSec >= 4) {
      warnings.push(`[스케줄러 지연] HighlightZombieSessionScheduler 소요 시간 경고 (${schedulerZombieSec}s / 상한 10s).`);
    }
  }

  if (schedulerCleanupSec !== null) {
    if (schedulerCleanupSec >= 8) {
      criticals.push(`[스케줄러 병목] StreamSessionCleanupScheduler 소요 시간 위험 (${schedulerCleanupSec}s / 상한 10s).`);
    } else if (schedulerCleanupSec >= 4) {
      warnings.push(`[스케줄러 지연] StreamSessionCleanupScheduler 소요 시간 경고 (${schedulerCleanupSec}s / 상한 10s).`);
    }
  }

  if (schedulerLeaderboardSec !== null) {
    if (schedulerLeaderboardSec >= 20) {
      criticals.push(`[스케줄러 병목] StreamerLeaderboardScheduler 소요 시간 위험 (${schedulerLeaderboardSec}s / 상한 30s).`);
    } else if (schedulerLeaderboardSec >= 10) {
      warnings.push(`[스케줄러 지연] StreamerLeaderboardScheduler 소요 시간 경고 (${schedulerLeaderboardSec}s / 상한 30s).`);
    }
  }

  if (schedulerHighlightCleanupSec !== null) {
    if (schedulerHighlightCleanupSec >= 45) {
      criticals.push(`[스케줄러 병목] HighlightCleanupScheduler 벌크정리 소요 시간 위험 (${schedulerHighlightCleanupSec}s / 상한 60s).`);
    } else if (schedulerHighlightCleanupSec >= 20) {
      warnings.push(`[스케줄러 지연] HighlightCleanupScheduler 벌크정리 소요 시간 경고 (${schedulerHighlightCleanupSec}s / 상한 60s).`);
    }
  }

  if (schedulerAnalysisCleanupSec !== null) {
    if (schedulerAnalysisCleanupSec >= 45) {
      criticals.push(`[스케줄러 병목] AnalysisDataCleanupScheduler 벌크정리 소요 시간 위험 (${schedulerAnalysisCleanupSec}s / 상한 60s).`);
    } else if (schedulerAnalysisCleanupSec >= 20) {
      warnings.push(`[스케줄러 지연] AnalysisDataCleanupScheduler 벌크정리 소요 시간 경고 (${schedulerAnalysisCleanupSec}s / 상한 60s).`);
    }
  }

  let status = "HEALTHY";
  if (criticals.length > 0) {
    status = "CRITICAL";
  } else if (warnings.length > 0) {
    status = failedMetrics.length > 0 ? "DEGRADED" : "WARNING";
  }

  return {
    status,
    timestamp: new Date().toISOString(),
    metrics: {
      diskUsagePercent: diskUsagePercent !== null ? `${diskUsagePercent}%` : "N/A",
      diskFreeGb: diskFreeGb !== null ? `${diskFreeGb}GB` : "N/A",
      hikariPending,
      hikariActive,
      cpuPercent: `${cpuPercent}%`,
      heapUsedMb,
      heapUsagePercent: `${heapUsagePercent}%`,
      apiTotalRps,
      apiP95LatencyMs: apiP95LatencyMs !== null ? `${apiP95LatencyMs}ms` : "N/A",
      signalRps,
      syncRps,
      http5xxRate,
      securityBlockedWebhookCount,
      schedulerFailures,
      schedulers: {
        zombieSec: schedulerZombieSec,
        cleanupSec: schedulerCleanupSec,
        leaderboardSec: schedulerLeaderboardSec,
        highlightCleanupSec: schedulerHighlightCleanupSec,
        analysisCleanupSec: schedulerAnalysisCleanupSec,
      },
    },
    failedMetrics,
    criticals,
    warnings,
    summary:
      status === "HEALTHY"
        ? `[정상] OCI api-server CPU ${cpuPercent}%, 힙 ${heapUsagePercent}%, 디스크 ${diskUsagePercent ?? "N/A"}% (여유 ${diskFreeGb ?? "N/A"}GB), HikariCP 대기 0건, API RPS ${apiTotalRps}, 스케줄러 지연/실패 없음으로 안정 서빙 중입니다.`
        : `[${status}] ${[...criticals, ...warnings].join(" / ")}`,
  };
}
