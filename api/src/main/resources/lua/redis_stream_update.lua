-- ARGV[1]: count (데이터 개수, 예: 20)
-- ARGV[2 ~ count+1]: channelIds
-- ARGV[count+2 ~ END]: channelId, json pairs

local unpack = table.unpack or unpack
local actual_key = KEYS[1]
local info_key = KEYS[2]
local temp_key = actual_key .. ":temp"

local count = tonumber(ARGV[1])

-- 방어 로직: 데이터가 없으면 종료
if count == nil or count == 0 then
    return {{}, {}}
end

-- 1. 임시 Set 생성 (신규 방송 ID들)
redis.call('DEL', temp_key)
local ids_for_sadd = {}

-- 인덱스가 1칸 밀렸으므로 ARGV[2]부터 읽습니다
for i = 1, count do
    table.insert(ids_for_sadd, ARGV[i + 1])
end

if #ids_for_sadd > 0 then
    redis.call('SADD', temp_key, unpack(ids_for_sadd))
end

-- 2. 신규/종료 방송 비교
local new_stream_ids = redis.call('SDIFF', temp_key, actual_key)
local closed_stream_ids = redis.call('SDIFF', actual_key, temp_key)

-- 3. 현재 활성 ID 목록 교체
if #ids_for_sadd > 0 then
    redis.call('RENAME', temp_key, actual_key)
else
    -- 만약 이번에 업데이트할 게 하나도 없다면 기존 키 삭제 (빈 Set)
    redis.call('DEL', actual_key)
end

-- 4. 상세 정보(Hash) 관리
if #closed_stream_ids > 0 then
    redis.call('HDEL', info_key, unpack(closed_stream_ids))
end

local hash_data = {}
-- ID 목록이 끝난 지점(count + 2)부터 끝까지 읽음
for i = count + 2, #ARGV do
    table.insert(hash_data, ARGV[i])
end

if #hash_data > 0 then
    redis.call('HSET', info_key, unpack(hash_data))
end

return {new_stream_ids, closed_stream_ids}
