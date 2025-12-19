-- Get user online information for multiple users
-- KEYS: user_ids (passed as arguments)
-- Returns: array of {userId, deviceId,serverLocation} pairs for all devices

local result = {}
for i = 1, #KEYS do
    local pattern = KEYS[i]
    local keys = redis.call('KEYS', pattern)

    -- Extract userId from pattern (user_presence:userId:*)
    local userId = pattern:match("user_presence:(%d+):")

    if userId then
        -- Get all devices' server locations for this user
        for j = 1, #keys do
            local serverLocation = redis.call('GET', keys[j])
            if serverLocation then
                 -- Extract deviceId from key (user_presence:userId:deviceId)
                local deviceId = keys[j]:match("user_presence:%d+:(.+)$")
                table.insert(result, userId)
                table.insert(result, deviceId)
                table.insert(result, serverLocation)
            end
        end
    end
end

return result
