-- V20: 시스템 동적 설정(Dynamic Config) 테이블 및 기본 파라미터 시드 데이터 생성
CREATE TABLE system_configs (
    config_key VARCHAR(64) PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    category VARCHAR(32) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

INSERT INTO system_configs (config_key, config_value, description, category) VALUES
    ('targeting.limit', '300', '엔진 분석 대상 스트리머 목표 인원수', 'TARGETING'),
    ('targeting.min_days', '5', '타겟 정규 스트리머 인정을 위한 최소 방송 일수', 'TARGETING'),
    ('targeting.window_days', '30', '활동성 검증 윈도우 기간 (일)', 'TARGETING'),
    ('highlight.leading_buffer_seconds', '40', '하이라이트 피크 발생 전 시작 여유 버퍼 (초)', 'HIGHLIGHT'),
    ('highlight.trailing_buffer_seconds', '10', '하이라이트 피크 발생 후 유지 버퍼 (초)', 'HIGHLIGHT'),
    ('highlight.cooldown_seconds', '60', '연쇄 한타 병합 쿨다운 (초)', 'HIGHLIGHT'),
    ('highlight.minimum_firepower', '5', '하이라이트로 인정될 최소 화력 최저선', 'HIGHLIGHT'),
    ('session.re_live_gap_minutes', '6', '리방(Re-Live) 인정 최대 공백 시간 (분)', 'SESSION'),
    ('session.noise_threshold_minutes', '5', '유효 세션 최소 지속 시간 (미만 노이즈 필터링, 분)', 'SESSION');
