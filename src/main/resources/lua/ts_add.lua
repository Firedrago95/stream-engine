-- KEYS[1]: Redis Key (e.g., "chat:analysis:room1")
-- ARGV[1]: Timestamp (e.g., "1706100000000" or "*")
-- ARGV[2]: Value (e.g., "42")
-- ARGV[3]: Retention (milliseconds)

return redis.call('TS.ADD', KEYS[1], ARGV[1], ARGV[2], 'RETENTION', ARGV[3])
