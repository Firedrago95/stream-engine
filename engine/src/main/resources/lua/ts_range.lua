-- KEYS[1]: time series key
-- ARGV[1]: fromTimestamp
-- ARGV[2]: toTimestamp

-- 1. 누적값들을 다 가져온다.
local raw_data = redis.call('TS.RANGE', KEYS[1], ARGV[1], ARGV[2])

-- 데이터가 2개 미만이면 변화량을 구할 수 없으므로 빈 리스트 반환
if #raw_data < 2 then
    return {}
end

local deltas = {}

-- 2. 반복문을 돌면서 '현재 값 - 이전 값'을 계산한다.
-- Lua의 배열 인덱스는 1부터 시작
for i = 2, #raw_data do
    local prev_item = raw_data[i-1]
    local curr_item = raw_data[i]

    -- item[1]은 타임스탬프, item[2]는 값(Value)
    -- RedisTimeSeries 값은 보통 문자열이나 숫자로 오므로 tonumber로 변환 필요
    local prev_val = tonumber(prev_item[2])
    local curr_val = tonumber(curr_item[2])
    
    -- 변화량(Delta) 계산
    local diff = curr_val - prev_val

    -- 음수 방지 (혹시 모를 서버 재시작 등으로 카운터가 0이 된 경우 등 방어 로직)
    if diff < 0 then diff = 0 end

    -- 3. 결과 리스트에 {타임스탬프, 변화량} 담는다.
    -- Java에서 String으로 파싱하므로 tostring() 처리 해주는 것이 안전함.
    table.insert(deltas, {curr_item[1], tostring(diff)})
end

return deltas
