local levels = {TRACE=true, DEBUG=true, INFO=true, WARN=true, ERROR=true}
local strings = {"timestamp", "release", "logger", "eventCode", "method", "route", "errorCode", "requestId", "traceId", "spanId", "impact"}

local function text(value, limit)
    if type(value) == "string" and #value <= limit then return value end
    return nil
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
    if not out.eventCode then return -1, timestamp, record end
    out.timestamp = out.timestamp or os.date("!%Y-%m-%dT%H:%M:%SZ", sec)
    for _, key in ipairs({"status", "durationMs"}) do
        if type(record[key]) == "number" then out[key] = record[key] end
    end
    out.category = (record.category == "growth" and record.level == "INFO") and "growth" or "ops"
    if type(record.exceptions) == "table" then
        local exceptions = {}
        local remaining = 30
        for i, exception in ipairs(record.exceptions) do
            if i > 5 then break end
            if type(exception) == "table" then
                local item = {type=text(exception.type, 128), frames={}}
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
