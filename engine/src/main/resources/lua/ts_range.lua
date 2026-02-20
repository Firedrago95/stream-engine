-- KEYS[1]: time series key
-- ARGV[1]: fromTimestamp
-- ARGV[2]: toTimestamp
-- ARGV[3]: maxCount (optional, e.g., 1000)

local maxCount = ARGV[3] and tonumber(ARGV[3]) or 1000
return redis.call('TS.RANGE', KEYS[1], ARGV[1], ARGV[2], 'COUNT', maxCount)
