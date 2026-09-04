import { queryLogql } from "../grafana-client.js";

export async function scanSystemErrors({ limit = 50, lookbackMinutes = 15 } = {}) {
  const safeLimit = Math.max(1, Math.min(Number(limit) || 50, 100));
  const safeLookback = Math.max(1, Math.min(Number(lookbackMinutes) || 15, 120));

  const endNs = BigInt(Date.now()) * 1000000n;
  const startNs = endNs - (BigInt(safeLookback) * 60n * 1000n * 1000000n);

  const logql = '{filename=~".*engine.*"} |= "ERROR"';
  const logsResult = await queryLogql(logql, safeLimit, startNs.toString(), endNs.toString());

  const errorLines = [];
  if (Array.isArray(logsResult)) {
    for (const streamObj of logsResult) {
      if (streamObj.values && Array.isArray(streamObj.values)) {
        for (const [ts, line] of streamObj.values) {
          errorLines.push({ timestamp: ts, line });
        }
      }
    }
  }

  const categorized = {
    wsErrors: [],
    jsonParsingErrors: [],
    summaryErrors: [],
    analysisErrors: [],
    otherErrors: [],
  };

  for (const item of errorLines) {
    const text = item.line;
    if (text.includes("Json") || text.includes("Jackson") || text.includes("Unrecognized") || text.includes("deserialize") || text.includes("파싱")) {
      categorized.jsonParsingErrors.push(text);
    } else if (text.includes("ChzzkWebSocketListener") || text.includes("WebSocket") || text.includes("소켓")) {
      categorized.wsErrors.push(text);
    } else if (text.includes("Summary") || text.includes("세션")) {
      categorized.summaryErrors.push(text);
    } else if (text.includes("Analysis") || text.includes("Redis")) {
      categorized.analysisErrors.push(text);
    } else {
      categorized.otherErrors.push(text);
    }
  }

  const totalErrors = errorLines.length;
  let summary = "";
  if (totalErrors === 0) {
    summary = `최근 ${safeLookback}분간 수집된 에러 로그가 0건으로 시스템이 매우 안정적입니다.`;
  } else {
    const parts = [];
    if (categorized.jsonParsingErrors.length > 0) parts.push(`JSON 파싱/스펙 에러 ${categorized.jsonParsingErrors.length}건`);
    if (categorized.wsErrors.length > 0) parts.push(`웹소켓/네트워크 에러 ${categorized.wsErrors.length}건`);
    if (categorized.summaryErrors.length > 0) parts.push(`세션 요약/API 에러 ${categorized.summaryErrors.length}건`);
    if (categorized.analysisErrors.length > 0) parts.push(`분석/Redis 에러 ${categorized.analysisErrors.length}건`);
    if (categorized.otherErrors.length > 0) parts.push(`기타 에러 ${categorized.otherErrors.length}건`);
    summary = `최근 ${safeLookback}분간 에러 총 ${totalErrors}건 감지 (${parts.join(", ")})`;
  }

  return {
    lookbackMinutes: safeLookback,
    totalErrors,
    summary,
    counts: {
      jsonParsingErrors: categorized.jsonParsingErrors.length,
      wsErrors: categorized.wsErrors.length,
      summaryErrors: categorized.summaryErrors.length,
      analysisErrors: categorized.analysisErrors.length,
      otherErrors: categorized.otherErrors.length,
    },
    samples: {
      jsonParsingErrors: categorized.jsonParsingErrors.slice(0, 3),
      wsErrors: categorized.wsErrors.slice(0, 3),
      summaryErrors: categorized.summaryErrors.slice(0, 3),
      analysisErrors: categorized.analysisErrors.slice(0, 3),
      otherErrors: categorized.otherErrors.slice(0, 3),
    },
  };
}
