import { queryPromql } from "../grafana-client.js";

export async function checkKafkaBottleneck() {
  const [
    producerTpsRes,
    consumerTpsRes,
    byteRateRes,
    lagRes,
    fetchLatencyRes,
    liveThreadsRes,
    heapUsedRes,
    heapMaxRes,
  ] = await Promise.allSettled([
    queryPromql("sum(kafka_producer_record_send_rate)"),
    queryPromql("sum(kafka_consumer_fetch_manager_records_consumed_rate)"),
    queryPromql("sum(kafka_producer_topic_byte_rate)"),
    queryPromql("sum(kafka_consumer_fetch_manager_records_lag)"),
    queryPromql("avg(kafka_consumer_fetch_manager_fetch_latency_avg)"),
    queryPromql("jvm_threads_live_threads{application=\"engine\"}"),
    queryPromql("sum(jvm_memory_used_bytes{area=\"heap\",application=\"engine\"})"),
    queryPromql("sum(jvm_memory_max_bytes{area=\"heap\",application=\"engine\"})"),
  ]);

  const producerTps = Number(producerTpsRes.value?.[0]?.value?.[1] ?? 0);
  const consumerTps = Number(consumerTpsRes.value?.[0]?.value?.[1] ?? 0);
  const byteRate = Number(byteRateRes.value?.[0]?.value?.[1] ?? 0);
  const byteRateKb = Number((byteRate / 1024).toFixed(1));
  const kafkaLag = Number(lagRes.value?.[0]?.value?.[1] ?? 0);
  const fetchLatencyMs = Number(Number(fetchLatencyRes.value?.[0]?.value?.[1] ?? 0).toFixed(2));
  const liveThreads = Number(liveThreadsRes.value?.[0]?.value?.[1] ?? 0);
  const heapUsedMb = Number((Number(heapUsedRes.value?.[0]?.value?.[1] ?? 0) / (1024 * 1024)).toFixed(1));
  const heapMaxMb = Number((Number(heapMaxRes.value?.[0]?.value?.[1] ?? 0) / (1024 * 1024)).toFixed(1));
  const heapUsagePercent = heapMaxMb > 0 ? Number(((heapUsedMb / heapMaxMb) * 100).toFixed(1)) : 0;

  let bottleneck = "NONE";
  const issues = [];

  if (kafkaLag > 50) {
    bottleneck = "CONSUMER_LAG";
    issues.push(`소비 지연 누적 (Lag: ${kafkaLag})`);
  }

  if (heapUsagePercent > 85) {
    bottleneck = "HEAP_EXHAUSTION";
    issues.push(`힙 메모리 고갈 위험 (${heapUsagePercent}%)`);
  }

  return {
    status: bottleneck === "NONE" ? "OPTIMAL" : "BOTTLENECK_DETECTED",
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
    issues,
    summary:
      bottleneck === "NONE"
        ? `[최적 상태] Producer ${producerTps.toFixed(1)} TPS / Consumer ${consumerTps.toFixed(1)} TPS 균형, Lag ${kafkaLag}건, 힙 사용률 ${heapUsagePercent}%, 활성 스레드 ${liveThreads}개로 처리 여유도(Headroom) 충분합니다.`
        : `[병목 감지] ${issues.join(", ")}`,
  };
}
