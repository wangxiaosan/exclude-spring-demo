package com.talkingdata.ecommerce.support.querydsl.base;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.RelationalPath;
import com.querydsl.sql.SQLQuery;
import com.querydsl.sql.SQLQueryFactory;
import com.querydsl.sql.dml.DefaultMapper;
import com.querydsl.sql.dml.SQLInsertClause;
import com.querydsl.sql.dml.SQLUpdateClause;
import com.talkingdata.ecommerce.support.querydsl.common.PrimaryEntity;
import com.talkingdata.ecommerce.support.querydsl.common.PrimaryKey;
import com.talkingdata.ecommerce.support.querydsl.common.QueryDslConfig;
import com.talkingdata.ecommerce.utils.BeanUtils;
import com.talkingdata.ecommerce.utils.ClassUtils;
import com.talkingdata.ecommerce.utils.ReflectionUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author wwy
 * @date 2019/7/17
 */
public abstract class AbstractBaseDao<T extends PrimaryEntity<ID>, ID extends Serializable> implements BaseDao<T, ID> {

    private static final String NO_CLASS_FOUND_TEMPLATE = "Not find a query class %s for domain class %s!";
    private static final String NO_FIELD_FOUND_TEMPLATE = "Not find a static field of the same type in %s!";
    private static final Logger log = LoggerFactory.getLogger(AbstractBaseDao.class);

    public AbstractBaseDao(DataSource dataSource) {
        this(QueryDslConfig.getInstance(dataSource));
    }

    protected final RelationalPath<T> root;
    protected final List<Path<?>> builderPaths;
    protected final SQLQueryFactory queryFactory;
    protected String primaryKeyField;


    public AbstractBaseDao(QueryDslConfig queryDslConfig) {
        this.queryFactory = queryDslConfig.getSqlQueryFactory();
        Class<T> primaryEntity = (Class<T>) ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.root = createPath(primaryEntity);
        this.primaryKeyField = getPrimaryKey(primaryEntity);
        this.builderPaths = getBuilderPaths(root);
    }

    private String getPrimaryKey(Class<T> primaryEntity) {
        Field[] fields = primaryEntity.getDeclaredFields();
        Field primaryField = null;
        Optional<Field> any = Arrays.stream(fields).filter(f -> Arrays.stream(f.getDeclaredAnnotations()).filter(a -> a instanceof PrimaryKey).findAny().isPresent()).findAny();
        if (any.isPresent()) {
            primaryField = any.get();
        }
        return primaryField.getName();
    }

    @Override
    public ID insert(T record) {
        if (nonNull(record)) {
            ID id = (ID) queryFactory.insert(root).populate(record).executeWithKey(getProperty(root, primaryKeyField));
            record.setPrimaryKey(id);
            return id;
        }
        return null;
    }

    @Override
    public Integer insert(List<T> records) {
        if (nonNull(records) && !records.isEmpty()) {
            SQLInsertClause insert = queryFactory.insert(root);
            records.forEach(r -> insert.populate(r).addBatch());
            Long execute = insert.execute();
            return execute.intValue();
        }
        return null;
    }

    @Override
    public int updateByPrimaryKey(T record) {
        if (nonNull(record)) {
            Long execute = queryFactory.update(root).populate(record, DefaultMapper.WITH_NULL_BINDINGS).where(((SimpleExpression) getProperty(root, primaryKeyField)).eq(record.getPrimaryKey())).execute();
            return execute.intValue();
        }
        return 0;
    }

    @Override
    public int updateByPrimaryKeySelective(T record) {
        if (nonNull(record)) {
            Long execute = queryFactory.update(root).populate(record).where(((SimpleExpression) getProperty(root, primaryKeyField)).eq(record.getPrimaryKey())).execute();
            return execute.intValue();
        }
        return 0;
    }

    protected SQLUpdateClause updateSelectiveByObject(T record) {
        return queryFactory.update(root).populate(record);
    }

