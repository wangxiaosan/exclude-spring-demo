package com.talkingdata.ecommerce.repository.impl;

import com.talkingdata.ecommerce.entity.TestDemo;
import com.talkingdata.ecommerce.entity.query.QTestDemo;
import com.talkingdata.ecommerce.repository.TestDemoRepository;
import com.talkingdata.ecommerce.support.querydsl.base.AbstractBaseDao;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;

/**
 * TestDemoRepositoryImpl is a Querydsl repository implement type
 */
@Singleton
public class TestDemoRepositoryImpl extends AbstractBaseDao<TestDemo, Integer> implements TestDemoRepository {

    @Inject
    public TestDemoRepositoryImpl(DataSource dataSource) {
    	super(dataSource);
    }

    @Override
    public TestDemo findById(Integer id) {
        return queryFactory.selectFrom(QTestDemo.testDemo).where(QTestDemo.testDemo.id.eq(id)).fetchOne();
    }
}

