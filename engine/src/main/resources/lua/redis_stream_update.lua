-- KEYS[1]: "stream:targets" (기존 방송 상태 SET)
-- KEYS[2]: "stream:live:" (방송 상세 정보 HASH)
-- KEYS[3]: "active:analysis:ids" (분석 엔진용 전용 인덱스 SET)

local closed_count = tonumber(ARGV[1])
local active_count = tonumber(ARGV[2])

local unpack = table.unpack or unpack

-- 1. 종료된 방송 처리
if closed_count > 0 then
  local closed_ids = {}
  for i = 1, closed_count do
    table.insert(closed_ids, ARGV[2 + i])
  end

  redis.call('HDEL', KEYS[2], unpack(closed_ids))
  redis.call('SREM', KEYS[3], unpack(closed_ids))
end

-- 2. 활성 방송 처리 (ID 및 상세 정보 JSON 추출)
local active_ids = {}
local hash_data = {}
local start_idx = 2 + closed_count + 1

for i = start_idx, #ARGV, 2 do
    local stream_id = ARGV[i]
    local json_str = ARGV[i + 1]
    if stream_id == nil then break end

    table.insert(active_ids, stream_id)
    table.insert(hash_data, stream_id)
    table.insert(hash_data, json_str)
end

-- 3. Redis 상태 덮어쓰기
redis.call('DEL', KEYS[1])

if #active_ids > 0 then
    redis.call('SADD', KEYS[1], unpack(active_ids))
    redis.call('SADD', KEYS[3], unpack(active_ids))
end

if #hash_data > 0 then
    redis.call('HSET', KEYS[2], unpack(hash_data))
end

return {}
