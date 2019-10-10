package com.talkingdata.ecommerce.support.querydsl.common;

import com.querydsl.core.QueryMetadata;
import com.querydsl.core.support.QueryBase;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.SubQueryExpression;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.MySQLTemplates;
import com.querydsl.sql.OracleTemplates;
import com.querydsl.sql.PostgreSQLTemplates;
import com.querydsl.sql.RelationalPath;
import com.querydsl.sql.SQLListener;
import com.querydsl.sql.SQLQueryFactory;
import com.querydsl.sql.SQLTemplates;
import com.querydsl.sql.dml.SQLInsertBatch;
import com.querydsl.sql.dml.SQLMergeBatch;
import com.querydsl.sql.dml.SQLUpdateBatch;
import com.querydsl.sql.types.DateTimeType;
import com.querydsl.sql.types.LocalDateType;
import com.querydsl.sql.types.LocalTimeType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.MarkerFactory;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author wwy
 * @date 2019/7/17
 */
public class QueryDslConfig {

    final Logger log = LoggerFactory.getLogger(QueryDslConfig.class);
    private final DataSource dataSource;
    private final Configuration configuration;

    private SQLQueryFactory sqlQueryFactory;

    private static volatile QueryDslConfig instance = null;

    public static QueryDslConfig getInstance(DataSource dataSource) {
        if (instance == null) {
            synchronized (QueryDslConfig.class) {
                if (instance == null) {
                    instance = new QueryDslConfig(dataSource);
                }
            }
        }
        return instance;
    }

    private QueryDslConfig(DataSource dataSource) {
        this.dataSource = dataSource;
        String databaseType = new PlatformUtils().determineDatabaseType(dataSource);
        SQLTemplates templates;
        switch (databaseType) {
            case "PostgreSql":
                templates = new PostgreSQLTemplates();
                break;
            case "Oracle":
                templates = new OracleTemplates();
                break;
            default:
                templates = new MySQLTemplates();
                break;
        }

        configuration = new Configuration(templates);
//        configuration.setExceptionTranslator(new SpringExceptionTranslator());
        configuration.register(new DateTimeType());
        configuration.register(new LocalDateType());
        configuration.register(new LocalTimeType());
//        Provider<Connection> provider = new SpringConnectionProvider(dataSource);
        sqlQueryFactory = new SQLQueryFactory(configuration, dataSource);
        configuration.addListener(new SQLListener() {
            @Override
            public void notifyQuery(QueryMetadata queryMetadata) {
                printSQL();
            }

            @Override
            public void notifyDelete(RelationalPath<?> relationalPath, QueryMetadata queryMetadata) {
                printSQL();
            }

            @Override
            public void notifyDeletes(RelationalPath<?> relationalPath, List<QueryMetadata> list) {
                printSQL();
            }

            @Override
            public void notifyMerge(RelationalPath<?> relationalPath, QueryMetadata queryMetadata, List<Path<?>> list,
                                    List<Path<?>> list1, List<Expression<?>> list2, SubQueryExpression<?> subQueryExpression) {
                printSQL();
            }

            @Override
            public void notifyMerges(RelationalPath<?> relationalPath, QueryMetadata queryMetadata,
                List<SQLMergeBatch> list) {
                printSQL();
            }

            @Override
            public void notifyInsert(RelationalPath<?> relationalPath, QueryMetadata queryMetadata, List<Path<?>> list,
                                     List<Expression<?>> list1, SubQueryExpression<?> subQueryExpression) {
                printSQL();
            }

            @Override
            public void notifyInserts(RelationalPath<?> relationalPath, QueryMetadata queryMetadata,
                List<SQLInsertBatch> list) {
                printSQL();
            }

            @Override
            public void notifyUpdate(RelationalPath<?> relationalPath, QueryMetadata queryMetadata,
                Map<Path<?>, Expression<?>> map) {
                printSQL();
            }

            @Override
            public void notifyUpdates(RelationalPath<?> relationalPath, List<SQLUpdateBatch> list) {
                printSQL();
            }
        });
    }

    private void printSQL() {
        String sql = MDC.get(QueryBase.MDC_QUERY);
        if (Objects.nonNull(sql)) {
            String params = MDC.get(QueryBase.MDC_PARAMETERS);
            if (Objects.nonNull(params)) {
                params = params.substring(1, params.length() - 1);
                String[] split = params.split(",");
                sql = sql.replace("?", "%s");
                Object[] objs = new Object[split.length];
                for (int i = 0; i < split.length; i++) {
                    String cs = split[i].trim();
                    if (StringUtils.isNumeric(cs) || "true".equals(cs) || "false".equals(cs)) {
                        objs[i] = cs;
                    } else if (cs.startsWith("com.querydsl.sql.types.Null")) {
                        objs[i] = "''";
                    } else {
                        objs[i] = "'" + cs + "'";
                    }
                }
                sql = String.format(sql, objs);
            }
            log.debug(MarkerFactory.getMarker("database"), "=====> Perform database operation : " + sql);
        }
    }

    public SQLQueryFactory getSqlQueryFactory() {
        return sqlQueryFactory;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public Configuration getConfiguration() {
        return configuration;
    }
}
