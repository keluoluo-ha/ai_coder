package com.hhk.aicoder.core.parser;


import com.hhk.aicoder.exception.BusinessException;
import com.hhk.aicoder.exception.ErrorCode;
import com.hhk.aicoder.model.enums.CodeGenTypeEnum;

/**
 * 代码解析执行器
 */
public class CodeParserExecutor {

    private static final HtmlCoderParser htmlCoderParser =new HtmlCoderParser();
    private static final MultiFileCodeParser multiFileCodeParser =new MultiFileCodeParser();


    public static Object executeParser(String content, CodeGenTypeEnum codeGenTypeEnum){
       return switch (codeGenTypeEnum){
            case HTML->htmlCoderParser.parseCode(content);
            case MULTI_FILE-> multiFileCodeParser.parseCode(content);
            default-> throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"不支持的代码解析类型");
        };

    }

}
