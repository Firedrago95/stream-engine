-- KEYS[1]: time series key
-- ARGV[1]: fromTimestamp
-- ARGV[2]: toTimestamp

-- TS.RANGE를 호출하고 결과를 그대로 반환한다.
return redis.call('TS.RANGE', KEYS[1], ARGV[1], ARGV[2])
