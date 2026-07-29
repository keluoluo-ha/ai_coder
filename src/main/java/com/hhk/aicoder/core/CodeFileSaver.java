package com.hhk.aicoder.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.hhk.aicoder.ai.model.HtmlCodeResult;
import com.hhk.aicoder.ai.model.MultiFileCodeResult;
import com.hhk.aicoder.model.enums.CodeGenTypeEnum;
import org.springframework.context.annotation.Configuration;

import java.io.File;


/**
 * 文件保存器
 */

@Deprecated
public class CodeFileSaver {


    /**
     * 文件保存目录
     */
    private static final String FILE_SAVE_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";


    /**
     * 保存HtmlCodeResult
     * @param result
     * @return
     */
    public static File saveHtmlCodeResult(HtmlCodeResult result){

        String uniqueDir = buildUniqueDir(CodeGenTypeEnum.HTML.getValue());

        writetoFile("index.html", uniqueDir, result.getHtmlCode());

        return new File(uniqueDir);
    }


    /**
     * 保存MultiFileCodeResult
     * @param multiFileCodeResult
     * @return
     */
    public static File saveMultiFileCodeResult(MultiFileCodeResult multiFileCodeResult){

        String uniqueDir = buildUniqueDir(CodeGenTypeEnum.MULTI_FILE.getValue());

        writetoFile("index.html", uniqueDir, multiFileCodeResult.getHtmlCode());
        writetoFile("index.css", uniqueDir, multiFileCodeResult.getCssCode());
        writetoFile("index.js", uniqueDir, multiFileCodeResult.getJsCode());


        return new File(uniqueDir);
    }


    /**
     * 构建唯一目录路径：tmp/code_output/bizType_雪花ID
     * @param bizType
     * @return
     */
    private static String buildUniqueDir(String bizType){

        String uniqueDirName = StrUtil.format("{}_{}", bizType, IdUtil.getSnowflakeNextIdStr());

        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;

        FileUtil.mkdir(dirPath);

        return dirPath;

    }

    /**
     * 写入单个文件
     * @param filename
     * @param filePath
     * @param content
     */
    private static void writetoFile(String filename,String filePath, String content){

        FileUtil.writeString(content, filePath + File.separator + filename, "UTF-8");

    }
}
