-- KEYS[1]: time series key
-- ARGV[1]: fromTimestamp
-- ARGV[2]: toTimestamp
-- ARGV[3]: maxCount (optional, e.g., 1000)

-- 1. 키가 존재하는지 먼저 확인
if redis.call('EXISTS', KEYS[1]) == 0 then
    return {}
end

local maxCount = ARGV[3] and tonumber(ARGV[3]) or 1000
return redis.call('TS.RANGE', KEYS[1], ARGV[1], ARGV[2], 'COUNT', maxCount)
