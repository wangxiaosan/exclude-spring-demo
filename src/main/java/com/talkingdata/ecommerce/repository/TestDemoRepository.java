package com.talkingdata.ecommerce.repository;

import com.talkingdata.ecommerce.entity.TestDemo;
import com.talkingdata.ecommerce.support.querydsl.base.BaseDao;


/**
 * TestDemoRepository is a Querydsl repository interface type
 */
public interface TestDemoRepository extends BaseDao<TestDemo, Integer> {

    TestDemo findById(Integer id);

}

