# Stream Engine (Slice Stream Engine)

## 📖 프로젝트 설명
- **Java 25 가상 스레드(Virtual Threads)** 기반의 고성능 실시간 스트림 데이터 수집 및 분석 엔진입니다.
- 라이브 스트리밍 플랫폼(Chzzk 등)의 대규모 채팅 및 스트림 데이터를 실시간으로 수집하여, 비즈니스 인사이트를 도출하고 하이라이트 구간을 자동으로 추출하는 **chzzSlice 서비스의 코어 시스템**입니다.
- 수집/분석을 담당하는 `engine`, 외부 통신 및 조회를 담당하는 `api-server`, 그리고 사용자 인터페이스인 `client`로 구성되어 있습니다.

---

## 🛠️ 기술 스택

### Backend (Core & API)
- **Language**: Java 25 (Virtual Threads 활용)
- **Framework**: Spring Boot 4.0.1
- **Data & Messaging**:
    - Spring Data Redis (Reactive & Repository)
    - Spring Kafka
- **Utils**: Spring Scheduling, Caffeine Cache, Jose4j

### Infrastructure
- **Database / Cache**: Redis 7 (Redis Stack Server), Redis TimeSeries
- **Message Broker**: Apache Kafka (Kraft Mode)
- **Container**: Docker, Docker Compose

### Testing
- JUnit 5, Mockito, AssertJ
- **TestContainers** (Redis, Kafka 통합 테스트)

---

## 🏗️ 아키텍처

시스템은 역할에 따라 **Engine**, **API Server**, **Client** 세 가지 모듈로 명확히 분리되어 있습니다.

### 모듈별 핵심 역할

1.  **`engine` (Data Processing)**
    * **역할**: 실시간 데이터 파이프라인의 핵심입니다.
    * **기능**:
        * 스트림 및 채팅 데이터 실시간 수집 (WebSocket/REST)
        * Kafka를 통한 메시지 버퍼링 및 비동기 처리
        * 데이터 집계 및 하이라이트 후보 구간 감지 (분석 로직)
        * 감지된 이벤트를 `api-server`로 Push

2.  **`api-server` (External Interface)**
    * **역할**: 클라우드 배포 및 외부 서비스 제공을 담당합니다.
    * **기능**:
        * `engine`으로부터 수신한 데이터 검증 및 저장
        * 클라이언트(`client`)에 집계 데이터 및 하이라이트 조회 API 제공
        * SSE(Server-Sent Events)를 이용한 실시간 데이터 푸시

3.  **`client` (Dashboard)**
    * **역할**: 사용자에게 분석된 데이터를 시각화하여 제공합니다.
    * **기능**:
        * 스트림 분석 결과 차트 시각화 (Recharts)
        * 실시간 채팅 화력 모니터링 대시보드

### 🔄 전체 데이터 플로우

```mermaid
graph TD
    %% Subgraph: Engine Module
    subgraph Engine_Scope ["engine (Data Processing)"]
        direction TB
        Scheduler[⏰ Scheduler] --> Ingestion{IngestionService}
        Ingestion -->|API Polling| ChzzkAPI[Chzzk API]
        Ingestion --> Redis_DB[(Redis)]
        Redis_DB -- "스트림 변경 감지" --> StreamEvent{Stream Event 발행}
        
        StreamEvent -- "이벤트 수신" --> ChatListener[ChatEventListener]
        ChatListener --> ChatMgr{ChatManager}
        ChatMgr --> WebSocket[Chzzk Chat WebSocket]
        
        WebSocket -- "채팅 메시지" --> Kafka_Topic((Kafka))
        Kafka_Topic -- "메시지 컨슘" --> AggService[ChatAggregationService]
        
        AggService -- "시계열 저장" --> RedisTS[(RedisTimeSeries)]
        
        Scheduler --> HighlightService[HighlightService]
        HighlightService -- "활성 스트림 조회" --> Redis_DB
        HighlightService -- "하이라이트 감지" --> Detector{HighlightDetector}
        Detector -- "채팅 화력 조회" --> RedisTS
        HighlightService -- "하이라이트 신호 전송" --> SignalClient[HighlightSignalClient]
    end

    %% Subgraph: API Server Module
    subgraph API_Scope ["api-server (API & Verification)"]
        direction TB
        InternalAPI{Internal API Endpoint}
        VerifyService{HighlightVerificationService}
        VOD_API[Chzzk VOD API]
        MainDB[(Database)]
        API_Endpoint{API Endpoint}
        
        SignalClient -- "HTTP PUSH" --> InternalAPI
        InternalAPI --> VerifyService
        VerifyService --> VOD_API
        VerifyService -- "검증 완료" --> MainDB
    end

    %% Subgraph: Client Module
    subgraph Client_Scope ["client (Frontend)"]
        UserClient[React Client]
    end

    %% Connections between modules
    API_Endpoint -- "데이터 조회" --> MainDB
    UserClient --> API_Endpoint

    %% Styling
    style Engine_Scope fill:#f9f9f9,stroke:#333,stroke-width:2px
    style API_Scope fill:#e6f3ff,stroke:#0056b3,stroke-width:2px
    style Client_Scope fill:#fff5f5,stroke:#d63384,stroke-width:2px
