import path from "node:path";
import { fileURLToPath } from "node:url";

if (!process.env.GRAFANA_TOKEN) {
  try {
    const __dirname = path.dirname(fileURLToPath(import.meta.url));
    const envPath = path.resolve(__dirname, "../../../.env");
    process.loadEnvFile(envPath);
  } catch {
  }
}

const GRAFANA_URL = process.env.GRAFANA_URL || "https://cheesepick.grafana.net";
const GRAFANA_TOKEN = process.env.GRAFANA_TOKEN;
const PROM_UID = process.env.PROMETHEUS_DATASOURCE_UID || "grafanacloud-prom";
const LOKI_UID = process.env.LOKI_DATASOURCE_UID || "grafanacloud-logs";

function getAuthHeader() {
  if (!GRAFANA_TOKEN) {
    throw new Error("GRAFANA_TOKEN 환경 변수가 설정되지 않았습니다. (.env 파일 또는 환경 변수를 확인하세요)");
  }
  return `Bearer ${GRAFANA_TOKEN}`;
}

export async function queryPromql(promql, time = null) {
  const url = new URL(`${GRAFANA_URL}/api/datasources/proxy/uid/${PROM_UID}/api/v1/query`);
  url.searchParams.set("query", promql);
  if (time) {
    url.searchParams.set("time", time.toString());
  }

  const response = await fetch(url.toString(), {
    method: "GET",
    headers: {
      Authorization: getAuthHeader(),
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Prometheus 쿼리 실패 (${response.status}): ${errorText}`);
  }

  const json = await response.json();
  if (json.status !== "success") {
    throw new Error(`Prometheus 오류: ${json.error || "알 수 없는 오류"}`);
  }

  return json.data.result;
}

export async function queryPromqlRange(promql, start, end, step = "15s") {
  const url = new URL(`${GRAFANA_URL}/api/datasources/proxy/uid/${PROM_UID}/api/v1/query_range`);
  url.searchParams.set("query", promql);
  url.searchParams.set("start", start.toString());
  url.searchParams.set("end", end.toString());
  url.searchParams.set("step", step);

  const response = await fetch(url.toString(), {
    method: "GET",
    headers: {
      Authorization: getAuthHeader(),
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Prometheus 범위 쿼리 실패 (${response.status}): ${errorText}`);
  }

  const json = await response.json();
  if (json.status !== "success") {
    throw new Error(`Prometheus 오류: ${json.error || "알 수 없는 오류"}`);
  }

  return json.data.result;
}

export async function queryLogql(logql, limit = 50, start = null, end = null, direction = "backward") {
  const url = new URL(`${GRAFANA_URL}/api/datasources/proxy/uid/${LOKI_UID}/loki/api/v1/query_range`);
  url.searchParams.set("query", logql);
  url.searchParams.set("limit", limit.toString());
  url.searchParams.set("direction", direction);
  if (start) {
    url.searchParams.set("start", start.toString());
  }
  if (end) {
    url.searchParams.set("end", end.toString());
  }

  const response = await fetch(url.toString(), {
    method: "GET",
    headers: {
      Authorization: getAuthHeader(),
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Loki 쿼리 실패 (${response.status}): ${errorText}`);
  }

  const json = await response.json();
  if (json.status !== "success") {
    throw new Error(`Loki 오류: ${json.error || "알 수 없는 오류"}`);
  }

  return json.data.result;
}
