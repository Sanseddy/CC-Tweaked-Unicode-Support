-- SPDX-FileCopyrightText: 2017 Daniel Ratcliffe
--
-- SPDX-License-Identifier: LicenseRef-CCPL

-- Load in expect from the module path.
--
-- Ideally we'd use require, but that is part of the shell, and so is not
-- available to the BIOS or any APIs. All APIs load this using dofile, but that
-- has not been defined at this point.
local expect

do
    local h = fs.open("rom/modules/main/cc/expect.lua", "r")
    local f, err = loadstring(h.readAll(), "@/rom/modules/main/cc/expect.lua")
    h.close()

    if not f then error(err) end
    expect = f().expect
end

-- [cc_tweaked_unicode_support] Character-aware string handling.
--
-- Lua strings are bytes, and with Unicode turned on a single character may be spelled with up to four of
-- them. The terminal decodes each well-formed character into one display cell, and editing operations must
-- use the same character boundaries. These helpers mirror the mod's Java-side decoder byte for byte: valid
-- UTF-8 is one character, while anything else remains one character per byte.
--
-- Exposed as a global so programs can use it too. `unicode.len(s)` and `string.len(s)` count decoded
-- characters. Lua's built-in `#s` operator still counts bytes, which is important for binary strings.
-- Raw byte measurements remain available explicitly as `unicode.byte_len(s)`.
do
    local string_byte, string_char, string_sub, byte_len = string.byte, string.char, string.sub, string.len
    local floor, concat, select = math.floor, table.concat, select

    -- How many bytes the character starting at byte `i` occupies. Never less than one, so scanning a
    -- malformed string still terminates.
    local function charlen(s, i)
        local b = string_byte(s, i)
        if not b or b < 0xC2 then return 1 end

        local n
        if b < 0xE0 then n = 2
        elseif b < 0xF0 then n = 3
        elseif b < 0xF5 then n = 4
        else return 1 end

        if i + n - 1 > byte_len(s) then return 1 end

        -- Strip the length marker off the lead byte, then fold in the continuation bytes.
        local cp = b % 2 ^ (7 - n)
        for k = i + 1, i + n - 1 do
            local c = string_byte(s, k)
            if c < 0x80 or c > 0xBF then return 1 end
            cp = cp * 64 + c - 0x80
        end

        -- Reject overlong spellings and surrogate halves: those are not characters, so their bytes stand
        -- for themselves instead.
        if n == 3 and (cp < 0x800 or cp >= 0xD800 and cp <= 0xDFFF) then return 1 end
        if n == 4 and (cp < 0x10000 or cp > 0x10FFFF) then return 1 end
        return n
    end

    local function len(s)
        local n, i, size = 0, 1, byte_len(s)
        while i <= size do
            i = i + charlen(s, i)
            n = n + 1
        end
        return n
    end

    -- Byte index at which character `n` starts. `n` may be one past the end, which gives `#s + 1`.
    local function offset(s, n)
        if n < 1 then return nil end
        local i, size = 1, byte_len(s)
        while n > 1 and i <= size do
            i = i + charlen(s, i)
            n = n - 1
        end
        return n == 1 and i or nil
    end

    local function sub(s, i, j)
        local size = len(s)
        i, j = i or 1, j or -1
        if i < 0 then i = size + i + 1 end
        if j < 0 then j = size + j + 1 end
        if i < 1 then i = 1 end
        if j > size then j = size end
        if i > j then return "" end
        return string_sub(s, offset(s, i), (offset(s, j + 1) or byte_len(s) + 1) - 1)
    end

    local function char(...)
        local out = {}
        for k = 1, select("#", ...) do
            local cp = select(k, ...)
            if type(cp) ~= "number" or cp ~= floor(cp) or cp < 0 or cp > 0x10FFFF
                or cp >= 0xD800 and cp <= 0xDFFF or cp >= 0xFDD0 and cp <= 0xFDEF then
                error("bad argument #" .. k .. " to 'char' (value out of range)", 2)
            end
            if cp < 0x80 then
                out[k] = string_char(cp)
            elseif cp < 0x800 then
                out[k] = string_char(0xC0 + floor(cp / 64), 0x80 + cp % 64)
            elseif cp < 0x10000 then
                out[k] = string_char(0xE0 + floor(cp / 4096), 0x80 + floor(cp / 64) % 64, 0x80 + cp % 64)
            else
                out[k] = string_char(
                    0xF0 + floor(cp / 262144), 0x80 + floor(cp / 4096) % 64,
                    0x80 + floor(cp / 64) % 64, 0x80 + cp % 64
                )
            end
        end
        return concat(out)
    end

    -- The character at byte index `i`, and how many bytes it took. A byte that is not part of a UTF-8
    -- sequence reports itself, which is the same reading the terminal gives it.
    local function codepoint(s, i)
        i = i or 1
        local n = charlen(s, i)
        local cp = string_byte(s, i)
        if not cp then return nil end
        if n == 1 then return cp, 1 end

        cp = cp % 2 ^ (7 - n)
        for k = i + 1, i + n - 1 do cp = cp * 64 + string_byte(s, k) - 0x80 end
        return cp, n
    end

    local function codes(s)
        local i, size = 1, byte_len(s)
        return function()
            if i > size then return nil end
            local at = i
            local cp, n = codepoint(s, i)
            i = i + n
            return at, cp
        end
    end

    -- How many bytes the character ending at byte `pos` occupies. Needed for a cursor moving left or a
    -- backspace, which have to step over a whole character rather than one byte of one.
    local function charback(s, pos)
        if pos <= 0 then return 0 end
        local start = pos
        while start > 1 and pos - start < 3 do
            local b = string_byte(s, start)
            if b < 0x80 or b > 0xBF then break end
            start = start - 1
        end

        local n = pos - start + 1
        return charlen(s, start) == n and n or 1
    end

    -- Snap a byte offset to a boundary of the UTF-8 character containing it. Mouse/editor cursors use the
    -- start; a horizontally scrolled viewport uses the end, so its first visible cell is never a continuation.
    -- Malformed sequences are left untouched, as their bytes are independent terminal characters.
    local function align(s, offset, forward)
        local size = byte_len(s)
        if offset <= 0 then return 0 end
        if offset >= size then return size end

        local next_byte = string_byte(s, offset + 1)
        if next_byte < 0x80 or next_byte > 0xBF then return offset end

        local lead = offset
        while lead > 1 do
            local b = string_byte(s, lead)
            if b < 0x80 or b > 0xBF then break end
            lead = lead - 1
        end

        local n = charlen(s, lead)
        if n <= offset - lead + 1 then return offset end
        return forward and lead + n - 1 or lead - 1
    end

    _G.unicode = {
        charlen = charlen,
        charback = charback,
        align = align,
        len = len,
        offset = offset,
        sub = sub,
        char = char,
        codepoint = codepoint,
        codes = codes,
        byte_len = byte_len,
        byte_sub = string_sub,
    }

    -- Make both `string.len(label)` and the method form `label:len()` Unicode-aware.
    -- Byte-oriented code can explicitly use unicode.byte_len/byte_sub without changing the stored bytes.
    string.len = len
