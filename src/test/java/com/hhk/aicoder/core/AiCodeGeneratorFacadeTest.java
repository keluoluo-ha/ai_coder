package com.hhk.aicoder.core;

import com.hhk.aicoder.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiCodeGeneratorFacadeTest {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    public void testGenerateCode() {
        Long appid= 12345L;
        Flux<String> flux = aiCodeGeneratorFacade.generateAndSaveCodeStream("帮我生成博客网站 代码不超过100行", CodeGenTypeEnum.HTML,appid);
        // blockLast()阻塞当前测试线程，等待flux执行完毕，才能进到内部断点
        String last = flux.blockLast();
        System.out.println(last);
    }

    @Test
    public void testGenerateCode1() {
        File generated = aiCodeGeneratorFacade.generateCode("帮我生成博客网站 代码不超过100行", CodeGenTypeEnum.MULTI_FILE, 12345L);
        System.out.println(generated.getAbsolutePath());
    }


}