package com.hhk.aicoder.ai;

import dev.langchain4j.service.SystemMessage;

public interface AiCodeGeneratorService {

    /**
     * 生成单文件代码
     * @param prompt
     * @return
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    String generateHtmlCode(String prompt);


    /**
     * 生成多文件代码
     * @param prompt
     * @return
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    String generateMultiFileCode(String prompt);

}
