-- KEYS[1]: ACTUAL_KEY (streams.active.id)
-- KEYS[2]: INFO_KEY (streams.info)
-- ARGV[1~N]: channelIds
-- ARGV[N+1~3N]: channelId, json pairs

local unpack = table.unpack or unpack
local actual_key = KEYS[1]
local info_key = KEYS[2]
local temp_key = actual_key .. ":temp"

-- 전체 인자의 1/3이 순수 ID 목록의 개수
local id_count = #ARGV / 3

-- 1. 임시 Set 생성 (신규 방송 ID들)
redis.call('DEL', temp_key)
local ids_for_sadd = {}
for i = 1, id_count do
    table.insert(ids_for_sadd, ARGV[i])
end
redis.call('SADD', temp_key, unpack(ids_for_sadd))

-- 2. 신규/종료 방송 비교
local new_stream_ids = redis.call('SDIFF', temp_key, actual_key)
local closed_stream_ids = redis.call('SDIFF', actual_key, temp_key)

-- 3. 현재 활성 ID 목록 교체
redis.call('RENAME', temp_key, actual_key)

-- 4. 상세 정보(Hash) 관리
-- 종료된 방송 상세 정보 삭제
if #closed_stream_ids > 0 then
    redis.call('HDEL', info_key, unpack(closed_stream_ids))
end

-- 모든 방송 상세 정보 업데이트 (HSET)
local hash_data = {}
for i = id_count + 1, #ARGV do
    table.insert(hash_data, ARGV[i])
end

if #hash_data > 0 then
    redis.call('HSET', info_key, unpack(hash_data))
end

return {new_stream_ids, closed_stream_ids}
