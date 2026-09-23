package ru.sanseddy.cctweakedunicodesupport.lua;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.LuaThread;
import org.squiddev.cobalt.compiler.LuaC;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.lib.CoreLibraries;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class LuaSyntaxTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "/data/computercraft/lua/bios.lua",
        "/data/computercraft/lua/rom/apis/textutils.lua",
        "/data/computercraft/lua/rom/programs/edit.lua",
        "/data/computercraft/lua/rom/programs/help.lua",
        "/data/computercraft/lua/rom/programs/advanced/multishell.lua",
        "/data/computercraft/lua/rom/programs/rednet/chat.lua",
        "/data/computercraft/lua/rom/apis/window.lua",
        "/data/computercraft/lua/rom/modules/main/cc/image/nft.lua",
        "/data/computercraft/lua/rom/modules/main/cc/internal/error_printer.lua",
        "/data/computercraft/lua/rom/modules/main/cc/internal/menu.lua",
        "/data/computercraft/lua/rom/modules/main/cc/strings.lua",
        "/data/computercraft/lua/rom/modules/main/cc/pretty.lua",
        "/data/computercraft/lua/rom/modules/main/cc/internal/syntax/lexer.lua",
        "/data/computercraft/lua/rom/modules/main/cc/completion.lua",
        "/data/computercraft/lua/rom/apis/fs.lua",
        "/data/computercraft/lua/rom/programs/shell.lua",
        "/data/computercraft/lua/rom/apis/help.lua"
    })
    void romOverrideCompiles(String path) throws Exception {
        try (var stream = LuaSyntaxTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, () -> "Missing test resource " + path);
            LuaC.compile(new LuaState(), stream, "@" + path);
        } catch (IOException e) {
            throw new AssertionError("Could not read " + path, e);
        }
    }

    @Test
    void unicodeHelpersHandleValidAndMalformedBoundaries() throws Exception {
        var path = "/data/computercraft/lua/bios.lua";
        String bios;
        try (var stream = LuaSyntaxTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, () -> "Missing test resource " + path);
            bios = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        var start = bios.indexOf("-- [cc_tweaked_unicode_support] Character-aware string handling.");
        var end = bios.indexOf("-- Inject a stub for the old bit library", start);
        if (start < 0 || end < 0) throw new AssertionError("Could not locate the Unicode helper block");

        var checks = """
            local ya = string.char(0xD0, 0xAF)
            local euro = string.char(0xE2, 0x82, 0xAC)
            local smile = string.char(0xF0, 0x9F, 0x99, 0x82)
            assert(unicode.len("A" .. ya .. smile) == 3)
            local full_row = string.rep(ya, 51)
            assert(unicode.len(full_row) == 51)
            assert(string.len(full_row) == 51)
            assert(unicode.byte_len(full_row) == 102)
            assert(unicode.sub(full_row, 1, 51) == full_row)
            assert(unicode.sub(full_row, 51, 51) == ya)
            assert(unicode.char(0x42F) == ya)
            assert(unicode.char(0x1F642) == smile)
            assert(unicode.charback(smile, 4) == 4)
            assert(unicode.charback("A" .. string.char(0x95), 2) == 1)
            assert(unicode.align(ya, 1) == 0)
            assert(unicode.align(ya, 1, true) == 2)
            assert(unicode.align(euro, 2) == 0)
            assert(unicode.align(euro, 2, true) == 3)
            for _, value in ipairs({ -1, 0xD800, 0xFDD0, 0xFDEF, 0x110000, 1.5 }) do
                assert(not pcall(unicode.char, value))
            end
            """;

        var script = bios.substring(start, end) + checks;
        var state = new LuaState();
        CoreLibraries.standardGlobals(state);
        var function = LoadState.load(
            state, new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)), "=unicode-test.lua", state.globals()
        );
        LuaThread.run(new LuaThread(state, function), Constants.NIL);
    }

    @Test
    void windowHandlesMismatchedLengthsAndUnicode() throws Exception {
        var biosPath = "/data/computercraft/lua/bios.lua";
        var windowPath = "/data/computercraft/lua/rom/apis/window.lua";
        String bios, windowCode;
        try (var stream = LuaSyntaxTest.class.getResourceAsStream(biosPath)) {
            assertNotNull(stream);
            bios = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var stream = LuaSyntaxTest.class.getResourceAsStream(windowPath)) {
            assertNotNull(stream);
            windowCode = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        var start = bios.indexOf("-- [cc_tweaked_unicode_support] Character-aware string handling.");
        var end = bios.indexOf("-- Inject a stub for the old bit library", start);
        if (start < 0 || end < 0) throw new AssertionError("Could not locate the Unicode helper block");

        var testScript = bios.substring(start, end) + """
            _G.colors = {
                white = 1, orange = 2, magenta = 4, lightBlue = 8,
                yellow = 16, lime = 32, pink = 64, gray = 128,
                lightGray = 256, cyan = 512, purple = 1024, blue = 2048,
                brown = 4096, green = 8192, red = 16384, black = 32768,
            }
            _G.colours = _G.colors
            _G.dofile = function(path)
                return {
                    expect = function(idx, val, ...)
                        return val
                    end
                }
            end

            local lastBlit = nil
            local mockParent = {
                setCursorPos = function(x, y) end,
                setCursorBlink = function(b) end,
                setTextColor = function(c) end,
                setPaletteColour = function() end,
                getPaletteColour = function() return 0, 0, 0 end,
                isColor = function() return true end,
                blit = function(text, fg, bg)
                    lastBlit = { text = text, fg = fg, bg = bg }
                    assert(unicode.len(text) == unicode.len(fg), "text and fg must have equal length: " .. unicode.len(text) .. " vs " .. unicode.len(fg))
                    assert(unicode.len(text) == unicode.len(bg), "text and bg must have equal length: " .. unicode.len(text) .. " vs " .. unicode.len(bg))
                end
            }

            """ + windowCode + """
            local win = create(mockParent, 1, 1, 10, 5, true)
            assert(win ~= nil)

            -- Test 1: writing unicode text that wraps/clips
            win.setCursorPos(1, 1)
            win.write("Привет мир")
            assert(lastBlit ~= nil)
            assert(unicode.len(lastBlit.text) == 10)
            assert(unicode.len(lastBlit.fg) == 10)
            assert(unicode.len(lastBlit.bg) == 10)

            -- Test 2: blit with mismatched colors (should not error or crash)
            win.setCursorPos(1, 2)
            win.blit("Привет", "0", "fff")
            assert(unicode.len(lastBlit.text) == 10)
            assert(unicode.len(lastBlit.fg) == 10)
            assert(unicode.len(lastBlit.bg) == 10)

            -- Test 3: typing and erasing edge case (backspace simulation)
            win.setCursorPos(1, 1)
            win.write("              ")
            assert(unicode.len(lastBlit.text) == 10)
            assert(unicode.len(lastBlit.fg) == 10)
            assert(unicode.len(lastBlit.bg) == 10)
            """;

        var state = new LuaState();
        CoreLibraries.standardGlobals(state);
        var function = LoadState.load(
            state, new ByteArrayInputStream(testScript.getBytes(StandardCharsets.UTF_8)), "=window-test.lua", state.globals()
        );
        LuaThread.run(new LuaThread(state, function), Constants.NIL);
    }

    @Test
    void lexerHandlesCyrillicWithoutInfiniteLoop() throws Exception {
        var biosPath = "/data/computercraft/lua/bios.lua";
        var lexerPath = "/data/computercraft/lua/rom/modules/main/cc/internal/syntax/lexer.lua";
        String bios, lexerCode;
        try (var stream = LuaSyntaxTest.class.getResourceAsStream(biosPath)) {
            assertNotNull(stream);
            bios = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var stream = LuaSyntaxTest.class.getResourceAsStream(lexerPath)) {
            assertNotNull(stream);
            lexerCode = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        var start = bios.indexOf("-- [cc_tweaked_unicode_support] Character-aware string handling.");
        var end = bios.indexOf("-- Inject a stub for the old bit library", start);
        if (start < 0 || end < 0) throw new AssertionError("Could not locate the Unicode helper block");

        var testScript = bios.substring(start, end) + """
            local mock_package_loaded = {
                ["cc.internal.syntax.errors"] = {
                    unfinished_string = 1,
                    unfinished_string_escape = 2,
                    unfinished_long_string = 3,
                    unfinished_long_comment = 4,
                    malformed_number = 5,
                    malformed_long_string = 6,
                    unexpected_character = 7,
                    nested_long_str = 8,
                },
                ["cc.internal.syntax.parser"] = {
                    tokens = {
                        IDENT = 1, NUMBER = 2, STRING = 3, COMMENT = 4,
                        AND = 5, BREAK = 6, DO = 7, ELSE = 8, ELSEIF = 9,
                        END = 10, FALSE = 11, FOR = 12, FUNCTION = 13,
                        GOTO = 14, IF = 15, IN = 16, LOCAL = 17, NIL = 18,
                        NOT = 19, OR = 20, REPEAT = 21, RETURN = 22,
                        THEN = 23, TRUE = 24, UNTIL = 25, WHILE = 26,
                        DOT = 27, CONCAT = 28, DOTS = 29, EQ = 30,
                        EQUALS = 31, LE = 32, GT = 33, COLON = 34,
                        DOUBLE_COLON = 35, NE = 36, COMMA = 37,
                        SEMICOLON = 38, OPAREN = 39, CPAREN = 40,
                        OSQUARE = 41, CSQUARE = 42, OBRACE = 43,
                        CBRACE = 44, MUL = 45, DIV = 46, LEN = 47,
                        MOD = 48, POW = 49, ADD = 50, SUB = 51,
                        ERROR = 52,
                    }
                }
            }
            _G.require = function(mod)
                return mock_package_loaded[mod] or error("missing mod: " .. tostring(mod))
            end
            """;

        var state = new LuaState();
        CoreLibraries.standardGlobals(state);
        state.globals().rawset("raw_lexer_code", org.squiddev.cobalt.LuaString.valueOf(lexerCode));

        var biosFunc = LoadState.load(
            state, new ByteArrayInputStream(testScript.getBytes(StandardCharsets.UTF_8)), "=bios-test.lua", state.globals()
        );
        LuaThread.run(new LuaThread(state, biosFunc), Constants.NIL);

        var testAssertions = """
            local lexer = load(raw_lexer_code, "=lexer.lua")()
            assert(lexer and lexer.lex_one)

            local context = {
                line = function() end,
                report = function() end
            }

            -- Test lexing pure Cyrillic string: must terminate in finite steps and advance pos
            local str = "привет мир"
            local pos = 1
            local count = 0
            while pos <= unicode.byte_len(str) and count < 20 do
                local token_id, start_pos, end_pos = lexer.lex_one(context, str, pos)
                if not token_id then break end
                assert(end_pos >= start_pos, "end_pos must be >= start_pos")
                assert(end_pos >= pos, "end_pos must be >= pos")
                pos = end_pos + 1
                count = count + 1
            end
            assert(count < 20, "lexer looped too many times on Cyrillic string")
            assert(pos > unicode.byte_len(str), "lexer did not consume entire string")

            -- Test lexing code with Russian comments and strings
            local code = 'local s = "Привет мир!" -- комментарий'
            pos = 1
            count = 0
            while pos <= unicode.byte_len(code) and count < 50 do
                local token_id, start_pos, end_pos = lexer.lex_one(context, code, pos)
                if not token_id then break end
                pos = end_pos + 1
                count = count + 1
            end
            assert(count < 50, "lexer looped too many times on code with Cyrillic")
            assert(pos > unicode.byte_len(code), "lexer did not consume entire code string")
            """;

        var assertFunc = LoadState.load(
            state, new ByteArrayInputStream(testAssertions.getBytes(StandardCharsets.UTF_8)), "=assert-test.lua", state.globals()
        );
        LuaThread.run(new LuaThread(state, assertFunc), Constants.NIL);
    }

    @Test
    void autocompleteHandlesUnicodeChoicesAndFiles() throws Exception {
        var biosPath = "/data/computercraft/lua/bios.lua";
        var completionPath = "/data/computercraft/lua/rom/modules/main/cc/completion.lua";
        var textutilsPath = "/data/computercraft/lua/rom/apis/textutils.lua";
        String bios, completionCode, textutilsCode;
        try (var stream = LuaSyntaxTest.class.getResourceAsStream(biosPath)) {
            assertNotNull(stream);
            bios = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var stream = LuaSyntaxTest.class.getResourceAsStream(completionPath)) {
            assertNotNull(stream);
            completionCode = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var stream = LuaSyntaxTest.class.getResourceAsStream(textutilsPath)) {
            assertNotNull(stream);
            textutilsCode = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        var start = bios.indexOf("-- [cc_tweaked_unicode_support] Character-aware string handling.");
        var end = bios.indexOf("-- Inject a stub for the old bit library", start);
        if (start < 0 || end < 0) throw new AssertionError("Could not locate the Unicode helper block");

        var testScript = bios.substring(start, end) + """
            _G.require = function(mod)
                if mod == "cc.expect" then
                    return {
                        expect = function(idx, val, ...) return val end,
                        field = function(tbl, f, ...) return tbl and tbl[f] end,
                    }
                elseif mod == "cc.strings" then
                    return {
                        wrap = function(text, width) return { text } end,
                    }
                end
                error("unknown require: " .. tostring(mod))
            end
            _G.dofile = function(file)
                return _G.require
            end
            _G.peripheral = { getNames = function() return { "модем", "монитор" } end }
            _G.redstone = { getSides = function() return { "top", "bottom", "left", "right", "front", "back" } end }
            _G.settings = { getNames = function() return { "тест_настройка" } end }
            """;

        var state = new LuaState();
        CoreLibraries.standardGlobals(state);

        var biosFunc = LoadState.load(
            state, new ByteArrayInputStream(testScript.getBytes(StandardCharsets.UTF_8)), "=bios-test.lua", state.globals()
        );
        LuaThread.run(new LuaThread(state, biosFunc), Constants.NIL);

        state.globals().rawset("raw_completion_code", org.squiddev.cobalt.LuaString.valueOf(completionCode));
        state.globals().rawset("raw_textutils_code", org.squiddev.cobalt.LuaString.valueOf(textutilsCode));

        var testAssertions = """
            local completion = load(raw_completion_code, "=completion.lua")()
            assert(completion and completion.choice)

            -- Test 1: choice completion with Russian strings
            local results = completion.choice("при", { "привет", "пример", "пока" })
            assert(#results == 2, "expected 2 results, got " .. #results)
            assert(results[1] == "вет", "expected 'вет', got '" .. tostring(results[1]) .. "'")
            assert(results[2] == "мер", "expected 'мер', got '" .. tostring(results[2]) .. "'")

            -- Test 2: choice completion with add_space=true
            local full_match = completion.choice("привет", { "привет" }, true)
            assert(#full_match == 1 and full_match[1] == " ")

            -- Test 3: peripheral completion in Russian
            local peri_results = completion.peripheral("мо")
            assert(#peri_results == 2)
            assert(peri_results[1] == "дем")
            assert(peri_results[2] == "нитор")

            -- Test 4: textutils.complete with Russian keys
            local textutils_chunk = load(raw_textutils_code, "=textutils.lua")
            textutils_chunk()
            local tu_results = complete("па", { ["папка"] = 1, ["параметр"] = 2, ["другое"] = 3 })
            assert(#tu_results == 2, "expected 2 textutils completions, got " .. #tu_results)
            """;

        var assertFunc = LoadState.load(
            state, new ByteArrayInputStream(testAssertions.getBytes(StandardCharsets.UTF_8)), "=assert-test.lua", state.globals()
        );
        LuaThread.run(new LuaThread(state, assertFunc), Constants.NIL);
    }
}
