package com.hhk.aicoder.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.hhk.aicoder.model.enums.CodeGenTypeEnum;

import java.io.File;

public abstract class CodeFileSaverTemplate <T>{

    /**
     * 文件保存目录
     */
    private static final String FILE_SAVE_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";


    public final File saveCoder(T result){

        //验证输入
        validateInput(result);
        //构建目录
        String baseFilePath=buildUniqueDir();
        //保存文件
        saveFile(result, baseFilePath);
        //返回目录文件对象
        return new File(baseFilePath);


    }




    /**
     * 验证输入参数
     * @param result
     */
    protected void validateInput(T result) {
        if (result==null)
            throw new IllegalArgumentException("result is null");
    }

    /**
     * 构建唯一目录路径：tmp/code_output/bizType_雪花ID
     * @return
     */
    private String buildUniqueDir(){

        CodeGenTypeEnum bizType = getBizType();
        String uniqueDirName = StrUtil.format("{}_{}", bizType.getValue(), IdUtil.getSnowflakeNextIdStr());

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
    protected static void writetoFile(String filename,String filePath, String content){
        FileUtil.writeString(content, filePath + File.separator + filename, "UTF-8");

    }

    /**
     * 获取业务类型
     * @return
     */
    protected abstract CodeGenTypeEnum getBizType();


    /**
     * 根据类型保存文件S
     * @param result
     * @param baseFilePath
     */
    protected abstract void saveFile(T result, String baseFilePath);



}
