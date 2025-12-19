-- Atomically increment unread count if timestamp is greater than stored value
-- KEYS[1]: unread key (e.g., unread_12345:conv_123)
-- ARGV[1]: new timestamp (epoch milliseconds)
-- Returns: 1 if incremented, 0 if not (timestamp not greater)

local key = KEYS[1]
local newTimestamp = tonumber(ARGV[1])

local currentTimestamp = redis.call('HGET', key, 'timestamp')
currentTimestamp = currentTimestamp and tonumber(currentTimestamp) or 0

if newTimestamp > currentTimestamp then
    redis.call('HINCRBY', key, 'count', 1)
    redis.call('HSET', key, 'timestamp', newTimestamp)
    return 1
end

return 0
