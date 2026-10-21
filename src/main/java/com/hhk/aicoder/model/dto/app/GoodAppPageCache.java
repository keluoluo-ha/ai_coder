package com.hhk.aicoder.model.dto.app;

import com.hhk.aicoder.model.vo.AppVO;
import com.mybatisflex.core.paginate.Page;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 精选应用列表缓存快照。避免直接序列化 MyBatis-Flex Page 丢泛型。
 */
@Data
public class GoodAppPageCache {

    private long pageNumber;
    private long pageSize;
    private long totalRow;
    private List<AppVO> records = new ArrayList<>();

    public static GoodAppPageCache from(Page<AppVO> page) {
        GoodAppPageCache cache = new GoodAppPageCache();
        cache.setPageNumber(page.getPageNumber());
        cache.setPageSize(page.getPageSize());
        cache.setTotalRow(page.getTotalRow());
        cache.setRecords(page.getRecords() == null ? new ArrayList<>() : page.getRecords());
        return cache;
    }

    public Page<AppVO> toPage() {
        Page<AppVO> page = new Page<>(pageNumber, pageSize, totalRow);
        page.setRecords(records == null ? new ArrayList<>() : records);
        return page;
    }
}
