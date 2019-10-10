package com.talkingdata.ecommerce.support.querydsl.base;

import com.talkingdata.ecommerce.support.transaction.Transactional;
import com.talkingdata.ecommerce.support.querydsl.common.PrimaryEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.Serializable;
import java.util.List;

/**
 * @author wwy
 * @date 2019/7/16
 */
@Transactional
public abstract class BaseService<T extends PrimaryEntity<ID>, ID extends Serializable> implements Service {

    private static final Logger log = LoggerFactory.getLogger(Service.class);

    public BaseService() {}

    @PostConstruct
    public void startService() {
        log.debug("{} starting...", getClass().getSimpleName());
        init();
        log.debug("{} start success.", getClass().getSimpleName());
    }

    @PreDestroy
    public void stopService() {
        log.debug("{} stopping...", getClass().getSimpleName());
        clean();
        log.debug("{} stop success.", getClass().getSimpleName());
    }

    @Override
    public void init() {
    }

    @Override
    public void clean() {
    }

    public abstract BaseDao<T, ID> getDao();

    public ID insert(T t) throws Exception {
        return this.getDao().insert(t);
    }

    public int updateByPrimaryKey(T t) throws Exception {
        return this.getDao().updateByPrimaryKey(t);
    }

    public int updateByPrimaryKeySelective(T t) throws Exception {
        return this.getDao().updateByPrimaryKeySelective(t);
    }

    public T selectByPrimaryKey(ID value) throws Exception {
        return this.getDao().selectByPrimaryKey(value);
    }

    public void deleteByPrimaryKey(ID value) throws Exception {
        this.getDao().deleteByPrimaryKey(value);
    }

    public int queryByCount(BasePage page) throws Exception {
        return this.getDao().queryByCount(page);
    }

    public List<T> queryByList(BasePage page) throws Exception {
        Integer rowCount = this.queryByCount(page);
        page.getPager().setRowCount(rowCount);
        return this.getDao().queryByList(page);
    }

    public T queryBySingle(BasePage page) throws Exception {
        page.setPageSize(1);
        List<T> results = this.getDao().queryByList(page);
        return null != results && results.size() != 0 ? (T)results.get(0) : null;
    }
}
