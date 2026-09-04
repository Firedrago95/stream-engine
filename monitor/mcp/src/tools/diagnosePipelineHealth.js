import { queryPromql } from "../grafana-client.js";

export async function diagnosePipelineHealth() {
  const queryDefinitions = [
    { key: "activeStreams", query: "engine_active_streams", required: true },
    { key: "producerTps", query: "sum(kafka_producer_record_send_rate)", required: true },
    { key: "consumerTps", query: "sum(kafka_consumer_fetch_manager_records_consumed_rate)", required: true },
    { key: "kafkaLag", query: "sum(kafka_consumer_fetch_manager_records_lag)", required: true },
    { key: "p95LatencySec", query: "histogram_quantile(0.95, sum(rate(analysis_processing_time_seconds_bucket[5m])) by (le))", required: true },
    { key: "liveThreads", query: 'jvm_threads_live_threads{application="engine"}', required: false },
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
      if (def.key === "kafkaLag" || def.key === "producerTps" || def.key === "consumerTps") {
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
  const liveThreads = parsedMetrics.liveThreads !== undefined ? Number(parsedMetrics.liveThreads) : null;

  const warnings = [];
  const criticals = [];

  if (failedMetrics.length > 0) {
    warnings.push(`일부 보조 지표 조회 실패: ${failedMetrics.map((f) => f.key).join(", ")}`);
  }

  if (activeStreams === 0) {
    criticals.push("수집 중인 활성 스트림 수가 0개입니다 (수집 엔진 중단 또는 웹소켓 미연결).");
  }

  if (kafkaLag >= 100) {
    criticals.push(`카프카 컨슈머 랙이 심각하게 누적되었습니다 (${kafkaLag}건).`);
  } else if (kafkaLag >= 10) {
    warnings.push(`카프카 컨슈머 랙이 발생하고 있습니다 (${kafkaLag}건).`);
  }

  if (p95LatencyMs >= 100) {
    criticals.push(`분석 P95 지연 시간이 위험 수준입니다 (${p95LatencyMs}ms).`);
  } else if (p95LatencyMs >= 50) {
    warnings.push(`분석 P95 지연 시간이 경고 수준입니다 (${p95LatencyMs}ms).`);
  }

  if (producerTps > 0 && consumerTps === 0) {
    criticals.push("프로듀서는 발행 중이나 컨슈머 소비가 전면 중단되었습니다.");
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
      producerTps: Number(producerTps.toFixed(1)),
      consumerTps: Number(consumerTps.toFixed(1)),
      kafkaLag,
      p95LatencyMs,
      liveThreads,
    },
    failedMetrics,
    criticals,
    warnings,
    summary:
      status === "HEALTHY"
        ? `[정상] 활성 채널 ${activeStreams}개, Kafka ${producerTps.toFixed(1)} TPS (Lag: ${kafkaLag}), 분석 P95 지연 ${p95LatencyMs}ms로 사일런트 페일러 없이 정상 가동 중입니다.`
        : `[${status}] ${[...criticals, ...warnings].join(" / ")}`,
  };
}
