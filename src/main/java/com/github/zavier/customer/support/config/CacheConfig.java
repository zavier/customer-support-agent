package com.github.zavier.customer.support.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.zavier.customer.support.web.ChatController.ChatSession;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public Cache<String, ChatSession> chatSessionCache() {
        return Caffeine.newBuilder()
                // 基于访问时间的过期：30分钟没有访问就过期
                .expireAfterAccess(30, TimeUnit.MINUTES)
                // 基于写入时间的过期：最多保留24小时
                .expireAfterWrite(24, TimeUnit.HOURS)
                // 最大缓存数量
                .maximumSize(10000)
                // 记录统计信息
                .recordStats()
                .build();
    }
}