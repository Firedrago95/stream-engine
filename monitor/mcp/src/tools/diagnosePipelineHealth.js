import { queryPromql } from "../grafana-client.js";

export async function diagnosePipelineHealth() {
  const [
    activeStreamsRes,
    producerTpsRes,
    consumerTpsRes,
    kafkaLagRes,
    p95LatencyRes,
    liveThreadsRes,
  ] = await Promise.allSettled([
    queryPromql("engine_active_streams"),
    queryPromql("sum(kafka_producer_record_send_rate)"),
    queryPromql("sum(kafka_consumer_fetch_manager_records_consumed_rate)"),
    queryPromql("sum(kafka_consumer_fetch_manager_records_lag)"),
    queryPromql("histogram_quantile(0.95, sum(rate(analysis_processing_time_seconds_bucket[5m])) by (le))"),
    queryPromql("jvm_threads_live_threads{application=\"engine\"}"),
  ]);

  const activeStreams = Number(activeStreamsRes.value?.[0]?.value?.[1] ?? 0);
  const producerTps = Number(producerTpsRes.value?.[0]?.value?.[1] ?? 0);
  const consumerTps = Number(consumerTpsRes.value?.[0]?.value?.[1] ?? 0);
  const kafkaLag = Number(kafkaLagRes.value?.[0]?.value?.[1] ?? 0);
  const p95LatencySec = Number(p95LatencyRes.value?.[0]?.value?.[1] ?? 0);
  const p95LatencyMs = Number((p95LatencySec * 1000).toFixed(2));
  const liveThreads = Number(liveThreadsRes.value?.[0]?.value?.[1] ?? 0);

  const warnings = [];
  const criticals = [];

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
    status = "WARNING";
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
    criticals,
    warnings,
    summary:
      status === "HEALTHY"
        ? `[정상] 활성 채널 ${activeStreams}개, Kafka ${producerTps.toFixed(1)} TPS (Lag: ${kafkaLag}), 분석 P95 지연 ${p95LatencyMs}ms로 사일런트 페일러 없이 정상 가동 중입니다.`
        : `[${status}] ${[...criticals, ...warnings].join(" / ")}`,
  };
}