    @Override
    public T selectByPrimaryKey(ID id) {
        T t = queryFactory.select(root).from(root).where(((SimpleExpression) getProperty(root, primaryKeyField)).eq(id)).fetchOne();
        return t;
    }

    @Override
    public int deleteByPrimaryKey(ID id) {
        Long execute = queryFactory.delete(root).where(((SimpleExpression) getProperty(root, primaryKeyField)).eq(id)).execute();
        return execute.intValue();
    }

    @Override
    public int queryByCount(BasePage page) {
        return queryByObjectCount(page);
    }

    @Override
    public List<T> queryByList(BasePage page) {
        OrderSpecifier<?> order;
        Integer pageNum = null;
        Integer pageSize = null;
        if (isNotBlank(page.getOrderBy()) || isNotBlank(page.getSort())) {
            order = toOrder(isNotBlank(page.getOrderBy()) ? page.getOrderBy() : page.getSort(), isNotBlank(page.getOrder()) ? page.getOrder() : Order.DESC.name());
        } else {
            order = toOrder(primaryKeyField, Order.DESC.name());
        }
        if (page.getPager().isPageEnabled()) {
            pageNum = page.getPage();
            pageSize = page.getRows();
        }
        return queryByList(pageNum, pageSize, page, order);
    }

    @Override
    public T findOne(T record) {
        SQLQuery<T> sqlQuery = queryFactory.select(root).from(root);
        if (nonNull(record)) {
            sqlQuery.where(getPredicate(record));
        }
        T t = sqlQuery.fetchOne();
        return t;
    }

    @Override
    public List<T> findAll(T record) {
        SQLQuery<T> sqlQuery = queryFactory.select(root).from(root);
        if (nonNull(record)) {
            sqlQuery.where(getPredicate(record));
        }
        List<T> t = sqlQuery.fetch();
        return t;
    }

    protected OrderSpecifier toOrder(String orderField, String direction) {
        if (isBlank(direction)) {
            direction = "asc";
        }
        Order order = Order.valueOf(direction.toUpperCase());
        if (null == orderField) {
            return new OrderSpecifier(order, getProperty(root, primaryKeyField));
        }
        Path<?> property = getProperty(root, orderField);
        if (null == property) {
            property = getProperty(root, primaryKeyField);
        }
        return new OrderSpecifier(order, property);
    }

    private List<T> queryByList(Integer page, Integer pageSize, Object page1, OrderSpecifier<?>... orders) {
        SQLQuery<T> sqlQuery = queryFactory.select(root).from(root);
        obtainQueryListWhere(sqlQuery, page1);
        sqlQuery.orderBy(orders);
        if (nonNull(page)) {
            sqlQuery.offset(getOffset(page, pageSize)).limit(pageSize);
        }
        List<T> fetch = sqlQuery.fetch();
        return fetch;
    }

    protected Long getOffset(Integer page, Integer pageSize) {
        return new Integer((page - 1) * pageSize).longValue();
    }

    private int queryByObjectCount(Object page) {
        SQLQuery<Integer> sqlQuery = queryFactory.select(Expressions.constant(1)).from(root);
        obtainQueryListWhere(sqlQuery, page);
        Long l = sqlQuery.fetchCount();
        return l.intValue();
    }

    private void obtainQueryListWhere(SQLQuery<?> sqlQuery, Object page) {
        if (nonNull(page)) {
            if (nonNull(page)) {
                Field[] fields = page.getClass().getDeclaredFields();
                Optional<Field> operator = Arrays.stream(fields).filter(f -> f.getName().contains("Operator")).findAny();
                if (operator.isPresent()) {
                    sqlQuery.where(getPagePredicate(page));
                } else {
                    sqlQuery.where(getPredicate(page));
                }
            }
        }
    }

