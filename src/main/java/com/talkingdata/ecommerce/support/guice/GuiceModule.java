package com.talkingdata.ecommerce.support.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.matcher.Matchers;
import com.talkingdata.ecommerce.support.querydsl.common.ConnectionContext;
import com.talkingdata.ecommerce.support.transaction.TransactionInterceptor;
import com.talkingdata.ecommerce.support.transaction.Transactional;
import com.talkingdata.ecommerce.utils.Configs;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.inject.Singleton;
import javax.sql.DataSource;

/**
 * @author wwy
 * @date 2019-08-23
 */
public abstract class GuiceModule extends AbstractModule {

    @Override
    protected void configure() {
        initialize();
        bind(ConnectionContext.class).in(Scopes.SINGLETON);
        // 事务拦截器
        TransactionInterceptor interceptor = new TransactionInterceptor();
        requestInjection(interceptor);
        bindInterceptor(Matchers.any(), Matchers.annotatedWith(Transactional.class), interceptor);
    }

    /**
     * 初始化guice环境
     */
    protected abstract void initialize();

    @Provides
    @Singleton
    public DataSource dataSource() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setUsername(Configs.getString("jdbc.username"));
        hikariConfig.setPassword(Configs.getString("jdbc.password"));
        hikariConfig.setMaximumPoolSize(Configs.getInt("jdbc.pool.maxActive"));
        hikariConfig.setMinimumIdle(Configs.getInt("jdbc.pool.minIdle"));
        hikariConfig.setDriverClassName(Configs.getString("jdbc.driver"));
        hikariConfig.setJdbcUrl(Configs.getString("jdbc.url"));
        return new HikariDataSource(hikariConfig);
    }
}
