package com.hhk.aicoder.utils;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring上下文工具类
 * 用于在静态方法中获取Spring Bean
 *
 * 实现 BeanPostProcessor 是为了让本类最先被实例化，
 * 保证 applicationContext 在任何普通 Bean 创建之前完成赋值，
 * 避免 @Bean 工厂方法在启动期调用 getBean 时出现 NPE。
 */
@Component
public class SpringContextUtil implements ApplicationContextAware, BeanPostProcessor {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextUtil.applicationContext = applicationContext;
    }

    private static ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException(
                    "SpringContextUtil 尚未就绪，不要在 Bean 创建阶段调用 getBean");
        }
        return applicationContext;
    }

    /**
     * 获取Spring Bean
     */
    public static <T> T getBean(Class<T> clazz) {
        return getApplicationContext().getBean(clazz);
    }

    /**
     * 获取Spring Bean
     */
    public static Object getBean(String name) {
        return getApplicationContext().getBean(name);
    }

    /**
     * 根据名称和类型获取Spring Bean
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return getApplicationContext().getBean(name, clazz);
    }
}
