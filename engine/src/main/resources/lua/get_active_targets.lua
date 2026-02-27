-- KEYS[1]: ANALYSIS_INDEX (Set)
-- KEYS[2]: STREAM_LIVE_HASH (Hash)

local active_ids = redis.call('SMEMBERS', KEYS[1])

if #active_ids == 0 then
    return {}
end

-- HMGET을 통해 한 번에 모든 상세 데이터(JSON)를 가져옴
return redis.call('HMGET', KEYS[2], unpack(active_ids))