end

local text_len, text_sub = unicode.len, unicode.sub

-- Inject a stub for the old bit library
_G.bit = {
    bnot = bit32.bnot,
    band = bit32.band,
    bor = bit32.bor,
    bxor = bit32.bxor,
    brshift = bit32.arshift,
    blshift = bit32.lshift,
    blogic_rshift = bit32.rshift,
}

-- Install lua parts of the os api
function os.version()
    return "CraftOS 1.9"
end

function os.pullEventRaw(sFilter)
    return coroutine.yield(sFilter)
end

function os.pullEvent(sFilter)
    local eventData = table.pack(os.pullEventRaw(sFilter))
    if eventData[1] == "terminate" then
        error("Terminated", 0)
    end
    return table.unpack(eventData, 1, eventData.n)
end

-- Install globals
function sleep(nTime)
    expect(1, nTime, "number", "nil")
    local timer = os.startTimer(nTime or 0)
    repeat
        local _, param = os.pullEvent("timer")
    until param == timer
end

function write(sText)
    expect(1, sText, "string", "number")

    local w, h = term.getSize()
    local x, y = term.getCursorPos()

    local nLinesPrinted = 0
    local function newLine()
        if y + 1 <= h then
            term.setCursorPos(1, y + 1)
        else
            term.setCursorPos(1, h)
            term.scroll(1)
        end
        x, y = term.getCursorPos()
        nLinesPrinted = nLinesPrinted + 1
    end

    -- Print the line with proper word wrapping
    sText = tostring(sText)
    while #sText > 0 do
        local whitespace = string.match(sText, "^[ \t]+")
        if whitespace then
            -- Print whitespace
            term.write(whitespace)
            x, y = term.getCursorPos()
            sText = string.sub(sText, unicode.byte_len(whitespace) + 1)
        end

        local newline = string.match(sText, "^\n")
        if newline then
            -- Print newlines
            newLine()
            sText = string.sub(sText, 2)
        end

        local text = string.match(sText, "^[^ \t\n]+")
        if text then
            sText = string.sub(sText, unicode.byte_len(text) + 1)

            local textLength = text_len(text)
            if textLength > w then
                -- Print a multiline word
                while text_len(text) > 0 do
                    if x > w then
                        newLine()
                    end
                    term.write(text)
                    text = text_sub(text, w - x + 2)
                    x, y = term.getCursorPos()
                end
            else
                -- Print a word normally
                if x + textLength - 1 > w then
                    newLine()
                end
                term.write(text)
                x, y = term.getCursorPos()
            end
        end
    end

    return nLinesPrinted
