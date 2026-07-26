package com.hhk.aicoder.core.parser;

public interface CodeParser<T> {


    /**
     * 代码解析器入口
     * @param content
     * @return
     */
    T parseCode(String content);


}
