# stream-engine
## 프로젝트 설명
- Java 25 가상 스레드(Virtual Threads) 기반의 실시간 스트림 데이터 수집 및 집계/분석 엔진
- 라이브 스트리밍 플랫폼(Chzzk 등)의 방대한 데이터를 실시간으로 수집하여 <br>
  비즈니스 분석과 하이라이트를 추출하는 chzzSlice 서비스의 코어 엔진입니다.


## 기술 스택
### Core
- Java 25 (Virtual Threads)
- Spring Boot 4.0.1
- Spring Data Redis
- Spring Scheduling
- Spring Kafka
### Infrastructure
- Redis 7 (Lua Script)
- Lettuce (Redis Client)
- RestClient (HTTP Client)
- Kafka
### Testing
- JUnit 5
- Mockito
- TestContainers (Redis, Kafka)
- AssertJ

## 🚀 실행 방법

프로젝트 루트 디렉토리에서 아래 명령어를 실행하여 애플리케이션을 시작합니다.

```bash
./gradlew bootRun
```

## 🏗️ 아키텍처


두 개의 독립적인 모듈(`engine`, `api-server`)로 구성되어 역할과 책임을 명확히 분리합니다.

### 모듈별 역할

*   **`engine`**:
    *   **데이터 수집 및 분석**: 실시간으로 스트림 및 채팅 데이터를 수집하고, 이를 집계/분석하여 하이라이트 후보를 감지하는 핵심 로직을 수행합니다.
    *   **내부 실행**: 로컬 환경 또는 내부 서버에서 실행됩니다.
    *   **PUSH**: 하이라이트 후보를 감지하면 `api-server`로 HTTP 요청을 보내 데이터를 PUSH합니다.

*   **`api-server`**:
    *   **외부 인터페이스**: `engine`으로부터 하이라이트 후보를 수신하고, 외부 API(e.g., 치지직 다시보기 API)를 통해 검증 후 최종 하이라이트를 저장/관리합니다.
    *   **외부 배포**: 외부에 배포되어 클라이언트(웹/앱)의 요청을 처리합니다.
    *   **조회 API 제공**: 클라이언트가 하이라이트 목록, 실시간 채팅 화력 등의 데이터를 조회할 수 있는 API를 제공합니다.

### 전체 플로우
```mermaid
graph TD
    subgraph engine (Data Processing)
        A[⏰ Scheduler] --> B{IngestionService}
        B --> C[Chzzk API]
        B --> D[(Redis)]
        D -- "스트림 변경 감지" --> E{Stream Event 발행}
        E -- "이벤트 수신" --> F[ChatEventListener]
        F --> G{ChatManager}
        G --> H[Chzzk Chat WebSocket]
        H -- "채팅 메시지" --> I((Kafka))
        I -- "메시지 전달" --> J[ChatAggregationService]
        J -- "주기적 저장" --> K[(RedisTimeSeries)]
        J -- "하이라이트 후보 감지" --> L{HighlightDetector}
    end

    subgraph api-server (API & Verification)
        M[Client] --> N{API Endpoint}
        N -- "데이터 조회" --> O[(Database)]
        L -- "HTTP PUSH" --> P{Internal API Endpoint}
        P -- "후보 수신" --> Q{HighlightVerificationService}
        Q --> R[Chzzk VOD API]
        Q -- "검증 완료" --> O
    end

    %% Styles
    style engine fill:#f9f9f9,stroke:#333,stroke-width:2px
    style api-server fill:#e6f3ff,stroke:#333,stroke-width:2px
```

### Clean Architecture 기반 모듈 구조

#### `engine`
```text
engine/
├── core/             # 공통 도메인 모델
│
├── ingestion/       # 라이브 스트림 수집
│ ├── application/
│ └── ...
│
├── chat/            # 💬 실시간 채팅 데이터 수집
│ └── ...
│
├── analyzer/        # 📊 실시간 채팅 집계 및 분석
│ ├── application/
│ │ └── ChatAggregationService
│ ├── domain/
│ │ ├── ChatRoomAggregation
│ │ └── ChatRoomAggregationRepository
│ └── infrastructure/
│   └── RedisChatRoomAggregationRepository
│
├── highlight/       # ⭐ 하이라이트 추출 (🚧 예정)
│ └── ...
│
└── global/          # 전역 설정
```

#### `api-server`
```text
api-server/
├── highlight/       # ⭐ 최종 하이라이트 관리
│ ├── application/
│ │ └── HighlightService
│ ├── domain/
│ │ └── Highlight
│ ├── infrastructure/
│ │ └── ...
│ └── presentation/
│   └── HighlightController
│
├── aggregation/     # 📊 집계 데이터 조회
│ ├── application/
│ │ └── ChatAggregationQueryService
│ ├── domain/
│ ├── infrastructure/
│ └── presentation/
│   └── ChatAggregationController
│
└── global/          # 전역 설정
```


