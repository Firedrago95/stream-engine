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
라이브 스트리밍 유튜브 편집자와 시청자들을 위한 **쇼츠형 하이라이트 자동 추출 서비스**입니다.  
수 시간 분량의 풀영상 다시보기를 일일이 탐색해야 하는 비효율을 해결하기 위해, 실시간 채팅 데이터의 화력을 분석하여 노래나 재미있는 클라이맥스 구간의 타임스탬프를 자동으로 생성해 줍니다.



## ✨ 핵심 기능
- **실시간 데이터 파이프라인**: API 및 WebSocket을 통한 스트림 상태 및 수만 건의 채팅 로그 실시간 수집
- **비동기 메시징 처리**: Apache Kafka를 이용한 대용량 채팅 트래픽 버퍼링 및 분산 처리
- **시계열 기반 화력 분석**: RedisTimeSeries 및 커스텀 Lua 스크립트를 활용한 구간별 채팅량 분석 및 `PEAK` 상태 감지
- **과거 방송 세션 아카이빙**: 방송별 하이라이트 및 화력 타임라인 영속화와 30일 데이터 수명주기(Lifecycle) 관리
- **하이브리드 인프라 아키텍처**: 비용 최적화와 고가용성을 위해 On-Premise(엔진)와 Cloud(API 서버)로 모듈 분리 운영



## 🛠️ 기술 스택

### Backend (Core & API)
- **Language**: Java 25 (Virtual Threads)
- **Framework**: Spring Boot 4.0.1
- **Data & Messaging**: Spring Data JPA, Spring Data Redis, Spring Kafka
- **Utils**: Spring Scheduling, Jackson, SLF4J

### Infrastructure
- **Database / Cache**: PostgreSQL 17, Redis 7.4 (Redis Stack / TimeSeries 내장)
- **Message Broker**: Apache Kafka (Kraft Mode)
- **Monitoring**: Grafana Alloy, Grafana Cloud
- **Security**: Cloudflare Tunnel (Zero Trust)



## 🏗️ 아키텍처
![architecture.png](client/public/architecture.png)
시스템은 역할에 따라 **Engine**, **API Server**, **Client**로 명확히 분리되어 동작합니다.

- **`engine` (On-Premise)**: 외부 API 개입 없이 순수하게 백그라운드에서 데이터를 수집하고 분석하는 헤드리스(Headless) 워커. 무거운 연산과 메모리 요구사항을 로컬 환경에서 처리하여 인프라 비용 최소화.
- **`api-server` (Oracle Cloud)**: 클라이언트가 접근하는 관문. Engine이 보내는 신호를 받아 검증 및 PostgreSQL에 영속화하고, 유저 트래픽과 과거 방송 데이터를 24시간 안정적으로 서빙.
- **`client`**: 수집된 하이라이트 데이터를 시각화하는 관리자/사용자용 웹 대시보드 (React, TypeScript).



## 💡 트러블슈팅 및 기술적 의사결정

### 1. JFR 실측 기반 스레드 고갈 병목 해결 (가상 스레드 도입)
200개 방송(2,000TPS) 모의 부하 테스트 중 CPU 사용률은 13%로 낮았으나 P95 지연시간이 1.2초까지 치솟고 221개의 스레드가 `TIMED_WAITING`에 빠지는 병목을 겪었습니다.
- **원인 규명**: JFR(Java Flight Recorder) 프로파일링을 통해 API Rate Limit 지연(0.6초)과 Keep-Alive Ping 루프의 `Thread.sleep`이 플랫폼 스레드를 점유하는 블로킹 원인임을 특정.
- **가상 스레드 전환**: 가상 스레드를 도입하여 동기식 코드 흐름을 유지하면서 블로킹 I/O를 비차단으로 전환. 3,000TPS 부하에서도 P95 지연시간을 32ms로 단축(97% 개선)하고 활성 스레드 점유율을 80% 감소(244개 ➔ 48개).

### 2. 이벤트 기반 비동기 파이프라인 구축 (Apache Kafka & Redis TimeSeries)
수집과 분석 로직이 동기적으로 묶여 있어 트래픽 스파이크 시 전체 세션이 끊어질 위험과 DB 쓰기 병목을 해결했습니다.
- **도메인 격리 및 배압 제어**: 수집(Ingestion)과 분석(Analysis) 계층 사이에 Kafka를 배치하여 트래픽 스파이크를 흡수하는 완충 구조 설계.
- **메모리 1차 압축 및 시계열 집계**: Caffeine Cache와 AtomicLong을 활용해 로컬 메모리에서 채팅 빈도를 1차 압축하고, 3초 주기로 Redis TimeSeries에 Lua 스크립트로 일괄 저장하여 Redis 초당 쓰기 부하를 약 93% 감축하고 3,000TPS 환경에서도 Kafka Lag 0을 유지.

### 3. 모니터링 기반 Redis 데이터 누수 및 부하 장애 해결
3주 무중단 운영 중 Redis 초당 요청수(1,645 TPS)와 스레드 대기(401개)가 폭증하는 장애를 Grafana 메트릭과 실시간 경고 로그 교차 분석을 통해 5분 만에 진단했습니다.
- **상태 동기화 결함 교정**: 수집 모듈(`IngestionService`)이 종료 방송을 판별할 때 살아있는 방송 목록으로 과거 데이터를 조회하던 인자 오류를 수정하고, `ArgumentCaptor` 기반 TDD로 검증을 보강.
- **상태 덮어쓰기 정합성 보장**: Redis Lua 스크립트에 분석 인덱스 초기화(`DEL`) 처리를 추가하여, 수집 주기마다 최신 활성 방송과 1:1 동기화되도록 보장하고 초당 조회 부하를 88% 이상 감축(1,645 ➔ 200 미만).

### 4. 사용자 피드백 기반 하이라이트 추출 로직 고도화
고정 임계치나 단순 Z-Score 알고리즘에서 발생하는 오탐지와 피크 누락 문제를 사용자 피드백을 바탕으로 개선했습니다.
- **동적 체급 분류 및 갭 윈도우**: 최근 채팅량에 따라 윈도우 크기(1분/2분)를 동적으로 분리하고, 직전 10초 예열 데이터를 연산에서 배제(Gap 윈도우)하여 1차 피크가 기준선을 오염시키는 현상 해결.
- **세션 병합 및 타임라인 보정**: Caffeine Cache 기반 60초 쿨다운 세션 병합과 `-20초 ~ +5초` 버퍼링 로직을 적용해 파편화 없는 쇼츠형 타임스탬프 추출 완성.
