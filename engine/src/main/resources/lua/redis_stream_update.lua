-- KEYS[1]: "stream:targets" (기존 방송 상태 SET)
-- KEYS[2]: "stream:live:" (방송 상세 정보 HASH)
-- KEYS[3]: "active:analysis:ids" (분석 엔진용 전용 인덱스 SET)
-- ARGV[1]: closed_count (종료된 방송의 개수)
-- ARGV[2] ~ ARGV[1 + closed_count]: closed_ids (종료된 방송 ID 목록)
-- ARGV[1 + closed_count + 1] ~ : [stream_id, json_str] (활성 방송 ID와 상세 정보 JSON 쌍 목록)

local closed_count = tonumber(ARGV[1])
local unpack = table.unpack or unpack
local BATCH_SIZE = 1000

if closed_count > 0 then
  local closed_ids = {}
  for i = 1, closed_count do
    table.insert(closed_ids, ARGV[1 + i])
  end

  for i = 1, #closed_ids, BATCH_SIZE do
    local chunk = {}
    for j = i, math.min(i + BATCH_SIZE - 1, #closed_ids) do
      table.insert(chunk, closed_ids[j])
    end
    if #chunk > 0 then
      redis.call('HDEL', KEYS[2], unpack(chunk))
      redis.call('SREM', KEYS[3], unpack(chunk))
    end
  end
end

local active_ids = {}
local hash_data = {}
local start_idx = 1 + closed_count + 1

for i = start_idx, #ARGV, 2 do
    local stream_id = ARGV[i]
    local json_str = ARGV[i + 1]
    if stream_id == nil then break end

    table.insert(active_ids, stream_id)
    table.insert(hash_data, stream_id)
    table.insert(hash_data, json_str)
end

redis.call('DEL', KEYS[1])
redis.call('DEL', KEYS[2])
redis.call('DEL', KEYS[3])

if #active_ids > 0 then
    for i = 1, #active_ids, BATCH_SIZE do
        local chunk = {}
        for j = i, math.min(i + BATCH_SIZE - 1, #active_ids) do
            table.insert(chunk, active_ids[j])
        end
        if #chunk > 0 then
            redis.call('SADD', KEYS[1], unpack(chunk))
            redis.call('SADD', KEYS[3], unpack(chunk))
        end
    end
    redis.call('EXPIRE', KEYS[1], 120)
    redis.call('EXPIRE', KEYS[3], 120)
end

if #hash_data > 0 then
    for i = 1, #hash_data, BATCH_SIZE do
        local chunk = {}
        for j = i, math.min(i + BATCH_SIZE - 1, #hash_data) do
            table.insert(chunk, hash_data[j])
        end
        if #chunk > 0 then
            redis.call('HSET', KEYS[2], unpack(chunk))
        end
    end
    redis.call('EXPIRE', KEYS[2], 120)
end

return {}
