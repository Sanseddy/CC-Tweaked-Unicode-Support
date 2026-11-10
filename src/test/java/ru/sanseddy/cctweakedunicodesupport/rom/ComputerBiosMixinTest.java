//? if neoforge {
package ru.sanseddy.cctweakedunicodesupport.rom;

import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.core.ComputerContext;
import dan200.computercraft.core.computer.Computer;
import dan200.computercraft.core.computer.ComputerEnvironment;
import dan200.computercraft.core.computer.GlobalEnvironment;
import dan200.computercraft.core.filesystem.MemoryMount;
import dan200.computercraft.core.lua.ILuaMachine;
import dan200.computercraft.core.metrics.MetricsObserver;
import dan200.computercraft.core.terminal.Terminal;
import org.junit.jupiter.api.Test;
import org.squiddev.cobalt.LuaState;
import org.squiddev.cobalt.compiler.LuaC;

import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs under NeoForge's transforming test loader, exercising the actual BIOS redirect. */
class ComputerBiosMixinTest {
    @Test
    void luaMachineReceivesUnicodeBiosWithoutCallingVanillaResourceApi() throws Exception {
        // A proxy supports both the legacy and 1.121.0 GlobalEnvironment interfaces.
        var globalEnvironment = (GlobalEnvironment) Proxy.newProxyInstance(
            GlobalEnvironment.class.getClassLoader(), new Class<?>[]{GlobalEnvironment.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getHostString", "getUserAgent" -> "Unicode BIOS test";
                default -> throw new AssertionError("Unexpected vanilla resource call: " + method.getName());
            }
        );
        var computerEnvironment = new ComputerEnvironment() {
            public int getDay() { return 0; }
            public double getTimeOfDay() { return 0; }
            public MetricsObserver getMetrics() { return MetricsObserver.discard(); }
            public WritableMount createRootMount() { return new MemoryMount(); }
        };
        var receivedBios = new AtomicBoolean();
        var context = ComputerContext.builder(globalEnvironment).luaFactory((environment, bios) -> {
            try {
                var contents = new String(bios.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(contents.contains("-- [cc_tweaked_unicode_support] Character-aware string handling."));
                assertNotNull(LuaC.compile(new LuaState(), new java.io.ByteArrayInputStream(
                    contents.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "@bios.lua"));
                receivedBios.set(true);
            } catch (Exception e) {
                throw new AssertionError("Could not load Unicode BIOS", e);
            }
            return (ILuaMachine) Proxy.newProxyInstance(
                ILuaMachine.class.getClassLoader(), new Class<?>[]{ILuaMachine.class}, (proxy, method, args) -> null
            );
        }).build();
        try {
            var computer = new Computer(context, computerEnvironment, new Terminal(51, 19, true), 0);
            var executorField = Computer.class.getDeclaredField("executor");
            executorField.setAccessible(true);
            var executor = executorField.get(computer);
            var createLuaMachine = executor.getClass().getDeclaredMethod("createLuaMachine");
            createLuaMachine.setAccessible(true);
            assertNotNull(createLuaMachine.invoke(executor));
            assertTrue(receivedBios.get(), "The Lua machine must receive the Unicode BIOS");
        } finally {
            context.ensureClosed(5, TimeUnit.SECONDS);
        }
    }
}
//?}
