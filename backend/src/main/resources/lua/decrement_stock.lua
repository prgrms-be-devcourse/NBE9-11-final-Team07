-- KEYS[1] : 재고 키 (예: stock:slot:1)
-- 반환값  : 차감 후 남은 재고 (정상 차감 시 0 이상), 재고 부족 시 -1
local current = redis.call('GET', KEYS[1])

-- 키가 없으면 차감 불가 (-1)
if current == false then
    return -1
end

if tonumber(current) > 0 then
    return redis.call('DECR', KEYS[1])
else
    return -1
end