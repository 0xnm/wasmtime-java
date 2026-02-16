package io.github.kawamuray.wasmtime;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigTest {
    @Test
    public void testNewConfig(){
        try(Config config = new Config()){
            Assert.assertNotEquals(0, config.innerPtr());
        }
    }
    @Test
    public void testStrategy(){
        try(Config config = new Config()){
            config.strategy(Strategy.AUTO).strategy(Strategy.CRANELIFT);
        }
    }
    @Test
    public void testOptLevel(){
        try(Config config = new Config();
            Config config1 = new Config();
            Config config2 = new Config();
            Config config3 = new Config();
            Config config4 = new Config();
            Config config5 = new Config()){
            config.strategy(Strategy.CRANELIFT).craneliftOptLevel(OptLevel.SPEED_AND_SIZE);
            config1.strategy(Strategy.CRANELIFT).craneliftOptLevel(OptLevel.SPEED);
            config2.strategy(Strategy.CRANELIFT).craneliftOptLevel(OptLevel.NONE);
            config3.strategy(Strategy.AUTO).craneliftOptLevel(OptLevel.SPEED_AND_SIZE);
            config4.strategy(Strategy.AUTO).craneliftOptLevel(OptLevel.SPEED);
            config5.strategy(Strategy.AUTO).craneliftOptLevel(OptLevel.NONE);
        }
    }

    @Test
    public void testCustomConfig(){
        try(Config config = new Config()) {
            config.strategy(Strategy.CRANELIFT)
                    .craneliftOptLevel(OptLevel.SPEED_AND_SIZE)
                    .debugInfo(true);
            try (Engine engine = new Engine(config);
                 Store store = new Store(engine)) {
                 store.engine();
            }
        }
    }

    @Test
    public void testNegativeSizesFail() {
        try (Config config = new Config()) {
            RuntimeException stack = Assert.assertThrows(RuntimeException.class, () -> config.maxWasmStack(-1));
            Assert.assertTrue(stack.getMessage().contains("non-negative"));

            RuntimeException guard = Assert.assertThrows(RuntimeException.class, () -> config.memoryGuardSize(-1));
            Assert.assertTrue(guard.getMessage().contains("non-negative"));

            RuntimeException reservation = Assert.assertThrows(RuntimeException.class, () -> config.memoryReservation(-1));
            Assert.assertTrue(reservation.getMessage().contains("non-negative"));
        }
    }

    @Test
    public void testCacheConfig() throws Exception {
        Path configPath = Files.createTempFile("wasmtime-cache-config", ".toml");
        Files.write(configPath, "[cache]\n".getBytes(StandardCharsets.UTF_8));
        try (Config config = new Config()) {
            config.cache(configPath.toString());
            try (Engine engine = new Engine(config)) {
                Assert.assertNotEquals(0, engine.innerPtr());
            }
        } finally {
            Files.deleteIfExists(configPath);
        }
    }

    @Test
    public void testCacheInvalidPathFails() {
        try (Config config = new Config()) {
            RuntimeException ex = Assert.assertThrows(RuntimeException.class,
                    () -> config.cache("/definitely/non-existent/wasmtime-cache.toml"));
            Assert.assertNotNull(ex.getMessage());
        }
    }
}
