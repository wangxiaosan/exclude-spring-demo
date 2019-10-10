package com.tailkingdata.ecommerce;

import com.querydsl.codegen.JavaTypeMappings;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.SQLTemplates;
import com.querydsl.sql.codegen.MetaDataSerializer;
import com.querydsl.sql.types.DateTimeType;
import com.querydsl.sql.types.LocalDateType;
import com.talkingdata.ecommerce.support.querydsl.codegen.SimpleBeanSerializer;
import com.talkingdata.ecommerce.support.querydsl.codegen.SimpleDaoIfcSerializer;
import com.talkingdata.ecommerce.support.querydsl.codegen.SimpleDaoImplSerializer;
import com.talkingdata.ecommerce.support.querydsl.codegen.SimpleMetaDataExporter;
import org.junit.Test;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;

/**
 * @author wwy
 * @date 2019/7/4
 */
public class DomainCodeGenTest {

    @Inject
    DataSource dataSource;

    @Test
    public void generateCode() throws Exception {
        Connection connection = dataSource.getConnection();

        SimpleMetaDataExporter exporter = new SimpleMetaDataExporter();
        exporter.setExportPrimaryKeys(true);
        exporter.setNamePrefix("Q");
        exporter.setBasePackageName("com.tailkingdata.ecommerce");
        exporter.setSchemaPattern("PUBLIC");
        exporter.setTargetFolder(new File("src/main/java"));
        exporter.setSerializerClass(MetaDataSerializer.class);
//        exporter.setDatasource("mysql");
        SimpleBeanSerializer beanSerializer = new SimpleBeanSerializer();
        exporter.setBeanSerializerClass(beanSerializer.getClass());
        // 如果不生成dao接口, 不要加daoIfcSerializer
        SimpleDaoIfcSerializer daoIfcSerializer = new SimpleDaoIfcSerializer();
        exporter.setDaoIfcSerializer(daoIfcSerializer);
        // --------------
        // 如果不生成dao实现, 不要加daoImplSerializer
        SimpleDaoImplSerializer daoImplSerializer = new SimpleDaoImplSerializer();
        exporter.setDaoImplSerializer(daoImplSerializer);
        // ---------------
        // 覆盖现有的类.
//        exporter.setCover(true);
        exporter.setTypeMappings(new JavaTypeMappings());
        Configuration configuration = new Configuration(SQLTemplates.DEFAULT);
        configuration.registerType("tinyint", Integer.class);
        configuration.register(new LocalDateType());
        configuration.register(new DateTimeType());
        exporter.setConfiguration(configuration);
        exporter.setSchemaPattern("local");
        //        exporter.setTableNamePattern("xxx_%");
        exporter.export(connection.getMetaData());
    }

}
