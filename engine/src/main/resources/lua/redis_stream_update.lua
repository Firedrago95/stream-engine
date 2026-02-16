-- KEYS[1]: "stream:targets" (기존 방송 상태 SET)
-- KEYS[2]: "stream:live:" (방송 상세 정보 HASH)
-- KEYS[3]: "active:analysis:ids" (분석 엔진용 전용 인덱스 SET)

local unpack = table.unpack or unpack
local actual_key = KEYS[1]
local info_key = KEYS[2]
local analysis_index_key = KEYS[3]
local temp_key = actual_key .. ":temp"

local count = tonumber(ARGV[1])

-- [방어 로직] 데이터가 없으면 관련 모든 키 정리
if count == nil or count == 0 then
    redis.call('DEL', actual_key, info_key, analysis_index_key)
    return { {}, {} }
end

-- 1. 임시 Set 생성 (신규 데이터 기반)
redis.call('DEL', temp_key)
local ids_for_sadd = {}
for i = 1, count do
    table.insert(ids_for_sadd, ARGV[i + 1])
end

if #ids_for_sadd > 0 then
    redis.call('SADD', temp_key, unpack(ids_for_sadd))
end

-- 2. 신규/종료 방송 비교
local new_channel_ids = redis.call('SDIFF', temp_key, actual_key)
local closed_channel_ids = redis.call('SDIFF', actual_key, temp_key)

-- 3. 현재 활성 ID 목록 교체 (Atomic Rename)
redis.call('RENAME', temp_key, actual_key)

-- 4. 분석용 인덱스(KEYS[3]) 동기화
if #closed_channel_ids > 0 then
    -- 종료된 방송은 상세 정보와 분석 인덱스에서 모두 제거
    redis.call('HDEL', info_key, unpack(closed_channel_ids))
    redis.call('SREM', analysis_index_key, unpack(closed_channel_ids)) -- [수정됨: reids -> redis]
end

if #ids_for_sadd > 0 then
    -- 현재 활성화된 모든 ID를 분석 인덱스에 등록 (Upsert 효과)
    redis.call('SADD', analysis_index_key, unpack(ids_for_sadd))
end

-- 5. 상세 정보(Hash) 업데이트
local hash_data = {}
for i = count + 2, #ARGV do
    table.insert(hash_data, ARGV[i])
end

if #hash_data > 0 then
    redis.call('HSET', info_key, unpack(hash_data)) -- [수정됨: upack -> unpack]
end

-- 6. 신규 타겟 정보 반환
local new_stream_targets_json = {}
if #new_channel_ids > 0 then
    new_stream_targets_json = redis.call('HMGET', info_key, unpack(new_channel_ids))
end

return { new_stream_targets_json, closed_channel_ids }
