<div align="center">
  <h1>
    <img src="./client/public/cheese-pick-logo.png" alt="치즈픽 로고" width="60" align="absmiddle" />치즈픽 (Cheese-Pick)
  </h1>
  <p>
    <b>🚀 실시간 스트림 화력 분석 엔진</b> <br/>
    <a href="https://cheesepick.me">cheesepick.me 바로가기</a>
  </p>
</div>

<br/>

## 📖 프로젝트 소개
Java 25 가상 스레드(Virtual Threads) 기반의 고성능 실시간 스트림 데이터 수집 및 분석 엔진입니다.
라이브 스트리밍 플랫폼의 대규모 채팅 및 스트림 데이터를 실시간으로 수집하여, 화력이 폭발하는 하이라이트 구간을 자동으로 추출하는 서비스의 핵심 코어 시스템입니다.

---

## ✨ 핵심 기능
- **실시간 데이터 파이프라인**: API 및 WebSocket을 통한 스트림 상태 및 수만 건의 채팅 로그 실시간 수집
- **비동기 메시징 처리**: Apache Kafka를 이용한 대용량 채팅 트래픽 버퍼링 및 분산 처리
- **시계열 기반 화력 분석**: RedisTimeSeries 및 커스텀 Lua 스크립트를 활용한 구간별 채팅량 분석 및 `PEAK` 상태 감지
- **하이브리드 인프라 아키텍처**: 비용 최적화와 고가용성을 위해 On-Premise(엔진)와 Cloud(API 서버)로 모듈 분리 운영

---

## 🛠️ 기술 스택

### Backend (Core & API)
- **Language**: Java 25 (Virtual Threads)
- **Framework**: Spring Boot 4.0.1
- **Data & Messaging**: Spring Data Redis, Spring Kafka
- **Utils**: Spring Scheduling, Jackson, SLF4J

### Infrastructure
- **Database / Cache**: Redis 7.4 (Redis Stack / TimeSeries 내장)
- **Message Broker**: Apache Kafka (Kraft Mode)
- **Monitoring**: Grafana Alloy, Grafana Cloud
- **Security**: Cloudflare Tunnel (Zero Trust)

---

## 🏗️ 아키텍처
![architecture.png](client/public/architecture.png)
시스템은 역할에 따라 **Engine**, **API Server**, **Client**로 명확히 분리되어 동작합니다.

- **`engine` (On-Premise)**: 외부 API 개입 없이 순수하게 백그라운드에서 데이터를 수집하고 분석하는 헤드리스(Headless) 워커. 무거운 연산과 메모리 요구사항을 로컬 환경에서 처리하여 인프라 비용 최소화.
- **`api-server` (Oracle Cloud)**: 클라이언트가 접근하는 관문. Engine이 보내는 신호를 받아 검증하고, 유저 트래픽을 24시간 안정적으로 서빙.
- **`client`**: 수집된 하이라이트 데이터를 시각화하는 관리자/사용자용 웹 대시보드 (React, TypeScript).

---

## 💡 트러블슈팅 및 기술적 의사결정

### 1. 하이브리드 인프라 분리 및 망 분리 보안 구축
무거운 데이터 수집/분석(Engine)과 트래픽 서빙(API)의 역할을 분리하여 인프라 유지 비용을 0원으로 줄이면서도 높은 가용성을 확보했습니다.
- **인바운드 통제**: 홈 서버(Engine)의 포트포워딩 개방 위험을 막기 위해 **Cloudflare Tunnel**을 도입하여 Zero Trust 기반의 아웃바운드 터널링 구축.
- **데이터 전송 보안**: 분리된 망(Home ↔ Cloud) 간의 통신 구간에 HTTPS를 적용하고, 애플리케이션 계층에서 **커스텀 시크릿 헤더 검증 필터**를 구현하여 인가되지 않은 외부 접근을 원천 차단.

### 2. 통계적 오류를 극복한 하이라이트 탐지 알고리즘 고도화
단순 임계치(V1)에서 발생하는 오탐지와 중복 생성 문제를 해결하기 위해 **Z-Score 알고리즘(V2)**을 도입하였으나, 라이브 방송의 불규칙한 트래픽 패턴으로 인해 추가적인 한계에 직면했고 이를 다음과 같이 개선했습니다. (V3)
- **기준선 붕괴 방어**: 최근 채팅량에 따라 방송 체급을 동적으로 분류(1분/2분 윈도우)하고, Z-Score 임계치를 유동적으로 적용. 최소 화력 하한선을 두어 소규모 방송의 Z-Score 폭발 현상 방어.
- **평균의 함정(Masking) 해결**: Z-Score 계산 시 직전 10초의 데이터를 배제하여, 1차 피크가 평균 기준선을 오염시켜 2차 클라이맥스가 누락되는 현상 해결.
- **파편화 방지**: Caffeine Cache를 활용해 60초 내의 연속된 피크 이벤트를 하나의 세션으로 병합하고, `-20초 ~ +5초`의 버퍼링을 두어 시청 맥락이 유지되는 타임라인 완성.

### 3. 분산 환경의 통합 모니터링 (Observability)
물리적으로 분리된 두 서버의 파편화된 지표를 관리하기 위해, 각 환경에 **Grafana Alloy**를 수집기로 구축했습니다. 애플리케이션 로그, JVM 지표(가상 스레드, 힙), 시스템 리소스를 Grafana Cloud로 집중시켜 단일 뷰에서 실시간 모니터링할 수 있는 파이프라인을 완성했습니다.
