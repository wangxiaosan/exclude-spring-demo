package com.talkingdata.ecommerce.entity.query;

import com.querydsl.core.types.Path;
import com.querydsl.core.types.PathMetadata;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.ColumnMetadata;
import com.talkingdata.ecommerce.entity.TestDemo;

import javax.annotation.Generated;
import java.sql.Types;

import static com.querydsl.core.types.PathMetadataFactory.forVariable;


/**
 * QTestDemo is a Querydsl query type for TestDemo
 */
@Generated("com.querydsl.sql.codegen.MetaDataSerializer")
public class QTestDemo extends com.querydsl.sql.RelationalPathBase<TestDemo> {

    private static final long serialVersionUID = 999439142;

    public static final QTestDemo testDemo = new QTestDemo("test_demo");

    public final NumberPath<Integer> id = createNumber("id", Integer.class);

    public final StringPath name = createString("name");

    public final com.querydsl.sql.PrimaryKey<TestDemo> primary = createPrimaryKey(id);

    public QTestDemo(String variable) {
        super(TestDemo.class, forVariable(variable), "null", "test_demo");
        addMetadata();
    }

    public QTestDemo(String variable, String schema, String table) {
        super(TestDemo.class, forVariable(variable), schema, table);
        addMetadata();
    }

    public QTestDemo(String variable, String schema) {
        super(TestDemo.class, forVariable(variable), schema, "test_demo");
        addMetadata();
    }

    public QTestDemo(Path<? extends TestDemo> path) {
        super(path.getType(), path.getMetadata(), "null", "test_demo");
        addMetadata();
    }

    public QTestDemo(PathMetadata metadata) {
        super(TestDemo.class, metadata, "null", "test_demo");
        addMetadata();
    }

    public void addMetadata() {
        addMetadata(id, ColumnMetadata.named("id").withIndex(1).ofType(Types.INTEGER).withSize(10).notNull());
        addMetadata(name, ColumnMetadata.named("name").withIndex(2).ofType(Types.VARCHAR).withSize(45));
    }

}

