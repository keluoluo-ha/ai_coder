package com.hhk.aicoder.core;


import com.hhk.aicoder.ai.AiCodeGeneratorService;
import com.hhk.aicoder.ai.AiCodeGeneratorServiceFactory;
import com.hhk.aicoder.ai.model.HtmlCodeResult;
import com.hhk.aicoder.ai.model.MultiFileCodeResult;
import com.hhk.aicoder.core.parser.CodeParserExecutor;
import com.hhk.aicoder.core.saver.CodeFileSaverExecutor;
import com.hhk.aicoder.exception.BusinessException;
import com.hhk.aicoder.exception.ErrorCode;
import com.hhk.aicoder.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * AI代码生成外观类 组合生成和保存类
 */
@Slf4j
@Service
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    /**
     *ai生成统一入口 根据类型生成代码
     * @param userMesssage
     * @param codeGenTypeEnum
     * @return
     */
    public File generateCode(String userMesssage, CodeGenTypeEnum codeGenTypeEnum) {

        if(codeGenTypeEnum==null){
            throw new RuntimeException("codeGenTypeEnum is null");
        }

         return  switch (codeGenTypeEnum){
            case CodeGenTypeEnum.HTML -> {
                HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode(userMesssage);
                yield CodeFileSaverExecutor.executeSaver(htmlCodeResult, codeGenTypeEnum);
            }
            case CodeGenTypeEnum.MULTI_FILE ->{
                MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode(userMesssage);
                yield CodeFileSaverExecutor.executeSaver(multiFileCodeResult, codeGenTypeEnum);
            }
            default -> throw new RuntimeException("codeGenTypeEnum is not support");

        };

    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(result,CodeGenTypeEnum.HTML);
            }
            case MULTI_FILE ->{
                Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(result,CodeGenTypeEnum.MULTI_FILE);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }




    /**
     * 生成代码并保存（流式）
     * @param result
     * @param codeGenTypeEnum
     * @return
     */
    private Flux<String> processCodeStream(Flux<String> result ,CodeGenTypeEnum codeGenTypeEnum) {

        // 当流式返回生成代码完成后，再保存代码
        StringBuilder codeBuilder = new StringBuilder();
        return result
                .doOnNext(chunk -> {
                    // 实时收集代码片段
                    codeBuilder.append(chunk);
                })
                .doOnComplete(() -> {
                    // 流式返回完成后保存代码
                    try {
                        String completeCode = codeBuilder.toString();
                        //解析代码
                        Object object = CodeParserExecutor.executeParser(completeCode, codeGenTypeEnum);
                        // 保存代码到文件
                        File file = CodeFileSaverExecutor.executeSaver(object, codeGenTypeEnum);
                        log.info("保存成功，路径为：" + file.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("保存失败: {}", e.getMessage());
                    }
                });
    }




}
