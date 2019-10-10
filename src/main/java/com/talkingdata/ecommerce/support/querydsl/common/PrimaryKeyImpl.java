package com.talkingdata.ecommerce.support.querydsl.common;

import java.lang.annotation.Annotation;

/**
 * @author wwy
 * @date 2019/7/16
 */
public class PrimaryKeyImpl implements PrimaryKey {

    private String value;

    public PrimaryKeyImpl(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return PrimaryKey.class;
    }
}
