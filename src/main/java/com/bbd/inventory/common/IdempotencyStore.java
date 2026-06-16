package com.bbd.inventory.common;

import org.apache.logging.log4j.util.Supplier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.jdbc.datasource.init.DatabasePopulatorUtils.execute;

@Component
public class IdempotencyStore {

    private final Map<String, Object> responses = new ConcurrentHashMap<>();

    /*
        if idempotent key == null -> execute
        if idempotent key != null   -> key in map: return cached (replay)
                                    -> key not in map: execute -> store -> run
     */
    @SuppressWarnings("unchecked")
    public <T> T execute(String key, Supplier<T> work) {

        if (key == null) return work.get();

        Object cached = responses.get(key);
        if (cached != null) return (T) cached;          // replay stored response

        T result = work.get();                          // real work
        responses.put(key, result);                     // store for replay
        return result;
    }
}
