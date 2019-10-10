package com.talkingdata.ecommerce.entity;

import com.talkingdata.ecommerce.support.querydsl.common.PrimaryEntity;
import com.talkingdata.ecommerce.support.querydsl.common.PrimaryKey;

import javax.annotation.Generated;

/**
 * TestDemo is a Querydsl bean type
 */
@Generated("com.talkingdata.appbuilder.codegen.SimpleBeanSerializer")
public class TestDemo implements PrimaryEntity<Integer> {

    @PrimaryKey("id")
    private Integer id;

    private String name;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Integer getPrimaryKey() {
         return id;
    }

    @Override
    public void setPrimaryKey(Integer id) {
        this.id = id;
    }

}

