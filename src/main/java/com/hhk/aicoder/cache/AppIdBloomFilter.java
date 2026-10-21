package com.hhk.aicoder.cache;

import com.hhk.aicoder.mapper.AppMapper;
import com.hhk.aicoder.model.entity.App;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.stereotype.Component;

/**
 * appId 布隆过滤器。不存在直接 404，避免缓存穿透打到 DB。
 * 误判会多查一次 DB，漏判（假阴性）由创建时 add 保证；启动时从 DB 回填。
 */
@Slf4j
@Component
public class AppIdBloomFilter {

    private final RBloomFilter<Long> bloomFilter;
    private final AppMapper appMapper;

    public AppIdBloomFilter(RBloomFilter<Long> appIdBloomFilter, AppMapper appMapper) {
        this.bloomFilter = appIdBloomFilter;
        this.appMapper = appMapper;
    }

    /**
     * 启动时把数据库里所有 App 的 ID 分批读出来，填进布隆过滤器。
     */
    @PostConstruct
    public void warmUp() {
        try {
            long count = 0;
            long pageNum = 1;
            long pageSize = 500;
            while (true) {
                Page<App> page = appMapper.paginate(Page.of(pageNum, pageSize), QueryWrapper.create());
                if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
                    break;
                }
                for (App app : page.getRecords()) {
                    if (app.getId() != null) {
                        bloomFilter.add(app.getId());
                        count++;
                    }
                }
                if (page.getRecords().size() < pageSize) {
                    break;
                }
                pageNum++;
            }
            log.info("appId 布隆过滤器回填完成, count={}", count);
        } catch (Exception e) {
            log.warn("appId 布隆过滤器回填失败，降级为每次查库", e);
        }
    }

    public boolean mightContain(Long appId) {
        if (appId == null) {
            return false;
        }
        try {
            return bloomFilter.contains(appId);
        } catch (Exception e) {
            log.warn("布隆过滤器查询失败，降级放行, appId={}", appId, e);
            return true;
        }
    }

    public void add(Long appId) {
        if (appId == null) {
            return;
        }
        try {
            bloomFilter.add(appId);
        } catch (Exception e) {
            log.warn("布隆过滤器写入失败, appId={}", appId, e);
        }
    }

    public void removeHint(Long appId) {
        // Redisson 标准布隆不支持删除；删除应用后允许一次误查 DB，不在此处理。
    }
}
