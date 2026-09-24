package com.pr_reviewer.prreviewer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            org.springframework.core.env.Environment env) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(env.getProperty("spring.data.redis.host"));
        config.setPort(Integer.parseInt(env.getProperty("spring.data.redis.port")));
        config.setPassword(env.getProperty("spring.data.redis.password"));
        return new LettuceConnectionFactory(config);
    }
}