    public BooleanBuilder getPagePredicate(final Object entity) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        Class entityClass1 = entity.getClass();
        Field[] fields = entityClass1.getDeclaredFields();
        for (Path<?> builderPath : builderPaths) {
            Optional<Field> any = Arrays.stream(fields).filter(f -> f.getName().equals(builderPath.getMetadata().getName())).findAny();
            if (any.isPresent()) {
                Object value = null;
                Field field = any.get();
                try {
                    field.setAccessible(true);
                    value = field.get(entity);
                } catch (IllegalArgumentException
                        | IllegalAccessException e) {
                    log.error("get field fail.", e);
                }
                if (null != value && !"".equals(String.valueOf(value))) {
                    value = convert(value, field.getType());
                    Path<?> property = getProperty(root, field.getName());
                    if (null != property
                            && property instanceof SimpleExpression) {
                        SimpleExpression simpleExpression = (SimpleExpression) property;
                        Optional<Field> operatorFieldOptional = Arrays.stream(fields).filter(f -> f.getName().equals(field.getName() + "Operator")).findAny();
                        if (value instanceof Collection || value.getClass().isArray()) {
                            booleanBuilder.and(simpleExpression.in(value));
                        } else {
                            if (operatorFieldOptional.isPresent()) {
                                Field operatorField = operatorFieldOptional.get();
                                operatorField.setAccessible(true);
                                Object operatorValue = null;
                                try {
                                    operatorValue = operatorField.get(entity);
                                } catch (IllegalArgumentException
                                        | IllegalAccessException e) {
                                    log.error("get field fail.", e);
                                }
                                if (null != operatorValue) {
                                    switch (String.valueOf(operatorValue)) {
                                        case "like":
                                            StringPath path = getStringProperty(root, field.getName());
                                            if (null != path) {
                                                booleanBuilder.and(path.like(String.format("%%%s%%", String.valueOf(value))));
                                            }
                                            break;
                                        case "<>":
                                        case "!=":
                                            obtainSimpleNEWhere(booleanBuilder, simpleExpression, value);
                                            break;
                                        default:
                                            obtainSimpleEQWhere(booleanBuilder, simpleExpression, value);
                                            break;
                                    }
                                }
                            } else {
                                obtainSimpleEQWhere(booleanBuilder, simpleExpression, value);
                            }
                        }
                    }
                }
            }
        }
        obtainTimeWhere(booleanBuilder, entity);
        return booleanBuilder;
    }

    private void obtainSimpleEQWhere(BooleanBuilder booleanBuilder, SimpleExpression simpleExpression, Object value) {
        if ("null".equalsIgnoreCase(String.valueOf(value))) {
            booleanBuilder.and(simpleExpression.isNull());
            return;
        }
        String simpleName = simpleExpression.getType().getSimpleName();
        if ("String".equals(simpleName)) {
            booleanBuilder.and(simpleExpression.eq(String.valueOf(value)));
        } else if ("Integer".equals(simpleName)) {
            booleanBuilder.and(simpleExpression.eq(Integer.parseInt(String.valueOf(value))));
        } else {
            booleanBuilder.and(simpleExpression.eq(value));
        }
    }

    private void obtainSimpleNEWhere(BooleanBuilder booleanBuilder, SimpleExpression simpleExpression, Object value) {
        if ("null".equalsIgnoreCase(String.valueOf(value))) {
            booleanBuilder.and(simpleExpression.isNotNull());
            return;
        }
        String simpleName = simpleExpression.getType().getSimpleName();
        if ("String".equals(simpleName)) {
            booleanBuilder.and(simpleExpression.ne(String.valueOf(value)));
        } else if ("Integer".equals(simpleName)) {
            booleanBuilder.and(simpleExpression.ne(Integer.parseInt(String.valueOf(value))));
        } else {
            booleanBuilder.and(simpleExpression.ne(value));
        }
    }

    private void obtainTimeWhere(BooleanBuilder booleanBuilder, final Object entity) {
        Field[] fields = entity.getClass().getDeclaredFields();
        Optional<Field> createTime1 = Arrays.stream(fields).filter(f -> f.getName().equals("createTime1")).findAny();
        Optional<Field> createTime2 = Arrays.stream(fields).filter(f -> f.getName().equals("createTime2")).findAny();
        Optional<Field> updateTime1 = Arrays.stream(fields).filter(f -> f.getName().equals("updateTime1")).findAny();
        Optional<Field> updateTime2 = Arrays.stream(fields).filter(f -> f.getName().equals("updateTime2")).findAny();
        if (createTime1.isPresent()) {
            obtainAfterTimeWhere(booleanBuilder, "createTime", transferStringField(createTime1.get(), entity));
        }
        if (createTime2.isPresent()) {
            obtainBeforeTimeWhere(booleanBuilder, "createTime", transferStringField(createTime2.get(), entity));
        }
        if (updateTime1.isPresent()) {
            obtainAfterTimeWhere(booleanBuilder, "updateTime", transferStringField(updateTime1.get(), entity));
        }
        if (updateTime2.isPresent()) {
            obtainBeforeTimeWhere(booleanBuilder, "updateTime", transferStringField(updateTime2.get(), entity));
        }
    }

    private DateTime transferStringField(Field field, final Object entity) {
        try {
            field.setAccessible(true);
            String value = (String) field.get(entity);
            DateTime dateTime = null;
            if (null != value) {
                dateTime = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").parseDateTime(value);
            }
            return dateTime;
        } catch (IllegalArgumentException
                | IllegalAccessException e) {
            log.error("get field fail.", e);
        }
        return null;
    }

    private void obtainBeforeTimeWhere(BooleanBuilder booleanBuilder, String entityField, DateTime time) {
        if( null == time ){
            return;
        }
        DateTime beforeTime = time.withTime(new LocalTime(23, 59, 59, 999));
        obtainCommonTimeWhere(booleanBuilder, entityField, beforeTime, "BEFORE");
    }

    private void obtainAfterTimeWhere(BooleanBuilder booleanBuilder, String entityField, DateTime time) {
        if( null == time ){
            return;
        }
        DateTime afterTime = time.withMillisOfDay(0);
        obtainCommonTimeWhere(booleanBuilder, entityField, afterTime, "AFTER");
    }

    private void obtainCommonTimeWhere(BooleanBuilder booleanBuilder, String entityField, DateTime time, String oper) {
        DateTimePath dateTimeProperty = getDateTimeProperty(root, entityField);
        if (nonNull(dateTimeProperty) && nonNull(time)) {
            if ("BEFORE".equalsIgnoreCase(oper)) {
                booleanBuilder.and(dateTimeProperty.before(time));
            } else if ("AFTER".equalsIgnoreCase(oper)) {
                booleanBuilder.and(dateTimeProperty.after(time));
            } else {
                booleanBuilder.and(dateTimeProperty.eq(time));
            }
        }
    }

    public Path<?> getProperty(RelationalPath<?> root, String property) {
        Class<? extends Object> pathClass = root.getClass();
        try {
            Field field = pathClass.getDeclaredField(property);
            return (Path<?>) field.get(root);
        } catch (NoSuchFieldException | SecurityException
                | IllegalArgumentException | IllegalAccessException e) {
            return null;
        }
    }

    public StringPath getStringProperty(RelationalPath<?> root, String property) {
        Class<? extends Object> pathClass = root.getClass();
        try {
            Field field = pathClass.getDeclaredField(property);
            return (StringPath) field.get(root);
        } catch (NoSuchFieldException | SecurityException
                | IllegalArgumentException | IllegalAccessException e) {
            return null;
        }
    }

    public DateTimePath getDateTimeProperty(RelationalPath<?> root, String property) {
        Class<? extends Object> pathClass = root.getClass();
        try {
            Field field = pathClass.getDeclaredField(property);
            return (DateTimePath) field.get(root);
        } catch (NoSuchFieldException | SecurityException
                | IllegalArgumentException | IllegalAccessException e) {
            return null;
        }
    }

    public BooleanBuilder getPredicate(final Object entity) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        Class entityClass1 = entity.getClass();
        Field[] fields = entityClass1.getDeclaredFields();
        for (Field field : fields) {
            if (!Modifier.isFinal(field.getModifiers())
                    && !Modifier.isStatic(field.getModifiers())) {
                Object value = null;
                try {
                    field.setAccessible(true);
                    value = field.get(entity);
                } catch (IllegalArgumentException
                        | IllegalAccessException e) {
                    log.error("get field fail.", e);
                }
                if (null != value) {
                    value = convert(value, field.getType());
                    Path<?> property = getProperty(root, field.getName());
                    if (null != property
                            && property instanceof SimpleExpression) {
                        SimpleExpression simpleExpression = (SimpleExpression) property;
                        if (value instanceof Collection || value.getClass().isArray()) {
                            booleanBuilder.and(simpleExpression.in(value));
                        } else {
                            obtainSimpleEQWhere(booleanBuilder, simpleExpression, value);
                        }
                    }

                }
            }

        }
        obtainTimeWhere(booleanBuilder, entity);
        return booleanBuilder;
    }

    public <F> F convert(Object propertyValue, Class<F> destType) {
        if (null == propertyValue) {
            return null;
        }
        Class<?> sourceType = propertyValue.getClass();
        // 转换枚举值
        if (sourceType.isEnum()) {
            Object[] enumConstants = sourceType.getEnumConstants();
            for (int i = 0; i < enumConstants.length; i++) {
                if (propertyValue.equals(enumConstants[i])) {
                    if (destType.equals(Integer.class)) {
                        propertyValue = Integer.valueOf(i);
                    } else {
                        propertyValue = ((Enum<?>) enumConstants[i]).name();
                    }
                    return (F) propertyValue;
                }
            }
        }
        // 转换成枚举值
        else if (destType.isEnum()) {
            Object[] enumConstants = destType.getEnumConstants();
            for (int i = 0; i < enumConstants.length; i++) {
                if (sourceType.equals(Integer.class)) {
                    if (propertyValue.equals(i)) {
                        propertyValue = enumConstants[i];
                        return (F) propertyValue;
                    }
                } else {
                    if (propertyValue.equals(((Enum<?>) enumConstants[i]).name())) {
                        propertyValue = enumConstants[i];
                        return (F) propertyValue;
                    }
                }

            }
        }
//        propertyValue = ConvertUtils.convert(propertyValue, destType);
        BeanUtils.copyProperties(propertyValue, destType);
        return (F) propertyValue;
    }


    public RelationalPath<T> createPath(Class<T> entityClass) {
        String pathClassName = getQueryClassName(entityClass);
        try {
            Class<?> pathClass = ClassUtils.forName(pathClassName,
                    entityClass.getClassLoader());
            Field field = getStaticFieldOfType(pathClass);
            if (field == null) {
                throw new IllegalStateException(String.format(NO_FIELD_FOUND_TEMPLATE, pathClass));
            } else {
                return (RelationalPath<T>) ReflectionUtils.getField(field, null);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format(NO_CLASS_FOUND_TEMPLATE, pathClassName, entityClass.getName()), e);
        }
    }

    private static Field getStaticFieldOfType(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            boolean isStatic = Modifier.isStatic(field.getModifiers());
            boolean hasSameType = type.equals(field.getType());
            if (isStatic && hasSameType) {
                return field;
            }
        }
        Class<?> superclass = type.getSuperclass();
        return Object.class.equals(superclass) ? null
                : getStaticFieldOfType(superclass);
    }

    private static String getQueryClassName(Class<?> entityClass) {
        String packageName = entityClass.getPackage().getName();
        packageName = packageName + ".query";
        return String.format("%s.Q%s", packageName, entityClass.getSimpleName());
    }


    private List<Path<?>> getBuilderPaths(RelationalPath<T> root) {
        List<Path<?>> columns = root.getColumns();
        return columns;
    }
}
