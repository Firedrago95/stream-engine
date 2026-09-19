import { queryPromql } from "../grafana-client.js";

export async function diagnosePipelineHealth() {
  const queryDefinitions = [
    { key: "activeStreams", query: "engine_active_streams", required: true },
    { key: "producerTps", query: "sum(kafka_producer_record_send_rate)", required: true },
    { key: "consumerTps", query: "sum(kafka_consumer_fetch_manager_records_consumed_rate)", required: true },
    { key: "kafkaLag", query: "sum(kafka_consumer_fetch_manager_records_lag)", required: true },
    { key: "p95LatencySec", query: "histogram_quantile(0.95, sum(rate(analysis_processing_time_seconds_bucket[5m])) by (le))", required: true },
    { key: "cpuUsage", query: 'system_cpu_usage{job="engine-home"}', required: false },
    { key: "heapPercent", query: '(sum(jvm_memory_used_bytes{job="engine-home", area="heap"}) / sum(jvm_memory_max_bytes{job="engine-home", area="heap"})) * 100', required: false },
    { key: "activeWebsockets", query: 'sum(collector_websocket_connections_active{job=~"engine-home|collector-home"})', required: false },
    { key: "redisLatencyMs", query: '(sum(rate(lettuce_command_completion_seconds_sum{job="engine-home"}[5m])) / sum(rate(lettuce_command_completion_seconds_count{job="engine-home"}[5m]))) * 1000', required: false },
    { key: "redisTps", query: 'sum(rate(lettuce_command_completion_seconds_count{job="engine-home"}[5m]))', required: false },
    { key: "liveThreads", query: 'jvm_threads_live_threads{application="engine"}', required: false },
    { key: "engineSchedulerFailures", query: 'sum(increase(scheduler_execution_total{application="engine",status="failure"}[1h]))', required: false },
    { key: "schedulerIngestionSec", query: 'max(scheduler_execution_duration_seconds_max{job="engine-home", scheduler="IngestionService"})', required: false },
    { key: "schedulerHighlightSec", query: 'max(scheduler_execution_duration_seconds_max{job="engine-home", scheduler="HighlightService"})', required: false },
    { key: "schedulerChatAggregationSec", query: 'max(scheduler_execution_duration_seconds_max{job="engine-home", scheduler="ChatAggregationService"})', required: false },
    { key: "schedulerStreamTierSec", query: 'max(scheduler_execution_duration_seconds_max{job="engine-home", scheduler="StreamTierManager"})', required: false },
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
      summary: `[측정 불가/UNKNOWN] 필수 모니터링 지표 조회 실패로 시스템 상태를 확정할 수 없습니다. (실패 항목: ${failedNames})`,
    };
  }

  const activeStreams = Number(parsedMetrics.activeStreams ?? 0);
  const producerTps = Number(parsedMetrics.producerTps ?? 0);
  const consumerTps = Number(parsedMetrics.consumerTps ?? 0);
  const kafkaLag = Number(parsedMetrics.kafkaLag ?? 0);
  const p95LatencySec = Number(parsedMetrics.p95LatencySec ?? 0);
  const p95LatencyMs = Number((p95LatencySec * 1000).toFixed(2));
  const cpuPercent = parsedMetrics.cpuUsage !== undefined ? Number(((parsedMetrics.cpuUsage ?? 0) * 100).toFixed(1)) : null;
  const heapPercent = parsedMetrics.heapPercent !== undefined ? Number(Number(parsedMetrics.heapPercent).toFixed(1)) : null;
  const activeWebsockets = parsedMetrics.activeWebsockets !== undefined ? Number(parsedMetrics.activeWebsockets) : null;
  const redisLatencyMs = parsedMetrics.redisLatencyMs !== undefined ? Number(Number(parsedMetrics.redisLatencyMs).toFixed(2)) : null;
  const redisTps = parsedMetrics.redisTps !== undefined ? Number(Number(parsedMetrics.redisTps).toFixed(1)) : null;
  const liveThreads = parsedMetrics.liveThreads !== undefined ? Number(parsedMetrics.liveThreads) : null;
  const engineSchedulerFailures = Number(parsedMetrics.engineSchedulerFailures ?? 0);

  const schedulerIngestionSec = parsedMetrics.schedulerIngestionSec !== undefined && parsedMetrics.schedulerIngestionSec !== 0
    ? Number(Number(parsedMetrics.schedulerIngestionSec).toFixed(2))
    : null;
  const schedulerHighlightSec = parsedMetrics.schedulerHighlightSec !== undefined && parsedMetrics.schedulerHighlightSec !== 0
    ? Number(Number(parsedMetrics.schedulerHighlightSec).toFixed(2))
    : null;
  const schedulerChatAggregationSec = parsedMetrics.schedulerChatAggregationSec !== undefined && parsedMetrics.schedulerChatAggregationSec !== 0
    ? Number(Number(parsedMetrics.schedulerChatAggregationSec).toFixed(3))
    : null;
  const schedulerStreamTierSec = parsedMetrics.schedulerStreamTierSec !== undefined && parsedMetrics.schedulerStreamTierSec !== 0
    ? Number(Number(parsedMetrics.schedulerStreamTierSec).toFixed(2))
    : null;

  const warnings = [];
  const criticals = [];

  if (failedMetrics.length > 0) {
    warnings.push(`일부 보조 지표 조회 실패: ${failedMetrics.map((f) => f.key).join(", ")}`);
  }

  // 1. 스트림 & 웹소켓
  if (activeStreams === 0) {
    criticals.push("수집 중인 활성 스트림 수가 0개입니다 (수집 엔진 중단 또는 웹소켓 미연결).");
  }
  if (activeWebsockets !== null && activeWebsockets === 0) {
    criticals.push("치지직 활성 웹소켓 연결 수가 0개입니다 (수집기 네트워크 단절 의심).");
  }

  // 2. 카프카 파이프라인
  if (producerTps > 0 && consumerTps === 0) {
    criticals.push("프로듀서는 발행 중이나 컨슈머 소비가 전면 중단되었습니다.");
  }
  if (kafkaLag >= 100) {
    criticals.push(`카프카 컨슈머 랙이 심각하게 누적되었습니다 (${kafkaLag}건).`);
  } else if (kafkaLag >= 10) {
    warnings.push(`카프카 컨슈머 랙이 발생하고 있습니다 (${kafkaLag}건).`);
  }

  // 3. 분석 레이턴시
  if (p95LatencyMs >= 100) {
    criticals.push(`분석 P95 지연 시간이 위험 수준입니다 (${p95LatencyMs}ms).`);
  } else if (p95LatencyMs >= 50) {
    warnings.push(`분석 P95 지연 시간이 경고 수준입니다 (${p95LatencyMs}ms).`);
  }

  // 4. 시스템 리소스 (CPU, Heap)
  if (cpuPercent !== null && cpuPercent >= 85) {
    criticals.push(`엔진 CPU 사용률 과부하 (${cpuPercent}%).`);
  } else if (cpuPercent !== null && cpuPercent >= 70) {
    warnings.push(`엔진 CPU 사용률 주의 (${cpuPercent}%).`);
  }
  if (heapPercent !== null && heapPercent >= 90) {
    criticals.push(`엔진 JVM 힙 메모리 임계 초과 (${heapPercent}%).`);
  } else if (heapPercent !== null && heapPercent >= 75) {
    warnings.push(`엔진 JVM 힙 메모리 주의 (${heapPercent}%).`);
  }

  // 5. 레디스
  if (redisLatencyMs !== null && redisLatencyMs >= 25) {
    criticals.push(`Redis 평균 명령 지연이 위험 수준입니다 (${redisLatencyMs}ms).`);
  } else if (redisLatencyMs !== null && redisLatencyMs >= 10) {
    warnings.push(`Redis 평균 명령 지연이 경고 수준입니다 (${redisLatencyMs}ms).`);
  }

  // 6. 스케줄러 실패
  if (engineSchedulerFailures > 0) {
    criticals.push(`최근 1시간 동안 엔진 스케줄러 작업 실패가 감지되었습니다 (${engineSchedulerFailures}건).`);
  }

  // 7. 스케줄러 소요 시간 지연 및 병목 (SLA)
  if (schedulerIngestionSec !== null) {
    if (schedulerIngestionSec >= 25) {
      criticals.push(`[스케줄러 병목] IngestionService 소요 시간이 위험(25s) 수준입니다 (${schedulerIngestionSec}s / 주기 30s).`);
    } else if (schedulerIngestionSec >= 18) {
      warnings.push(`[스케줄러 지연] IngestionService 소요 시간이 경고(18s) 수준입니다 (${schedulerIngestionSec}s / 주기 30s).`);
    }
  }

  if (schedulerHighlightSec !== null) {
    if (schedulerHighlightSec >= 2.4) {
      criticals.push(`[스케줄러 병목] HighlightService 소요 시간이 위험(2.4s) 수준입니다 (${schedulerHighlightSec}s / 주기 3s).`);
    } else if (schedulerHighlightSec >= 1.5) {
      warnings.push(`[스케줄러 지연] HighlightService 소요 시간이 경고(1.5s) 수준입니다 (${schedulerHighlightSec}s / 주기 3s).`);
    }
  }

  if (schedulerChatAggregationSec !== null) {
    if (schedulerChatAggregationSec >= 2.0) {
      criticals.push(`[스케줄러 병목] ChatAggregationService 소요 시간이 위험 수준입니다 (${schedulerChatAggregationSec}s / 주기 3s).`);
    } else if (schedulerChatAggregationSec >= 1.0) {
      warnings.push(`[스케줄러 지연] ChatAggregationService 소요 시간이 경고 수준입니다 (${schedulerChatAggregationSec}s / 주기 3s).`);
    }
  }

  if (schedulerStreamTierSec !== null) {
    if (schedulerStreamTierSec >= 40) {
      criticals.push(`[스케줄러 병목] StreamTierManager 소요 시간이 위험 수준입니다 (${schedulerStreamTierSec}s / 주기 60s).`);
    } else if (schedulerStreamTierSec >= 20) {
      warnings.push(`[스케줄러 지연] StreamTierManager 소요 시간이 경고 수준입니다 (${schedulerStreamTierSec}s / 주기 60s).`);
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
      activeStreams,
      activeWebsockets,
      producerTps: Number(producerTps.toFixed(1)),
      consumerTps: Number(consumerTps.toFixed(1)),
      kafkaLag,
      p95LatencyMs,
      cpuPercent,
      heapPercent,
      redisLatencyMs,
      redisTps,
      liveThreads,
      engineSchedulerFailures,
      schedulers: {
        ingestionSec: schedulerIngestionSec,
        highlightSec: schedulerHighlightSec,
        chatAggregationSec: schedulerChatAggregationSec,
        streamTierSec: schedulerStreamTierSec,
      },
    },
    failedMetrics,
    criticals,
    warnings,
    summary:
      status === "HEALTHY"
        ? `[정상] 활성 채널 ${activeStreams}개, WebSocket ${activeWebsockets ?? "N/A"}개, Kafka ${producerTps.toFixed(1)} TPS (Lag: ${kafkaLag}), 분석 P95 ${p95LatencyMs}ms, Ingestion ${schedulerIngestionSec ?? "N/A"}s, Highlight ${schedulerHighlightSec ?? "N/A"}s로 전 항목 정상 가동 중입니다.`
        : `[${status}] ${[...criticals, ...warnings].join(" / ")}`,
  };
}
