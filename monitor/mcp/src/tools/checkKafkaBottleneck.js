import { queryPromql } from "../grafana-client.js";

export async function checkKafkaBottleneck() {
  const queryDefinitions = [
    { key: "producerTps", query: "sum(kafka_producer_record_send_rate)", required: true },
    { key: "consumerTps", query: "sum(kafka_consumer_fetch_manager_records_consumed_rate)", required: true },
    { key: "kafkaLag", query: "sum(kafka_consumer_fetch_manager_records_lag)", required: true },
    { key: "byteRate", query: "sum(kafka_producer_topic_byte_rate)", required: false },
    { key: "fetchLatencyMs", query: "avg(kafka_consumer_fetch_manager_fetch_latency_avg)", required: false },
    { key: "liveThreads", query: 'jvm_threads_live_threads{application="engine"}', required: false },
    { key: "heapUsedBytes", query: 'sum(jvm_memory_used_bytes{area="heap",application="engine"})', required: false },
    { key: "heapMaxBytes", query: 'sum(jvm_memory_max_bytes{area="heap",application="engine"})', required: false },
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
      bottleneck: "UNKNOWN",
      timestamp: new Date().toISOString(),
      metrics: parsedMetrics,
      failedMetrics,
      issues: [],
      summary: `[측정 불가/UNKNOWN] 필수 카프카 지표 조회 실패로 병목 여부를 진단할 수 없습니다. (실패 항목: ${failedNames})`,
    };
  }

  const producerTps = Number(parsedMetrics.producerTps ?? 0);
  const consumerTps = Number(parsedMetrics.consumerTps ?? 0);
  const kafkaLag = Number(parsedMetrics.kafkaLag ?? 0);
  const byteRate = Number(parsedMetrics.byteRate ?? 0);
  const byteRateKb = Number((byteRate / 1024).toFixed(1));
  const fetchLatencyMs = Number(Number(parsedMetrics.fetchLatencyMs ?? 0).toFixed(2));
  const liveThreads = parsedMetrics.liveThreads !== undefined ? Number(parsedMetrics.liveThreads) : null;
  const heapUsedMb = parsedMetrics.heapUsedBytes !== undefined ? Number((Number(parsedMetrics.heapUsedBytes) / (1024 * 1024)).toFixed(1)) : null;
  const heapMaxMb = parsedMetrics.heapMaxBytes !== undefined ? Number((Number(parsedMetrics.heapMaxBytes) / (1024 * 1024)).toFixed(1)) : null;
  const heapUsagePercent = heapMaxMb && heapMaxMb > 0 ? Number(((heapUsedMb / heapMaxMb) * 100).toFixed(1)) : 0;

  let bottleneck = "NONE";
  const issues = [];

  if (failedMetrics.length > 0) {
    issues.push(`일부 보조 지표 조회 실패: ${failedMetrics.map((f) => f.key).join(", ")}`);
  }

  if (kafkaLag > 50) {
    bottleneck = "CONSUMER_LAG";
    issues.push(`소비 지연 누적 (Lag: ${kafkaLag})`);
  }

  if (heapUsagePercent > 85) {
    bottleneck = "HEAP_EXHAUSTION";
    issues.push(`힙 메모리 고갈 위험 (${heapUsagePercent}%)`);
  }

  let status = "OPTIMAL";
  if (bottleneck !== "NONE") {
    status = "BOTTLENECK_DETECTED";
  } else if (failedMetrics.length > 0) {
    status = "DEGRADED";
  }

  return {
    status,
    bottleneck,
    metrics: {
      producerTps: Number(producerTps.toFixed(1)),
      consumerTps: Number(consumerTps.toFixed(1)),
      throughputKbPerSec: byteRateKb,
      consumerLag: kafkaLag,
      fetchLatencyMs,
      liveThreads,
      heapUsedMb,
      heapMaxMb,
      heapUsagePercent: `${heapUsagePercent}%`,
    },
    failedMetrics,
    issues,
    summary:
      status === "OPTIMAL"
        ? `[최적 상태] Producer ${producerTps.toFixed(1)} TPS / Consumer ${consumerTps.toFixed(1)} TPS 균형, Lag ${kafkaLag}건, 힙 사용률 ${heapUsagePercent}%, 활성 스레드 ${liveThreads}개로 처리 여유도(Headroom) 충분합니다.`
        : `[${status}] ${issues.join(", ")}`,
  };
}