end


function print(...)
    local nLinesPrinted = 0
    local nLimit = select("#", ...)
    for n = 1, nLimit do
        local s = tostring(select(n, ...))
        if n < nLimit then
            s = s .. "\t"
        end
        nLinesPrinted = nLinesPrinted + write(s)
    end
    nLinesPrinted = nLinesPrinted + write("\n")
    return nLinesPrinted
end

function printError(...)
    local oldColour
    if term.isColour() then
        oldColour = term.getTextColour()
        term.setTextColour(colors.red)
    end
    print(...)
    if term.isColour() then
        term.setTextColour(oldColour)
    end
end

function read(_sReplaceChar, _tHistory, _fnComplete, _sDefault)
    expect(1, _sReplaceChar, "string", "nil")
    expect(2, _tHistory, "table", "nil")
    expect(3, _fnComplete, "function", "nil")
    expect(4, _sDefault, "string", "nil")

    term.setCursorBlink(true)

    local sLine
    if type(_sDefault) == "string" then
        sLine = _sDefault
    else
        sLine = ""
    end
    local nHistoryPos
    local nPos, nScroll = text_len(sLine), 0
    if _sReplaceChar then
        _sReplaceChar = text_sub(_sReplaceChar, 1, 1)
    end

    local tCompletions
    local nCompletion
    local function recomplete()
        if _fnComplete and nPos == text_len(sLine) then
            tCompletions = _fnComplete(sLine)
            if tCompletions and #tCompletions > 0 then
                nCompletion = 1
            else
                nCompletion = nil
            end
        else
            tCompletions = nil
            nCompletion = nil
        end
    end

    local function uncomplete()
        tCompletions = nil
        nCompletion = nil
    end

    local w = term.getSize()
    local sx = term.getCursorPos()

    local function redraw(_bClear)
        local cursor_pos = nPos - nScroll
        if sx + cursor_pos >= w then
            -- We've moved beyond the RHS, ensure we're on the edge.
            nScroll = sx + nPos - w
        elseif cursor_pos < 0 then
            -- We've moved beyond the LHS, ensure we're on the edge.
            nScroll = nPos
        end

        local _, cy = term.getCursorPos()
        term.setCursorPos(sx, cy)
        local sReplace = _bClear and " " or _sReplaceChar
        if sReplace then
            term.write(string.rep(sReplace, math.max(text_len(sLine) - nScroll, 0)))
        else
            term.write(text_sub(sLine, nScroll + 1))
        end

        if nCompletion and tCompletions and tCompletions[nCompletion] then
            local sCompletion = tCompletions[nCompletion]
            local oldText, oldBg
            if not _bClear then
                oldText = term.getTextColor()
                oldBg = term.getBackgroundColor()
                term.setTextColor(colors.white)
                term.setBackgroundColor(colors.gray)
            end
            if sReplace then
                term.write(string.rep(sReplace, text_len(sCompletion)))
            else
                term.write(sCompletion)
            end
            if not _bClear then
                term.setTextColor(oldText)
                term.setBackgroundColor(oldBg)
            end
        end

        term.setCursorPos(sx + nPos - nScroll, cy)
    end

    local function clear()
        redraw(true)
    end

    recomplete()
    redraw()

    local function acceptCompletion()
        if nCompletion then
            -- Clear
            clear()

            -- Find the common prefix of all the other suggestions which start with the same letter as the current one
            local sCompletion = tCompletions[nCompletion]
            sLine = sLine .. sCompletion
            nPos = text_len(sLine)

            -- Redraw
            recomplete()
            redraw()
        end
    end
    while true do
        local sEvent, param, param1, param2 = os.pullEvent()
        if sEvent == "char" then
            -- Typed key
            clear()
            sLine = text_sub(sLine, 1, nPos) .. param .. text_sub(sLine, nPos + 1)
            nPos = nPos + 1
            recomplete()
            redraw()

        elseif sEvent == "paste" then
            -- Pasted text
            clear()
            sLine = text_sub(sLine, 1, nPos) .. param .. text_sub(sLine, nPos + 1)
            nPos = nPos + text_len(param)
            recomplete()
            redraw()

        elseif sEvent == "key" then
            if param == keys.enter or param == keys.numPadEnter then
                -- Enter/Numpad Enter
                if nCompletion then
                    clear()
                    uncomplete()
                    redraw()
                end
                break

            elseif param == keys.left then
                -- Left
                if nPos > 0 then
                    clear()
                    nPos = nPos - 1
                    recomplete()
                    redraw()
                end

            elseif param == keys.right then
                -- Right
                if nPos < text_len(sLine) then
                    -- Move right
                    clear()
                    nPos = nPos + 1
                    recomplete()
                    redraw()
                else
                    -- Accept autocomplete
                    acceptCompletion()
                end

            elseif param == keys.up or param == keys.down then
                -- Up or down
                if nCompletion and tCompletions and #tCompletions > 0 then
                    -- Cycle completions
                    clear()
                    if param == keys.up then
                        nCompletion = nCompletion - 1
                        if nCompletion < 1 then
                            nCompletion = #tCompletions
                        end
                    elseif param == keys.down then
                        nCompletion = nCompletion + 1
                        if nCompletion > #tCompletions then
                            nCompletion = 1
                        end
                    end
                    redraw()

                elseif _tHistory then
                    -- Cycle history
                    clear()
                    if param == keys.up then
                        -- Up
                        if nHistoryPos == nil then
                            if #_tHistory > 0 then
                                nHistoryPos = #_tHistory
                            end
                        elseif nHistoryPos > 1 then
                            nHistoryPos = nHistoryPos - 1
                        end
                    else
                        -- Down
                        if nHistoryPos == #_tHistory then
                            nHistoryPos = nil
                        elseif nHistoryPos ~= nil then
                            nHistoryPos = nHistoryPos + 1
                        end
                    end
                    if nHistoryPos then
                        sLine = _tHistory[nHistoryPos]
                        nPos, nScroll = text_len(sLine), 0
                    else
                        sLine = ""
                        nPos, nScroll = 0, 0
                    end
                    uncomplete()
                    redraw()

                end

            elseif param == keys.backspace then
                -- Backspace
                if nPos > 0 then
                    clear()
                    sLine = text_sub(sLine, 1, nPos - 1) .. text_sub(sLine, nPos + 1)
                    nPos = nPos - 1
                    if nScroll > 0 then nScroll = nScroll - 1 end
                    recomplete()
                    redraw()
                end

            elseif param == keys.home then
                -- Home
                if nPos > 0 then
                    clear()
                    nPos = 0
                    recomplete()
                    redraw()
                end

            elseif param == keys.delete then
                -- Delete
                if nPos < text_len(sLine) then
                    clear()
                    sLine = text_sub(sLine, 1, nPos) .. text_sub(sLine, nPos + 2)
                    recomplete()
                    redraw()
                end

            elseif param == keys["end"] then
                -- End
                if nPos < text_len(sLine) then
                    clear()
                    nPos = text_len(sLine)
                    recomplete()
                    redraw()
                end

            elseif param == keys.tab then
                -- Tab (accept autocomplete)
                acceptCompletion()

            end

        elseif sEvent == "mouse_click" or sEvent == "mouse_drag" and param == 1 then
            local _, cy = term.getCursorPos()
            if param1 >= sx and param1 <= w and param2 == cy then
                -- Ensure we don't scroll beyond the current line
                nPos = math.min(math.max(nScroll + param1 - sx, 0), text_len(sLine))
                redraw()
            end

        elseif sEvent == "term_resize" then
            -- Terminal resized
            w = term.getSize()
            redraw()

        end
    end

    local _, cy = term.getCursorPos()
    term.setCursorBlink(false)
    term.setCursorPos(w + 1, cy)
    print()

    return sLine
