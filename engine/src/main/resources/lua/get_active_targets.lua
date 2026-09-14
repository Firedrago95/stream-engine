-- KEYS[1]: ANALYSIS_INDEX (Set)
-- KEYS[2]: STREAM_LIVE_HASH (Hash)

local active_ids = redis.call('SMEMBERS', KEYS[1])

if #active_ids == 0 then
    return {}
end

local unpack = table.unpack or unpack
local BATCH_SIZE = 1000
local results = {}

for i = 1, #active_ids, BATCH_SIZE do
    local chunk = {}
    for j = i, math.min(i + BATCH_SIZE - 1, #active_ids) do
        table.insert(chunk, active_ids[j])
    end
    if #chunk > 0 then
        local chunk_res = redis.call('HMGET', KEYS[2], unpack(chunk))
        for _, val in ipairs(chunk_res) do
            table.insert(results, val)
        end
    end
end

return results
