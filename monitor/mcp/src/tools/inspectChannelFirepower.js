import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { queryLogql } from "../grafana-client.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const mapPath = path.resolve(__dirname, "../data/streamerMap.json");
let streamerMap = {};
try {
  streamerMap = JSON.parse(fs.readFileSync(mapPath, "utf-8"));
} catch (e) {
  // fallback empty map
}

function resolveChannel(input) {
  const raw = input.trim();
  if (/^[a-f0-9]{32}$/i.test(raw)) {
    const id = raw.toLowerCase();
    for (const [name, mappedId] of Object.entries(streamerMap)) {
      if (mappedId.toLowerCase() === id) {
        return { channelId: id, streamerName: name };
      }
    }
    return { channelId: id, streamerName: raw };
  }

  const normalized = raw.replace(/\s+/g, "");
  if (streamerMap[raw]) {
    return { channelId: streamerMap[raw], streamerName: raw };
  }
  if (streamerMap[normalized]) {
    return { channelId: streamerMap[normalized], streamerName: raw };
  }

  for (const [name, mappedId] of Object.entries(streamerMap)) {
    if (name.includes(raw) || raw.includes(name)) {
      return { channelId: mappedId, streamerName: name };
    }
  }

  return { channelId: raw, streamerName: raw };
}

export async function inspectChannelFirepower({ channelIdOrName }) {
  if (!channelIdOrName || typeof channelIdOrName !== "string" || channelIdOrName.trim().length === 0) {
    throw new Error("channelIdOrName 파라미터가 필요합니다.");
  }

  const { channelId, streamerName } = resolveChannel(channelIdOrName);
  const keyword = channelId;

  if (!/^[a-zA-Z0-9가-힣\s\-_]{1,50}$/.test(keyword)) {
    throw new Error("유효하지 않은 검색어 형식입니다. (영문, 숫자, 한글, 공백, 하이픈, 언더스코어 1~50자만 허용)");
  }

  const safeKeyword = keyword.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
  const logql = `{filename=~".*(engine|collector).*"} |= "${safeKeyword}"`;

  const endNs = BigInt(Date.now()) * 1000000n;
  const startNs = endNs - (30n * 60n * 1000n * 1000000n);

  const logsResult = await queryLogql(logql, 30, startNs.toString(), endNs.toString());
  const logLines = [];

  if (Array.isArray(logsResult)) {
    for (const streamObj of logsResult) {
      if (streamObj.values && Array.isArray(streamObj.values)) {
        for (const [ts, line] of streamObj.values) {
          logLines.push({ timestamp: ts, line });
        }
      }
    }
  }

  logLines.sort((a, b) => (a.timestamp < b.timestamp ? 1 : -1));

  const peakLogs = logLines.filter((l) => l.line.includes("PEAK") || l.line.includes("시그널"));
  const errorLogs = logLines.filter((l) => l.line.includes("ERROR") || l.line.includes("Exception"));
  const wsLogs = logLines.filter((l) => l.line.includes("WebSocket") || l.line.includes("연결") || l.line.includes("수집"));

  let status = "ACTIVE";
  let summary = "";
  const displayName = streamerName && streamerName !== channelId ? `${streamerName} (${channelId.slice(0, 8)}...)` : channelId;

  if (logLines.length === 0) {
    status = "NOT_FOUND";
    summary = `'${displayName}' 관련 최근 로그가 없습니다. 방송이 종료되었거나 엔진 수집 대상에 포함되지 않았을 수 있습니다.`;
  } else if (errorLogs.length > 0) {
    status = "ERROR";
    summary = `'${displayName}' 관련 최근 에러가 감지되었습니다: ${errorLogs[0].line}`;
  } else if (peakLogs.length > 0) {
    status = "ACTIVE_WITH_PEAK";
    summary = `'${displayName}' 정상 수집 중이며 최근 화력 PEAK 시그널이 감지되었습니다. (${peakLogs[0].line})`;
  } else {
    status = "ACTIVE_NORMAL";
    summary = `'${displayName}' 정상 수집 중이며 안정적인 트래픽을 처리하고 있습니다. (최근 로그 ${logLines.length}건)`;
  }

  return {
    streamerName: streamerName || channelIdOrName,
    channelId,
    status,
    summary,
    totalLogsFound: logLines.length,
    recentLogs: logLines.slice(0, 5).map((l) => l.line),
  };
}
