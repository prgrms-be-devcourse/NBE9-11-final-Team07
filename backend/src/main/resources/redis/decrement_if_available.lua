-- KEYS[1]: remaining key
-- return: 차감 후 값(>=0) 성공, 또는 -1 실패(품절/미초기화, 차감하지 않음)
local remaining = redis.call('GET', KEYS[1])
if remaining == false then
  return -1
end
if tonumber(remaining) <= 0 then
  return -1
end
return redis.call('DECR', KEYS[1])
