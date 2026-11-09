package com.hhk.aicoder.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 长期记忆 实体类。
 *
 * @author hhk
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("agent_memory")
public class AgentMemory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 所属用户
     */
    @Column("user_id")
    private Long userId;

    /**
     * 记忆内容
     */
    private String content;

    /**
     * 记忆标签
     */
    private String tag;

    /**
     * 1=生效 0=已作废
     */
    @Column("is_active")
    private Integer isActive;

    /**
     * 创建时间
     */
    @Column("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column("update_time")
    private LocalDateTime updateTime;
}
