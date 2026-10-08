-- V19: target_streamers 테이블에 제외 사유, 임시 격리 만료일자 및 원본 타겟 타입/활성상태 보존 컬럼 추가
ALTER TABLE target_streamers ADD COLUMN reason VARCHAR(255);
ALTER TABLE target_streamers ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE target_streamers ADD COLUMN previous_target_type VARCHAR(20);
ALTER TABLE target_streamers ADD COLUMN previous_is_active BOOLEAN;
