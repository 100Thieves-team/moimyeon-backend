local levels = {TRACE=true, DEBUG=true, INFO=true, WARN=true, ERROR=true}
local UUID = "^%x%x%x%x%x%x%x%x%-%x%x%x%x%-%x%x%x%x%-%x%x%x%x%-%x%x%x%x%x%x%x%x%x%x%x%x$"
local strings = {"timestamp", "release", "logger", "eventCode", "method", "route", "errorCode", "requestId", "traceId", "spanId", "impact"}

local function text(value, limit)
    if type(value) == "string" and #value <= limit then return value end
    return nil
end

local function growth_properties(value)
    if type(value) ~= "table" then return nil end
    local clean = {}
    local count = 0
    for key, item in pairs(value) do
        if count == 16 then break end
        if type(key) == "string" and #key <= 64 and key:match("^%a[%w_]*$") then
            local kind = type(item)
            if kind == "number" or kind == "boolean" or (kind == "string" and #item <= 128) then
                clean[key] = item
                count = count + 1
            end
        end
    end
    if count == 0 then return nil end
    return clean
end

local function clip(value, limit)
    if type(value) ~= "string" then return nil end
    if #value <= limit then return value end
    local cut = limit - 3
    while cut > 0 and value:byte(cut + 1) >= 0x80 and value:byte(cut + 1) < 0xC0 do cut = cut - 1 end
    return value:sub(1, cut) .. "…"
end

function sanitize(tag, timestamp, record)
    if not levels[record.level] then return -1, timestamp, record end
    if record.schemaVersion ~= 1 and record.eventCode ~= "logging.serialization_failed" then return -1, timestamp, record end
    local sec = timestamp.sec
    local nsec = timestamp.nsec
    if type(record.timestamp) == "string" then
        local y, m, d, h, mi, s, fraction = record.timestamp:match("^(%d%d%d%d)%-(%d%d)%-(%d%d)T(%d%d):(%d%d):(%d%d)%.?(%d*)Z$")
        if not y then return -1, timestamp, record end
        sec = os.time({year=tonumber(y), month=tonumber(m), day=tonumber(d), hour=tonumber(h), min=tonumber(mi), sec=tonumber(s), isdst=false})
        nsec = math.floor((tonumber("0." .. fraction) or 0) * 1000000000)
    elseif record.eventCode ~= "logging.serialization_failed" then
        return -1, timestamp, record
    end
    if not sec or sec > os.time() + 7200 then return -1, timestamp, record end
    if (record.level == "DEBUG" or record.level == "TRACE") and sec < os.time() - 259200 then return -1, timestamp, record end
    local out = {schemaVersion=1, level=record.level, service=os.getenv("LOG_SERVICE_NAME"), environment=os.getenv("LOG_ENVIRONMENT")}
    for _, key in ipairs(strings) do out[key] = text(record[key], 256) end
    if record.schemaVersion == 1 then out.message = clip(record.message, 16384) end
    if not out.eventCode then return -1, timestamp, record end
    out.timestamp = out.timestamp or os.date("!%Y-%m-%dT%H:%M:%SZ", sec)
    for _, key in ipairs({"status", "durationMs"}) do
        if type(record[key]) == "number" then out[key] = record[key] end
    end
    out.category = (record.category == "growth" and record.level == "INFO") and "growth" or "ops"
    if out.category == "growth" then
        if type(record.eventId) == "string" and record.eventId:match(UUID) then out.eventId = record.eventId end
        if type(record.analyticsId) == "string" and record.analyticsId:match("^[0-9a-f]+$") and #record.analyticsId == 32 then out.analyticsId = record.analyticsId end
        out.properties = growth_properties(record.properties)
    end
    if type(record.exceptions) == "table" then
        local exceptions = {}
        local remaining = 30
        for i, exception in ipairs(record.exceptions) do
            if i > 5 then break end
            if type(exception) == "table" then
                local item = {type=text(exception.type, 128), frames={}}
                if record.schemaVersion == 1 then item.message = clip(exception.message, 16384) end
                if type(exception.frames) == "table" then
                    for _, frame in ipairs(exception.frames) do
                        if remaining == 0 then break end
                        if type(frame) == "table" then
                            local clean = {class=text(frame.class, 128), method=text(frame.method, 128), file=text(frame.file, 128)}
                            if type(frame.line) == "number" then clean.line = frame.line end
                            table.insert(item.frames, clean)
                            remaining = remaining - 1
                        end
                    end
                end
                table.insert(exceptions, item)
            end
        end
        out.exceptions = exceptions
    end
    return 1, {sec=sec, nsec=nsec}, out
end

function posthog(tag, timestamp, record)
    local token = os.getenv("POSTHOG_PROJECT_TOKEN")
    if not token or token == "" or not record.analyticsId or not record.eventId then return -1, timestamp, record end
    local properties = {}
    if type(record.properties) == "table" then
        for key, item in pairs(record.properties) do properties[key] = item end
    end
    properties.distinct_id = nil
    properties.token = nil
    properties.api_key = nil
    properties.environment = record.environment
    properties.service = record.service
    properties.release = record.release
    return 1, timestamp, {api_key=token, event=record.eventCode, distinct_id=record.analyticsId, uuid=record.eventId, timestamp=record.timestamp, properties=properties}
end
