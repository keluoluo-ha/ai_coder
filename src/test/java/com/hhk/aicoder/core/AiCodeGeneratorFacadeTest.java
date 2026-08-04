package com.hhk.aicoder.core;

import com.hhk.aicoder.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;

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
    @Test
    void generateVueProjectCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                "简单的任务记录网站，总代码量不超过 200 行",
                CodeGenTypeEnum.VUE_PROJECT, 36216L);
        // 阻塞等待所有数据收集完成
        List<String> result = codeStream.collectList().block();
        // 验证结果
        Assertions.assertNotNull(result);
        String completeContent = String.join("", result);
        Assertions.assertNotNull(completeContent);
    }


}