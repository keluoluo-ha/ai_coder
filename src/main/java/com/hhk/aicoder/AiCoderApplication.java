package com.hhk.aicoder;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@MapperScan("com.hhk.aicoder.mapper")
public class AiCoderApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCoderApplication.class, args);
    }

}
