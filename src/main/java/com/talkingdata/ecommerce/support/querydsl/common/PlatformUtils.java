package com.talkingdata.ecommerce.support.querydsl.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashMap;

/**
 * @author wwy
 * @date 2019/7/17
 */
public class PlatformUtils {

    private HashMap<String, String> jdbcDriver = new HashMap();

    private static final Logger log = LoggerFactory.getLogger(PlatformUtils.class);

    public PlatformUtils() {
        this.jdbcDriver.put("PostgreSQL JDBC Driver", "PostgreSql");
        this.jdbcDriver.put("MySQL Connector Java", "MySQL");
        this.jdbcDriver.put("MySQL-AB JDBC Driver", "MySQL");
        this.jdbcDriver.put("MySQL Connector/J", "MySQL");
        this.jdbcDriver.put("Apache Derby Network Client JDBC Driver", "Derby");
    }

    public String determineDatabaseType(DataSource dataSource) {
        Connection connection = null;
        String var6 = null;
        try {
            connection = dataSource.getConnection();
            DatabaseMetaData ex = connection.getMetaData();
            var6 = this.determineDatabaseType(ex.getDriverName());
        } catch (SQLException e) {
            log.error("get sqlTemplate fail.", e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException var14) {
                    log.error("connection close fail.");
                }
            }

        }
        return var6;
    }

    public String determineDatabaseType(String driverName) {
        if (this.jdbcDriver.containsKey(driverName)) {
            return this.jdbcDriver.get(driverName);
        }
        return null;
    }
}
