package com.moksh.walletwizzard.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Wraps Spring Boot's auto-configured HikariCP DataSource with RlsDataSource.
 * Using BeanPostProcessor lets Spring Boot handle all datasource configuration
 * from application.yaml (url, credentials, pool settings) as normal, and we
 * intercept only after the bean is fully initialised.
 */
@Configuration
public class DatabaseConfig implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource dataSource && "dataSource".equals(beanName)) {
            return new RlsDataSource(dataSource);
        }
        return bean;
    }
}
