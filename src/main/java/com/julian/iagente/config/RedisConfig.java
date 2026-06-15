package com.julian.iagente.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
public class RedisConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {

        // =========================
        // DEFAULT (fallback seguro)
        // =========================
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues();

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)

                // =========================
                // 🧠 MEMORIA DEL USUARIO IA
                // =========================
                .withCacheConfiguration("personas",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofDays(1))
                                .disableCachingNullValues()
                )

                .withCacheConfiguration("memory",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofDays(7))
                                .disableCachingNullValues()
                )

                // =========================
                // 🌦️ DATOS EXTERNOS (TOOLS)
                // =========================
                .withCacheConfiguration("weather",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(10))
                                .disableCachingNullValues()
                )

                // =========================
                // 🤖 RESPUESTAS / CONTEXTO IA
                // (si luego cacheas prompts o embeddings)
                // =========================
                .withCacheConfiguration("ai_context",
                        RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofHours(6))
                                .disableCachingNullValues()
                )

                .build();
    }
}
