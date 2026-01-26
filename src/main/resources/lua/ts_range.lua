-- KEYS[1]: time series 의 키
-- ARGV[1]: 시작 타임스탬프 (e.g.'1672531200000')
-- ARGV[2]: 종료 타임스탬프 (e.g.'1672617600000')

return redis.call('TS.RANGE', KEYS[1], ARGV[1], ARGV[2])
