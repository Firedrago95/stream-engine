-- V19: target_streamers 테이블에 제외 사유 및 임시 격리 만료일자 컬럼 추가
ALTER TABLE target_streamers ADD COLUMN reason VARCHAR(255);
ALTER TABLE target_streamers ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;
