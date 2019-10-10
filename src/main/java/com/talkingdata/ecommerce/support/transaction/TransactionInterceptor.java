package com.talkingdata.ecommerce.support.transaction;

import com.talkingdata.ecommerce.support.querydsl.common.ConnectionContext;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import javax.inject.Inject;
import java.lang.reflect.Method;
import java.sql.Connection;

/**
 * @author wwy
 * @date 2019-08-23
 */
@Slf4j
public class TransactionInterceptor implements MethodInterceptor {
    @Inject
    private ConnectionContext context;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Transactional annotation = method.getAnnotation(Transactional.class);
        if (annotation == null) {
            annotation = method.getDeclaringClass().getAnnotation(Transactional.class);
        }
        if (annotation == null || context.getConnection() != null) {
            return invocation.proceed();
        }
        Connection connection = context.getConnection(true);
        connection.setAutoCommit(false);
        try {
            Object rv = invocation.proceed();
            connection.commit();
            return rv;
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.close();
            context.removeConnection();
        }
    }
}
