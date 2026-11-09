package com.hhk.aicoder.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 长期记忆标签
 *
 * @author hhk
 */
@Getter
public enum MemoryTagEnum {

    USER_PREFERENCE("用户偏好", "USER_PREFERENCE"),
    PROJECT_CONVENTION("项目约定", "PROJECT_CONVENTION"),
    IMPORTANT_FACT("重要事实", "IMPORTANT_FACT");

    private final String text;
    private final String value;

    MemoryTagEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值
     * @return 枚举值
     */
    public static MemoryTagEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (MemoryTagEnum anEnum : MemoryTagEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
