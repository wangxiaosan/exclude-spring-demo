package com.talkingdata.ecommerce.support.querydsl.base;

/**
 * 2016-12-28 copy from dmp
 */
public class BasePage {

    private Integer page = 1;

    private Integer pageSize = 10;

    /**
     * 适配um的分页字段, 相当于pageSize
     */
    private Integer rows;

    private String orderBy;

    private String order;

    private String q;

    /**
     * 适配um的排序字段, 相当于orderBy
     */
    private String sort;

    /**
     * 分页导航
     */
    private Pager pager = new Pager();

    public Pager getPager() {
        pager.setPageId(getPage());
        pager.setPageSize(getPageSize());
        if (null != getRows()) {
            pager.setPageSize(getRows());
        }
        String orderField = "";
        if (orderBy != null && orderBy.trim().length() > 0) {
            orderField = orderBy;
        }
        if (orderField.trim().length() > 0 && (order != null && order.trim().length() > 0)) {
            orderField += " " + order;
        }
        pager.setOrderField(orderField);
        return pager;
    }

    public void setPager(Pager pager) {
        this.pager = pager;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        if (null != this.rows) {
            return rows;
        }
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(String orderBy) {
        this.orderBy = orderBy;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public Integer getRows() {
        if (null == this.rows) {
            return pageSize;
        }
        return rows;
    }
    public void setRows(Integer rows) {
        this.rows = rows;
    }
}
