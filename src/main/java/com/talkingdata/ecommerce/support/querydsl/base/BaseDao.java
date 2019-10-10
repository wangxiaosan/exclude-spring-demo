package com.talkingdata.ecommerce.support.querydsl.base;

import com.talkingdata.ecommerce.support.querydsl.common.PrimaryEntity;

import java.io.Serializable;
import java.util.List;

/**
 * @author wwy
 * @date 2019/7/16
 */
public interface BaseDao<T extends PrimaryEntity<ID>, ID extends Serializable> {

    /**
     * 根据实体插入到表
     * 
     * @param record
     * @return 主键
     */
    ID insert(T record);

    /**
     * 根据实体列表批量插入
     * 
     * @param records
     * @return 最后一条主键
     */
    Integer insert(List<T> records);

    /**
     * 根据实体全量更新
     * 
     * @param record
     * @return 更新条数
     */
    int updateByPrimaryKey(T record);

    /**
     * 根据实体, 更新实体非空字段
     * 
     * @param record
     * @return
     */
    int updateByPrimaryKeySelective(T record);

    /**
     * 根据主键查询实体
     * 
     * @param id 主键
     * @return 实体
     */
    T selectByPrimaryKey(ID id);

    /**
     * 根据实体条件查询一条实体
     * 
     * @param record 实体条件
     * @return 实体
     */
    T findOne(T record);

    /**
     * 根据实体条件查询实体列表
     * 
     * @param record 实体条件
     * @return 实体列表
     */
    List<T> findAll(T record);

    /**
     * 根据主键删除
     * 
     * @param id
     * @return 删除条数
     */
    int deleteByPrimaryKey(ID id);

    /**
     * 根据page参数, 查询总count
     * 
     * @param page
     * @param <P>
     * @return int count
     */
    <P extends BasePage> int queryByCount(P page);

    /**
     * 根据page参数, 查询实体List
     * 
     * @param page
     * @param <P>
     * @return 实体list
     */
    <P extends BasePage> List<T> queryByList(P page);
}
