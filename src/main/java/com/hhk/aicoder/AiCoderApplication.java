package com.hhk.aicoder;

import com.hhk.aicoder.config.AiCoderProperties;
import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(AiCoderProperties.class)
@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@MapperScan("com.hhk.aicoder.mapper")
public class AiCoderApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCoderApplication.class, args);
    }

}
