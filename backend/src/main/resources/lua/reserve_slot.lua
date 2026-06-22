-- KEYS[1] : 예약 카운터 키 (예: popspot:reservation:slot:1:reserved)
-- ARGV[1] : capacity (정원)
-- 반환값  : 차감 후 카운터(정상이면 capacity 이하), -1이면 정원 초과
local current = redis.call('GET', KEYS[1])
if current == false then
    return -1
end
if tonumber(current) >= tonumber(ARGV[1]) then
    return -1
end
return redis.call('INCR', KEYS[1])