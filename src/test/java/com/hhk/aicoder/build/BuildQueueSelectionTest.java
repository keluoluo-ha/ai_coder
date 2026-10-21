package com.hhk.aicoder.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhk.aicoder.config.AiCoderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BuildQueueSelectionTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withBean(AiCoderProperties.class, AiCoderProperties::new)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
            .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
            .withBean(RabbitProperties.class, RabbitProperties::new)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(RedisStreamBuildQueueService.class, RabbitMqBuildQueueService.class);

    @Test
    void defaultsToRedis() {
        context.run(ctx -> {
            assertThat(ctx).hasSingleBean(BuildQueueService.class);
            assertThat(ctx.getBean(BuildQueueService.class)).isInstanceOf(RedisStreamBuildQueueService.class);
        });
    }

    @Test
    void explicitlySelectsRedis() {
        context.withPropertyValues("aicoder.build.queue.type=redis").run(ctx -> {
            assertThat(ctx).hasSingleBean(BuildQueueService.class);
            assertThat(ctx).doesNotHaveBean(RabbitMqBuildQueueService.class);
        });
    }

    @Test
    void selectsRabbitWithoutConnectingToBroker() {
        context.withPropertyValues("aicoder.build.queue.type=rabbitmq").run(ctx -> {
            assertThat(ctx).hasSingleBean(BuildQueueService.class);
            assertThat(ctx.getBean(BuildQueueService.class)).isInstanceOf(RabbitMqBuildQueueService.class);
            assertThat(ctx).doesNotHaveBean(RedisStreamBuildQueueService.class);
        });
    }
}
