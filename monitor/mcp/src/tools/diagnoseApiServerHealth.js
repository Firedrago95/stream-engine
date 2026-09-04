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
    { key: "signalRps", query: `sum(rate(http_server_requests_seconds_count{job="${jobLabel}",uri=~".*signals.*"}[5m]))`, required: false },
    { key: "http5xxRate", query: `sum(rate(http_server_requests_seconds_count{job="${jobLabel}",status=~"5.."}[5m]))`, required: false },
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
      if (def.key === "http5xxRate" || def.key === "signalRps") {
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
  const signalRps = Number((parsedMetrics.signalRps ?? 0).toFixed(2));
  const http5xxRate = Number((parsedMetrics.http5xxRate ?? 0).toFixed(2));

  const warnings = [];
  const criticals = [];

  if (failedMetrics.length > 0) {
    warnings.push(`일부 보조 지표 조회 실패: ${failedMetrics.map((f) => f.key).join(", ")}`);
  }

  if (diskUsagePercent !== null && diskUsagePercent >= 90) {
    criticals.push(`디스크 사용량이 위험 수준입니다 (${diskUsagePercent}% 사용 중, 여유 ${diskFreeGb}GB).`);
  } else if (diskUsagePercent !== null && diskUsagePercent >= 85) {
    warnings.push(`디스크 사용량이 경고 수준입니다 (${diskUsagePercent}% 사용 중).`);
  }

  if (hikariPending > 0) {
    criticals.push(`HikariCP 커넥션 풀 대기(Pending) 발생 (${hikariPending}건) - DB 락 또는 슬로우 쿼리 의심.`);
  }

  if (http5xxRate > 0.5) {
    criticals.push(`API 서버 5xx 에러율이 감지되었습니다 (${http5xxRate} RPS).`);
  }

  if (cpuPercent >= 90) {
    criticals.push(`API 서버 CPU 사용률 과부하 (${cpuPercent}%).`);
  } else if (cpuPercent >= 75) {
    warnings.push(`API 서버 CPU 사용률 경고 (${cpuPercent}%).`);
  }

  if (heapUsagePercent >= 85) {
    criticals.push(`API 서버 JVM 힙 메모리 고갈 위험 (${heapUsagePercent}%).`);
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
      signalRps,
      http5xxRate,
    },
    failedMetrics,
    criticals,
    warnings,
    summary:
      status === "HEALTHY"
        ? `[정상] OCI api-server CPU ${cpuPercent}%, 힙 ${heapUsagePercent}%, 디스크 사용률 ${diskUsagePercent ?? "N/A"}% (여유 ${diskFreeGb ?? "N/A"}GB), HikariCP 대기 0건으로 안정 서빙 중입니다.`
        : `[${status}] ${[...criticals, ...warnings].join(" / ")}`,
  };
}
