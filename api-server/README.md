# Slice Stream Engine API Server

이 모듈은 `slice-stream-engine` 프로젝트의 **클라우드 배포용 API 서버** 역할을 수행합니다.
주요 목적은 클라이언트(`client` 모듈 또는 외부 서비스)에게 **채팅 분석 결과 및 하이라이트 데이터를 제공**하는 것입니다.

실제 채팅 데이터 수집 및 복잡한 분석 로직은 `engine` 모듈(로컬 실행)에서 처리하며,
이 `api-server`는 `engine`으로부터 분석 결과를 수신하여 저장하고, 이를 외부에 노출하는 최소한의 책임만을 가집니다.

## 주요 기능

*   **분석 결과 수신**: `engine` 모듈로부터 전송된 채팅 분석 결과를 수신하여 데이터베이스에 저장합니다.
*   **채팅 분석 결과 조회**: 특정 스트림 ID에 대한 시간별 채팅량 데이터를 제공합니다.
*   **하이라이트 데이터 조회**: (향후 구현 예정) 특정 스트림 ID에 대한 하이라이트 구간 정보를 제공합니다.
*   **실시간 업데이트**: Server-Sent Events (SSE)를 통해 클라이언트에게 실시간으로 업데이트되는 분석 데이터를 푸시합니다.

## API Endpoints (예시)

*   `GET /api/v1/analysis/{streamId}`: 특정 스트림 ID의 채팅 분석 결과 조회
*   `GET /api/v1/analysis/{streamId}/sse`: 특정 스트림 ID의 실시간 채팅 분석 결과를 SSE로 구독
*   `POST /api/v1/highlights`: (향후 `engine` 모듈로부터) 하이라이트 데이터 수신
*   `GET /api/v1/highlights/{streamId}`: (향후) 특정 스트림 ID의 하이라이트 목록 조회

## 개발 환경 및 배포

이 모듈은 독립적으로 빌드 및 배포될 수 있도록 설계되었습니다.
Docker를 통해 컨테이너화하여 EC2와 같은 클라우드 환경에 배포하는 것을 목표로 합니다.

## 설정

`application.yml` 또는 환경 변수를 통해 Redis 연결 정보, Kafka (필요시) 등의 설정을 관리합니다.

---

**참고:**
이 `README.md`는 `api-server` 모듈을 위한 문서입니다. 전체 프로젝트에 대한 내용은 최상위 `README.md`를 참조하십시오.
