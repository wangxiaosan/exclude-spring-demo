package com.talkingdata.ecommerce.support.querydsl.common;

import java.io.Serializable;

/**
 * @author wwy
 * @date 2019/7/16
 */
public interface PrimaryEntity<ID extends Serializable> extends Serializable {

    ID getPrimaryKey();

    void setPrimaryKey(ID id);

}
