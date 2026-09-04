import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";

import { diagnosePipelineHealth } from "../src/tools/diagnosePipelineHealth.js";
import { checkKafkaBottleneck } from "../src/tools/checkKafkaBottleneck.js";
import { scanSystemErrors } from "../src/tools/scanSystemErrors.js";
import { inspectChannelFirepower } from "../src/tools/inspectChannelFirepower.js";

describe("Slice Observability Tools Unit Tests (Mock Fetch)", () => {
  const originalFetch = globalThis.fetch;

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  describe("diagnosePipelineHealth", () => {
    it("모든 지표가 정상이면 HEALTHY를 반환한다", async () => {
      globalThis.fetch = async (url) => {
        const u = new URL(url);
        const query = u.searchParams.get("query") || "";

        let val = "10";
        if (query.includes("engine_active_streams")) val = "200";
        if (query.includes("kafka_producer_record_send_rate")) val = "50";
        if (query.includes("kafka_consumer_fetch_manager_records_consumed_rate")) val = "50";
        if (query.includes("records_lag")) val = "0";
        if (query.includes("analysis_processing_time")) val = "0.01";
        if (query.includes("jvm_threads")) val = "45";

        return {
          ok: true,
          status: 200,
          json: async () => ({
            status: "success",
            data: {
              resultType: "vector",
              result: [{ metric: {}, value: [1000, val] }],
            },
          }),
        };
      };

      const result = await diagnosePipelineHealth();
      assert.equal(result.status, "HEALTHY");
      assert.equal(result.metrics.activeStreams, 200);
      assert.equal(result.metrics.kafkaLag, 0);
      assert.equal(result.metrics.producerTps, 50);
      assert.equal(result.failedMetrics.length, 0);
    });

    it("필수 지표(예: kafkaLag) 조회가 실패하면 UNKNOWN 상태를 반환하고 에러를 명시한다", async () => {
      globalThis.fetch = async (url) => {
        const u = new URL(url);
        const query = u.searchParams.get("query") || "";

        if (query.includes("records_lag")) {
          return {
            ok: false,
            status: 500,
            text: async () => "Internal Server Error",
          };
        }

        return {
          ok: true,
          status: 200,
          json: async () => ({
            status: "success",
            data: {
              result: [{ metric: {}, value: [1000, "10"] }],
            },
          }),
        };
      };

      const result = await diagnosePipelineHealth();
      assert.equal(result.status, "UNKNOWN");
      assert.ok(result.failedMetrics.some((f) => f.key === "kafkaLag"));
      assert.ok(result.summary.includes("측정 불가/UNKNOWN"));
    });

    it("보조 지표(liveThreads)만 실패한 경우 DEGRADED 상태를 반환한다", async () => {
      globalThis.fetch = async (url) => {
        const u = new URL(url);
        const query = u.searchParams.get("query") || "";

        if (query.includes("jvm_threads")) {
          return {
            ok: false,
            status: 500,
            text: async () => "Threads Unavailable",
          };
        }

        let val = "10";
        if (query.includes("engine_active_streams")) val = "100";
        if (query.includes("records_lag")) val = "0";
        if (query.includes("analysis_processing_time")) val = "0.01";

        return {
          ok: true,
          status: 200,
          json: async () => ({
            status: "success",
            data: {
              result: [{ metric: {}, value: [1000, val] }],
            },
          }),
        };
      };

      const result = await diagnosePipelineHealth();
      assert.equal(result.status, "DEGRADED");
      assert.equal(result.failedMetrics.length, 1);
      assert.equal(result.failedMetrics[0].key, "liveThreads");
    });
  });

  describe("checkKafkaBottleneck", () => {
    it("필수 카프카 지표 실패 시 UNKNOWN을 반환한다", async () => {
      globalThis.fetch = async () => ({
        ok: false,
        status: 503,
        text: async () => "Prometheus Down",
      });

      const result = await checkKafkaBottleneck();
      assert.equal(result.status, "UNKNOWN");
      assert.equal(result.bottleneck, "UNKNOWN");
      assert.ok(result.failedMetrics.length > 0);
    });
  });

  describe("scanSystemErrors", () => {
    it("limit가 100을 초과하면 100으로 캡핑하고 JSON 파싱 에러를 분류한다", async () => {
      let requestedLimit = null;
      globalThis.fetch = async (url) => {
        const u = new URL(url);
        requestedLimit = u.searchParams.get("limit");

        return {
          ok: true,
          status: 200,
          json: async () => ({
            status: "success",
            data: {
              result: [
                {
                  stream: {},
                  values: [
                    ["1001", "2026-09-04 ERROR i.s.s.e.c.i.c.w.ChzzkWebSocketListener : JsonMappingException: Unrecognized field"],
                    ["1002", "2026-09-04 ERROR i.s.s.e.c.i.c.w.ChzzkWebSocketListener : [Stream1] 웹소켓 에러 발생"],
                  ],
                },
              ],
            },
          }),
        };
      };

      const result = await scanSystemErrors({ limit: 500, lookbackMinutes: 30 });
      assert.equal(requestedLimit, "100");
      assert.equal(result.counts.jsonParsingErrors, 1);
      assert.equal(result.counts.wsErrors, 1);
      assert.equal(result.totalErrors, 2);
    });
  });

  describe("inspectChannelFirepower", () => {
    it("특수문자나 따옴표가 포함된 검색어 입력 시 예외를 던진다 (LogQL Injection 방어)", async () => {
      await assert.rejects(
        async () => {
          await inspectChannelFirepower({ channelIdOrName: 'test" |= "hack' });
        },
        /유효하지 않은 검색어 형식입니다/
      );
    });

    it("정상 채널명 입력 시 이스케이프된 쿼리로 실행된다", async () => {
      let executedQuery = null;
      globalThis.fetch = async (url) => {
        const u = new URL(url);
        executedQuery = u.searchParams.get("query");
        return {
          ok: true,
          status: 200,
          json: async () => ({
            status: "success",
            data: { result: [] },
          }),
        };
      };

      const result = await inspectChannelFirepower({ channelIdOrName: "텐코 시부키" });
      assert.equal(result.status, "NOT_FOUND");
      assert.ok(executedQuery.includes("텐코 시부키"));
    });
  });
});
