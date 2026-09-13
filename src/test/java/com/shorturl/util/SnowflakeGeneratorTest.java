package com.shorturl.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SnowflakeGeneratorTest {

    @Test
    void shouldGenerateUniqueIds(){

        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();

        Set<Long> ids = new HashSet<>();

        for(int i = 0; i < 10_000; i++){
            long id = generator.generateId();
            assertTrue(ids.add(id));
        }
        assertEquals(10_000, ids.size());
    }

    @Test
    void shouldGenerateUniqueIdsConcurrently() throws InterruptedException {

        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();

        Set<Long> ids = ConcurrentHashMap.newKeySet();

        int threadsCount = 20;
        int idsPerThread = 1_000;

        Thread[] threads = new Thread[threadsCount];

        for(int i = 0; i < threadsCount; i++){
            threads[i] = new Thread(() -> {
                for(int j = 0; j < idsPerThread; j++){
                    ids.add(generator.generateId());
                }
            });
            threads[i].start();
        }

        for(Thread thread : threads){
            thread.join();
        }

        assertEquals(threadsCount * idsPerThread, ids.size());
    }
}
