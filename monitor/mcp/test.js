import { diagnosePipelineHealth } from "./src/tools/diagnosePipelineHealth.js";
import { checkKafkaBottleneck } from "./src/tools/checkKafkaBottleneck.js";
import { scanSystemErrors } from "./src/tools/scanSystemErrors.js";
import { inspectChannelFirepower } from "./src/tools/inspectChannelFirepower.js";

async function runHealthCheck() {
  console.log("==================================================");
  console.log("  🔍 Slice Stream Engine 실시간 원클릭 진단 리포트");
  console.log("==================================================\n");

  const [health, kafka, errors, channel] = await Promise.all([
    diagnosePipelineHealth(),
    checkKafkaBottleneck(),
    scanSystemErrors({ limit: 10 }),
    inspectChannelFirepower({ channelIdOrName: "PEAK" }),
  ]);

  console.log("1️⃣ [파이프라인 전수 진단 (사일런트 페일러 탐지)]");
  console.log(`- 상태: ${health.status}`);
  console.log(`- 요약: ${health.summary}`);
  console.log(`- 세부: 활성 스트림 ${health.metrics.activeStreams}개 | TPS ${health.metrics.producerTps} | Lag ${health.metrics.kafkaLag} | P95 지연 ${health.metrics.p95LatencyMs}ms\n`);

  console.log("2️⃣ [카프카 스트리밍 파이프라인 점검]");
  console.log(`- 상태: ${kafka.status} (병목: ${kafka.bottleneck})`);
  console.log(`- 요약: ${kafka.summary}`);
  console.log(`- 메모리/스레드: Heap ${kafka.metrics.heapUsagePercent} (${kafka.metrics.heapUsedMb}MB / ${kafka.metrics.heapMaxMb}MB) | 활성 스레드 ${kafka.metrics.liveThreads}개\n`);

  console.log("3️⃣ [Loki 에러 로그 요약]");
  console.log(`- 요약: ${errors.summary}`);
  console.log(`- 분류: 웹소켓 ${errors.counts.wsErrors}건 | 세션요약 ${errors.counts.summaryErrors}건 | 분석 ${errors.counts.analysisErrors}건\n`);

  console.log("4️⃣ [특정 채널/키워드 수집 추적 (PEAK 화력)]");
  console.log(`- 상태: ${channel.status}`);
  console.log(`- 요약: ${channel.summary}\n`);
  console.log("==================================================");
}

runHealthCheck().catch(console.error);
