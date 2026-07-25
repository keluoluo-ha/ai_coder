package com.hhk.aicoder.ai;

import com.hhk.aicoder.ai.model.HtmlCodeResult;
import com.hhk.aicoder.ai.model.MultiFileCodeResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;



@SpringBootTest
class AiCodeGeneratorServiceTest {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    @Test
    void generateHtmlCode() {
        HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode("做个程序员鱼皮的工作记录小工具");
        System.out.println(htmlCodeResult);
    }

    @Test
    void generateMultiFileCode() {
        MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode("做个程序员鱼皮的留言板");
        Assertions.assertNotNull(multiFileCodeResult);
    }
}
