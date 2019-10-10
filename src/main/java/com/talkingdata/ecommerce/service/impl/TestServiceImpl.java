package com.talkingdata.ecommerce.service.impl;

import com.talkingdata.ecommerce.entity.TestDemo;
import com.talkingdata.ecommerce.repository.TestDemoRepository;
import com.talkingdata.ecommerce.service.TestService;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author wwy
 * @date 2019-08-28
 */
@Singleton
public class TestServiceImpl implements TestService {

    @Inject
    private TestDemoRepository testDemoRepository;

    @Override
    public TestDemo findById(Integer id) {
        return testDemoRepository.findById(id);
    }
}
