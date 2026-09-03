import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";

import { diagnosePipelineHealth } from "./tools/diagnosePipelineHealth.js";
import { inspectChannelFirepower } from "./tools/inspectChannelFirepower.js";
import { checkKafkaBottleneck } from "./tools/checkKafkaBottleneck.js";
import { scanSystemErrors } from "./tools/scanSystemErrors.js";
import { queryPromql, queryPromqlRange, queryLogql } from "./grafana-client.js";

const server = new Server(
  {
    name: "slice-observability-mcp",
    version: "1.0.0",
  },
  {
    capabilities: {
      tools: {},
    },
  }
);

server.setRequestHandler(ListToolsRequestSchema, async () => {
  return {
    tools: [
      {
        name: "diagnose_pipeline_health",
        description:
          "실시간 채팅 수집/분석 파이프라인 전수 진단. 활성 스트림 수, Kafka Lag, 처리 지연(P95), 인입/소비 매칭을 교차 검증하여 에러 로그가 없는 '사일런트 페일러'를 즉시 탐지합니다.",
        inputSchema: {
          type: "object",
          properties: {},
        },
      },
      {
        name: "inspect_channel_firepower",
        description:
          "특정 스트리머 또는 채널의 실시간 수집 상태 및 화력 심층 진단. WebSocket 연결 상태, 최근 채팅 인입량, PEAK 시그널 전송 여부를 확인합니다.",
        inputSchema: {
          type: "object",
          properties: {
            channelIdOrName: {
              type: "string",
              description: "스트리머 이름(예: 텐코 시부키, 침착맨) 또는 채널 ID/해시값",
            },
          },
          required: ["channelIdOrName"],
        },
      },
      {
        name: "check_kafka_bottleneck",
        description:
          "카프카 스트리밍 파이프라인 레이턴시 및 처리량 점검. 초당 인입 TPS, 소비 TPS, Consumer Lag, JVM 힙 메모리 및 가상 스레드 활성 상태를 확인합니다.",
        inputSchema: {
          type: "object",
          properties: {},
        },
      },
      {
        name: "scan_system_errors",
        description:
          "Loki 연동 스마트 에러 로그 요약. 수천 줄의 로그 대신 웹소켓 끊김, 세션 마감, Redis 쓰기 등 실제 장애 유발 에러만 압축 요약합니다.",
        inputSchema: {
          type: "object",
          properties: {
            limit: {
              type: "number",
              description: "조회할 최대 에러 로그 수 (기본값: 50)",
            },
          },
        },
      },
      {
        name: "query_promql",
        description: "Grafana Cloud Prometheus(Mimir)에 임의의 PromQL 즉시 쿼리를 실행합니다.",
        inputSchema: {
          type: "object",
          properties: {
            query: {
              type: "string",
              description: "실행할 PromQL 쿼리문",
            },
            time: {
              type: "number",
              description: "선택적 Unix 타임스탬프 (초 단위)",
            },
          },
          required: ["query"],
        },
      },
      {
        name: "query_promql_range",
        description: "Grafana Cloud Prometheus(Mimir)에 범위(Range) PromQL 쿼리를 실행합니다.",
        inputSchema: {
          type: "object",
          properties: {
            query: {
              type: "string",
              description: "실행할 PromQL 쿼리문",
            },
            start: {
              type: "number",
              description: "시작 Unix 타임스탬프 (초 단위)",
            },
            end: {
              type: "number",
              description: "종료 Unix 타임스탬프 (초 단위)",
            },
            step: {
              type: "string",
              description: "쿼리 스텝 (예: '15s', '1m')",
            },
          },
          required: ["query", "start", "end"],
        },
      },
      {
        name: "query_logql",
        description: "Grafana Cloud Loki에 임의의 LogQL 쿼리를 실행하여 최근 로그를 검색합니다.",
        inputSchema: {
          type: "object",
          properties: {
            query: {
              type: "string",
              description: "실행할 LogQL 쿼리문 (예: '{filename=~\".*engine.*\"} |= \"PEAK\"')",
            },
            limit: {
              type: "number",
              description: "가져올 최대 로그 라인 수 (기본값: 30)",
            },
          },
          required: ["query"],
        },
      },
    ],
  };
});

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args = {} } = request.params;

  try {
    let result;
    switch (name) {
      case "diagnose_pipeline_health":
        result = await diagnosePipelineHealth();
        break;
      case "inspect_channel_firepower":
        result = await inspectChannelFirepower(args);
        break;
      case "check_kafka_bottleneck":
        result = await checkKafkaBottleneck();
        break;
      case "scan_system_errors":
        result = await scanSystemErrors(args);
        break;
      case "query_promql":
        result = await queryPromql(args.query, args.time);
        break;
      case "query_promql_range":
        result = await queryPromqlRange(args.query, args.start, args.end, args.step);
        break;
      case "query_logql":
        result = await queryLogql(args.query, args.limit);
        break;
      default:
        throw new Error(`알 수 없는 도구: ${name}`);
    }

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(result, null, 2),
        },
      ],
    };
  } catch (error) {
    return {
      isError: true,
      content: [
        {
          type: "text",
          text: `도구 실행 실패 (${name}): ${error.message}`,
        },
      ],
    };
  }
});

async function run() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

run().catch((error) => {
  process.stderr.write(`MCP 서버 실행 오류: ${error.stack || error}\n`);
  process.exit(1);
});