end

function loadfile(filename, mode, env)
    -- Support the previous `loadfile(filename, env)` form instead.
    if type(mode) == "table" and env == nil then
        mode, env = nil, mode
    end

    expect(1, filename, "string")
    expect(2, mode, "string", "nil")
    expect(3, env, "table", "nil")

    local file = fs.open(filename, "r")
    if not file then return nil, "File not found" end

    local func, err = load(file.readAll(), "@/" .. fs.combine(filename), mode, env)
    file.close()
    return func, err
end

function dofile(_sFile)
    expect(1, _sFile, "string")

    local fnFile, e = loadfile(_sFile, nil, _G)
    if fnFile then
        return fnFile()
    else
        error(e, 2)
    end
end

-- Install the rest of the OS api
function os.run(_tEnv, _sPath, ...)
    expect(1, _tEnv, "table")
    expect(2, _sPath, "string")

    local tEnv = _tEnv
    setmetatable(tEnv, { __index = _G })

    if settings.get("bios.strict_globals", false) then
        -- load will attempt to set _ENV on this environment, which
        -- throws an error with this protection enabled. Thus we set it here first.
        tEnv._ENV = tEnv
        getmetatable(tEnv).__newindex = function(_, name)
          error("Attempt to create global " .. tostring(name), 2)
        end
    end

    local fnFile, err = loadfile(_sPath, nil, tEnv)
    if fnFile then
        local ok, err = pcall(fnFile, ...)
        if not ok then
            if err and err ~= "" then
                printError(err)
            end
            return false
        end
        return true
    end
    if err and err ~= "" then
        printError(err)
    end
    return false
end

local tAPIsLoading = {}
function os.loadAPI(_sPath)
    expect(1, _sPath, "string")
    local sName = fs.getName(_sPath)
    if sName:sub(-4) == ".lua" then
        sName = sName:sub(1, -5)
    end
    if tAPIsLoading[sName] == true then
        printError("API " .. sName .. " is already being loaded")
        return false
    end
    tAPIsLoading[sName] = true

    local tEnv = {}
    setmetatable(tEnv, { __index = _G })
    local fnAPI, err = loadfile(_sPath, nil, tEnv)
    if fnAPI then
        local ok, err = pcall(fnAPI)
        if not ok then
            tAPIsLoading[sName] = nil
            return error("Failed to load API " .. sName .. " due to " .. err, 1)
        end
    else
        tAPIsLoading[sName] = nil
        return error("Failed to load API " .. sName .. " due to " .. err, 1)
    end

    local tAPI = {}
    for k, v in pairs(tEnv) do
        if k ~= "_ENV" then
            tAPI[k] =  v
        end
    end

    _G[sName] = tAPI
    tAPIsLoading[sName] = nil
    return true
end

function os.unloadAPI(_sName)
    expect(1, _sName, "string")
    if _sName ~= "_G" and type(_G[_sName]) == "table" then
        _G[_sName] = nil
    end
end

function os.sleep(nTime)
    sleep(nTime)
end

local nativeShutdown = os.shutdown
function os.shutdown()
    nativeShutdown()
    while true do
        coroutine.yield()
    end
end

local nativeReboot = os.reboot
function os.reboot()
    nativeReboot()
    while true do
        coroutine.yield()
    end
end

local bAPIError = false

local function load_apis(dir)
    if not fs.isDir(dir) then return end

    for _, file in ipairs(fs.list(dir)) do
        if file:sub(1, 1) ~= "." then
            local path = fs.combine(dir, file)
            if not fs.isDir(path) then
                if not os.loadAPI(path) then
                    bAPIError = true
                end
            end
        end
    end
end

-- Load APIs
load_apis("rom/apis")
if http then load_apis("rom/apis/http") end
if turtle then load_apis("rom/apis/turtle") end
if pocket then load_apis("rom/apis/pocket") end

if commands and fs.isDir("rom/apis/command") then
    -- Load command APIs
    if os.loadAPI("rom/apis/command/commands.lua") then
        -- Add a special case-insensitive metatable to the commands api
        local tCaseInsensitiveMetatable = {
            __index = function(table, key)
                local value = rawget(table, key)
                if value ~= nil then
                    return value
                end
                if type(key) == "string" then
                    local value = rawget(table, string.lower(key))
                    if value ~= nil then
                        return value
                    end
                end
                return nil
            end,
        }
        setmetatable(commands, tCaseInsensitiveMetatable)
        setmetatable(commands.async, tCaseInsensitiveMetatable)

        -- Add global "exec" function
        exec = commands.exec
    else
        bAPIError = true
    end
end

if bAPIError then
    print("Press any key to continue")
    os.pullEvent("key")
    term.clear()
    term.setCursorPos(1, 1)
end

-- Set default settings
settings.define("shell.allow_startup", {
    default = true,
    description = "Run startup files when the computer turns on.",
    type = "boolean",
})
settings.define("shell.allow_disk_startup", {
    default = commands == nil,
    description = "Run startup files from disk drives when the computer turns on.",
    type = "boolean",
})

settings.define("shell.autocomplete", {
    default = true,
    description = "Autocomplete program and arguments in the shell.",
    type = "boolean",
})
settings.define("edit.autocomplete", {
    default = true,
    description = "Autocomplete API and function names in the editor.",
        type = "boolean",
})
settings.define("lua.autocomplete", {
    default = true,
    description = "Autocomplete API and function names in the Lua REPL.",
        type = "boolean",
})

settings.define("edit.default_extension", {
    default = "lua",
    description = [[The file extension the editor will use if none is given. Set to "" to disable.]],
    type = "string",
})
settings.define("paint.default_extension", {
    default = "nfp",
    description = [[The file extension the paint program will use if none is given. Set to "" to disable.]],
    type = "string",
})

settings.define("list.show_hidden", {
    default = false,
    description = [[Whether the list program show  hidden files (those starting with ".").]],
    type = "boolean",
})

settings.define("motd.enable", {
    default = pocket == nil,
    description = "Display a random message when the computer starts up.",
    type = "boolean",
})
settings.define("motd.path", {
    default = "/rom/motd.txt:/motd.txt",
    description = [[The path to load random messages from. Should be a colon (":") separated string of file paths.]],
    type = "string",
})

settings.define("lua.warn_against_use_of_local", {
    default = true,
    description = [[Print a message when input in the Lua REPL starts with the word 'local'. Local variables defined in the Lua REPL are be inaccessible on the next input.]],
    type = "boolean",
})
settings.define("lua.function_args", {
    default = true,
    description = "Show function arguments when printing functions.",
    type = "boolean",
})
settings.define("lua.function_source", {
    default = false,
    description = "Show where a function was defined when printing functions.",
    type = "boolean",
})
settings.define("bios.strict_globals", {
    default = false,
    description = "Prevents assigning variables into a program's environment. Make sure you use the local keyword or assign to _G explicitly.",
    type = "boolean",
})
settings.define("shell.autocomplete_hidden", {
    default = false,
    description = [[Autocomplete hidden files and folders (those starting with ".").]],
    type = "boolean",
})

if term.isColour() then
    settings.define("bios.use_multishell", {
        default = true,
        description = [[Allow running multiple programs at once, through the use of the "fg" and "bg" programs.]],
        type = "boolean",
    })
end
if _CC_DEFAULT_SETTINGS then
    for sPair in string.gmatch(_CC_DEFAULT_SETTINGS, "[^,]+") do
        local sName, sValue = string.match(sPair, "([^=]*)=(.*)")
        if sName and sValue then
            local value
            if sValue == "true" then
                value = true
            elseif sValue == "false" then
                value = false
            elseif sValue == "nil" then
                value = nil
            elseif tonumber(sValue) then
                value = tonumber(sValue)
            else
                value = sValue
            end
            if value ~= nil then
                settings.set(sName, value)
            else
                settings.unset(sName)
            end
        end
    end
end

-- Load user settings
if fs.exists(".settings") then
    settings.load(".settings")
end

-- Run the shell
local ok, err = pcall(parallel.waitForAny,
    function()
        local sShell
        if term.isColour() and settings.get("bios.use_multishell") then
            sShell = "rom/programs/advanced/multishell.lua"
        else
            sShell = "rom/programs/shell.lua"
        end
        os.run({}, sShell)
        os.run({}, "rom/programs/shutdown.lua")
    end,
    rednet.run
)

-- If the shell errored, let the user read it.
term.redirect(term.native())
if not ok then
    printError(err)
    pcall(function()
        term.setCursorBlink(false)
        print("Press any key to continue")
        os.pullEvent("key")
    end)
end

-- End
os.shutdown()
