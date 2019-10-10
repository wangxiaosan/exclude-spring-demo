package com.talkingdata.ecommerce.support.querydsl.codegen;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.mysema.codegen.CodeWriter;
import com.mysema.codegen.JavaWriter;
import com.mysema.codegen.ScalaWriter;
import com.mysema.codegen.model.ClassType;
import com.mysema.codegen.model.SimpleType;
import com.mysema.codegen.model.Type;
import com.mysema.codegen.model.TypeCategory;
import com.querydsl.codegen.CodegenModule;
import com.querydsl.codegen.EntityType;
import com.querydsl.codegen.Property;
import com.querydsl.codegen.QueryTypeFactory;
import com.querydsl.codegen.Serializer;
import com.querydsl.codegen.SimpleSerializerConfig;
import com.querydsl.codegen.Supertype;
import com.querydsl.codegen.TypeMappings;
import com.querydsl.sql.ColumnImpl;
import com.querydsl.sql.ColumnMetadata;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.SQLTemplates;
import com.querydsl.sql.SQLTemplatesRegistry;
import com.querydsl.sql.SchemaAndTable;
import com.querydsl.sql.codegen.KeyDataFactory;
import com.querydsl.sql.codegen.MetaDataExporter;
import com.querydsl.sql.codegen.NamingStrategy;
import com.querydsl.sql.codegen.SQLCodegenModule;
import com.querydsl.sql.codegen.SpatialSupport;
import com.querydsl.sql.codegen.support.ForeignKeyData;
import com.querydsl.sql.codegen.support.InverseForeignKeyData;
import com.querydsl.sql.codegen.support.NotNullImpl;
import com.querydsl.sql.codegen.support.PrimaryKeyData;
import com.querydsl.sql.codegen.support.SizeImpl;
import com.talkingdata.ecommerce.support.querydsl.base.AbstractBaseDao;
import com.talkingdata.ecommerce.support.querydsl.base.BaseDao;
import com.talkingdata.ecommerce.support.querydsl.common.PrimaryEntity;
import com.talkingdata.ecommerce.support.querydsl.common.PrimaryKeyImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.nonNull;

/**
 * @author wwy
 * @date 2019/7/8
 */
public class SimpleMetaDataExporter {
    private static final Logger logger = LoggerFactory.getLogger(MetaDataExporter.class);

    private final SQLTemplatesRegistry sqlTemplatesRegistry = new SQLTemplatesRegistry();

    private final SQLCodegenModule module = new SQLCodegenModule();

    private final Set<String> classes = new HashSet<String>();

    private File targetFolder;

    private File beansTargetFolder;

//    @Nullable
//    private String beanPackageName;

    @Nullable
    private String basePackageName;

    @Nullable
    private String schemaPattern, tableNamePattern;

    @Nullable
    private Serializer beanSerializer;

    @Nullable
    private Serializer daoIfcSerializer;

    @Nullable
    private Serializer daoImplSerializer;

    private boolean createScalaSources = false;

    private final Map<EntityType, Type> entityToWrapped = new HashMap<EntityType, Type>();

    private Serializer serializer;

    private TypeMappings typeMappings;

    private QueryTypeFactory queryTypeFactory;

    private NamingStrategy namingStrategy;

    private Configuration configuration;

    private KeyDataFactory keyDataFactory;

    private boolean columnAnnotations = false;

    private boolean validationAnnotations = false;

    private boolean primaryKeyAnnotations = true;

    private boolean dateTimeAnnotations = true;

    private boolean schemaToPackage = false;

    private String sourceEncoding = "UTF-8";

    private boolean lowerCase = false;

    private boolean exportTables = true;

    private boolean exportViews = true;

    private boolean exportAll = false;

    private boolean exportPrimaryKeys = true;

    private boolean exportForeignKeys = true;

    private boolean exportDirectForeignKeys = true;

    private boolean exportInverseForeignKeys = true;

    private boolean spatial = false;

    /**
     * 是否覆盖.
     */
    private boolean cover = false;

    @Nullable
    private String datasource;

    @Nullable
    private String tableTypesToExport;

    public SimpleMetaDataExporter() {
    }

    protected EntityType createEntityType(SchemaAndTable schemaAndTable,
                                          final String className) {
        EntityType classModel;

        if (beanSerializer == null) {
            String packageName = normalizePackage(module.getPackageName(), schemaAndTable);
            String simpleName = module.getPrefix() + className + module.getSuffix();
            Type classTypeModel = new SimpleType(TypeCategory.ENTITY,
                    packageName + "." + simpleName, packageName, simpleName, false, false);
            classModel = new EntityType(classTypeModel, module.get(Function.class, CodegenModule.VARIABLE_NAME_FUNCTION_CLASS));
            typeMappings.register(classModel, classModel);

        } else {
            String beanPackageName = basePackageName + ".entity";
            String beanPackage = normalizePackage(beanPackageName, schemaAndTable);
            String simpleName = module.getBeanPrefix() + className + module.getBeanSuffix();
            Type classTypeModel = new SimpleType(TypeCategory.ENTITY,
                    beanPackage + "." + simpleName, beanPackage, simpleName, false, false);
            classModel = new EntityType(classTypeModel, module.get(Function.class, CodegenModule.VARIABLE_NAME_FUNCTION_CLASS));

            Type mappedType = queryTypeFactory.create(classModel);
            entityToWrapped.put(classModel, mappedType);
            typeMappings.register(classModel, mappedType);
        }

        classModel.getData().put("schema", schemaAndTable.getSchema());
        classModel.getData().put("table", schemaAndTable.getTable());
        return classModel;
    }


    private String normalizePackage(String packageName, SchemaAndTable schemaAndTable) {
        String rval = packageName;
        if (schemaToPackage) {
            rval = namingStrategy.getPackage(rval, schemaAndTable);
        }
        return rval;
    }

    protected Property createProperty(EntityType classModel, String normalizedColumnName,
                                      String propertyName, Type typeModel) {
        return new Property(
                classModel,
                propertyName,
                propertyName,
                typeModel,
                Collections.<String>emptyList(),
                false);
    }

    /**
     * Export the tables based on the given database metadata
     *
     * @param md database metadata
     * @throws SQLException
     */
    public void export(DatabaseMetaData md) throws SQLException {
        if (basePackageName == null) {
            basePackageName = module.getPackageName();
        }
        if (beansTargetFolder == null) {
            beansTargetFolder = targetFolder;
        }
        String beanPackageName = basePackageName + ".entity";
        module.bind(SQLCodegenModule.PACKAGE_NAME, beanPackageName + ".query");
        module.bind(SQLCodegenModule.BEAN_PACKAGE_NAME, beanPackageName);

        if (spatial) {
            SpatialSupport.addSupport(module);
        }

        classes.clear();
        typeMappings = module.get(TypeMappings.class);
        queryTypeFactory = module.get(QueryTypeFactory.class);
        serializer = module.get(Serializer.class);
        beanSerializer = module.get(Serializer.class, SQLCodegenModule.BEAN_SERIALIZER);
        namingStrategy = module.get(NamingStrategy.class);
        configuration = module.get(Configuration.class);

        SQLTemplates templates = sqlTemplatesRegistry.getTemplates(md);
        if (templates != null) {
            configuration.setTemplates(templates);
        } else {
            logger.info("Found no specific dialect for " + md.getDatabaseProductName());
        }

        if (beanSerializer == null) {
            keyDataFactory = new KeyDataFactory(namingStrategy, module.getPackageName(),
                    module.getPrefix(), module.getSuffix(), schemaToPackage);
        } else {
            keyDataFactory = new KeyDataFactory(namingStrategy, beanPackageName,
                    module.getBeanPrefix(), module.getBeanSuffix(), schemaToPackage);
        }

        String[] typesArray = null;

        if (tableTypesToExport != null && !tableTypesToExport.isEmpty()) {
            List<String> types = new ArrayList<String>();
            for (String tableType : tableTypesToExport.split(",")) {
                types.add(tableType.trim());
            }
            typesArray = types.toArray(new String[types.size()]);
        } else if (!exportAll) {
            List<String> types = new ArrayList<String>(2);
            if (exportTables) {
                types.add("TABLE");
            }
            if (exportViews) {
                types.add("VIEW");
            }
            typesArray = types.toArray(new String[types.size()]);
        }

        List<String> schemas = Arrays.asList(schemaPattern);
        if (schemaPattern != null && schemaPattern.contains(",")) {
            schemas = ImmutableList.copyOf(schemaPattern.split(","));
        }
        List<String> tables = Arrays.asList(tableNamePattern);
        if (tableNamePattern != null && tableNamePattern.contains(",")) {
            tables = ImmutableList.copyOf(tableNamePattern.split(","));
        }

        for (String schema : schemas) {
            schema = schema != null ? schema.trim() : null;
            for (String table : tables) {
                table = table != null ? table.trim() : null;
                handleTables(md, schema, table, typesArray);
            }
        }
    }

    private void handleTables(DatabaseMetaData md, String schemaPattern, String tablePattern, String[] types) throws SQLException {
        ResultSet tables;
        if (null != datasource) {
            if (datasource.equalsIgnoreCase("mysql")) {
                tables = md.getTables(schemaPattern, schemaPattern, tablePattern, types);
            } else if (datasource.equalsIgnoreCase("pgSql")) {
                tables = md.getTables(schemaPattern, null, tablePattern, types);
            } else {
                tables = md.getTables(schemaPattern, schemaPattern, tablePattern, types);
            }
        } else {
            tables = md.getTables(schemaPattern, schemaPattern, tablePattern, types);
        }
        try {
            while (tables.next()) {
                handleTable(md, tables);
            }
        } finally {
            tables.close();
        }
    }

    Set<String> getClasses() {
        return classes;
    }

    private void handleColumn(EntityType classModel, String tableName, ResultSet columns) throws SQLException {
        String columnName = normalize(columns.getString("COLUMN_NAME"));
        String normalizedColumnName = namingStrategy.normalizeColumnName(columnName);
        int columnType = columns.getInt("DATA_TYPE");
        String typeName = columns.getString("TYPE_NAME");
        Number columnSize = (Number) columns.getObject("COLUMN_SIZE");
        Number columnDigits = (Number) columns.getObject("DECIMAL_DIGITS");
        int columnIndex = columns.getInt("ORDINAL_POSITION");
        int nullable = columns.getInt("NULLABLE");

        String propertyName = namingStrategy.getPropertyName(normalizedColumnName, classModel);
        Class<?> clazz = configuration.getJavaType(columnType,
                typeName,
                columnSize != null ? columnSize.intValue() : 0,
                columnDigits != null ? columnDigits.intValue() : 0,
                tableName, columnName);
        if (clazz == null) {
            clazz = Object.class;
        }
        TypeCategory fieldType = TypeCategory.get(clazz.getName());
        if (Number.class.isAssignableFrom(clazz)) {
            fieldType = TypeCategory.NUMERIC;
        } else if (Enum.class.isAssignableFrom(clazz)) {
            fieldType = TypeCategory.ENUM;
        }
        Type typeModel = new ClassType(fieldType, clazz);
        Property property = createProperty(classModel, normalizedColumnName, propertyName, typeModel);
        ColumnMetadata column = ColumnMetadata.named(normalizedColumnName).ofType(columnType).withIndex(columnIndex);
        if (nullable == DatabaseMetaData.columnNoNulls) {
            column = column.notNull();
        }
        if (columnSize != null) {
            column = column.withSize(columnSize.intValue());
        }
        if (columnDigits != null) {
            column = column.withDigits(columnDigits.intValue());
        }
        property.getData().put("COLUMN", column);

        if (columnAnnotations) {
            property.addAnnotation(new ColumnImpl(normalizedColumnName));
        }
        if (validationAnnotations) {
            if (nullable == DatabaseMetaData.columnNoNulls) {
                property.addAnnotation(new NotNullImpl());
            }
            int size = columns.getInt("COLUMN_SIZE");
            if (size > 0 && clazz.equals(String.class)) {
                property.addAnnotation(new SizeImpl(0, size));
            }
        }
//        if (primaryKeyAnnotations) {
//            if (property.getType().getSimpleName().equals("DateTime")) {
//                property.addAnnotation(new DateTimeFormatImpl("yyyy-MM-dd HH:mm:ss"));
//            }
//        }
        if (dateTimeAnnotations) {
            if (classModel.getData().get(PrimaryKeyData.class) != null) {
                ArrayList<PrimaryKeyData> values = Lists.newArrayList((Collection<PrimaryKeyData>) classModel.getData().get(PrimaryKeyData.class));
                String primaryKeyColumn = values.get(0).getColumns().get(0);
                if (columnName.equals(primaryKeyColumn)) {
                    property.addAnnotation(new PrimaryKeyImpl(columnName));
                }
            }
        }

        classModel.addProperty(property);
    }

    private void handleTable(DatabaseMetaData md, ResultSet tables) throws SQLException {
        String catalog = tables.getString("TABLE_CAT");
        String schema = tables.getString("TABLE_SCHEM");
        String schemaName = normalize(tables.getString("TABLE_SCHEM"));
        String tableName = normalize(tables.getString("TABLE_NAME"));

        String normalizedSchemaName = namingStrategy.normalizeSchemaName(schemaName);
        String normalizedTableName = namingStrategy.normalizeTableName(tableName);

        SchemaAndTable schemaAndTable = new SchemaAndTable(
                normalizedSchemaName, normalizedTableName);

        if (!namingStrategy.shouldGenerateClass(schemaAndTable)) {
            return;
        }

        String className = namingStrategy.getClassName(schemaAndTable);
        EntityType classModel = createEntityType(schemaAndTable, className);

        if (exportPrimaryKeys) {
            // collect primary keys
            Map<String, PrimaryKeyData> primaryKeyData = keyDataFactory
                    .getPrimaryKeys(md, catalog, schema, tableName);
            if (!primaryKeyData.isEmpty()) {
                classModel.getData().put(PrimaryKeyData.class, primaryKeyData.values());
            }
        }

        if (exportForeignKeys) {
            if (exportDirectForeignKeys) {
                // collect foreign keys
                Map<String, ForeignKeyData> foreignKeyData = keyDataFactory
                        .getImportedKeys(md, catalog, schema, tableName);
                if (!foreignKeyData.isEmpty()) {
                    Collection<ForeignKeyData> foreignKeysToGenerate = new HashSet<ForeignKeyData>();
                    for (ForeignKeyData fkd : foreignKeyData.values()) {
                        if (namingStrategy.shouldGenerateForeignKey(schemaAndTable, fkd)) {
                            foreignKeysToGenerate.add(fkd);
                        }
                    }

                    if (!foreignKeysToGenerate.isEmpty()) {
                        classModel.getData().put(ForeignKeyData.class, foreignKeysToGenerate);
                    }
                }
            }

            if (exportInverseForeignKeys) {
                // collect inverse foreign keys
                Map<String, InverseForeignKeyData> inverseForeignKeyData = keyDataFactory
                        .getExportedKeys(md, catalog, schema, tableName);
                if (!inverseForeignKeyData.isEmpty()) {
                    classModel.getData().put(InverseForeignKeyData.class, inverseForeignKeyData.values());
                }
            }
        }

        // collect columns
        ResultSet columns = md.getColumns(catalog, schema, tableName.replace("/", "//"), null);
        try {
            while (columns.next()) {
                handleColumn(classModel, tableName, columns);
            }
        } finally {
            columns.close();
        }

        // serialize model
        serialize(classModel, schemaAndTable);

        logger.info("Exported " + tableName + " successfully");
    }

    private String normalize(String str) {
        if (lowerCase && str != null) {
            return str.toLowerCase();
        } else {
            return str;
        }
    }

    private void serialize(EntityType type, SchemaAndTable schemaAndTable) {
        try {
            String fileSuffix = createScalaSources ? ".scala" : ".java";
            String daoPackageName = basePackageName + ".repository";
            String beanPackageName = basePackageName + ".entity";

            if (beanSerializer != null) {
                String packageName = normalizePackage(beanPackageName, schemaAndTable);
                Property primaryKeyProperty = null;
                Set<Property> properties = type.getProperties();
                for (Property p : properties) {
                    if (p.getAnnotations().stream().filter(a ->
                            a.annotationType().getSimpleName().contains("PrimaryKey")).findAny().isPresent()) {
                        primaryKeyProperty = p;
                    }
                }
                Type primaryKeyPropertyType = null;
                Type beanSuperType;
                if (nonNull(primaryKeyProperty)) {
                    primaryKeyPropertyType = primaryKeyProperty.getType();

                    beanSuperType = new ClassType(PrimaryEntity.class,
                            primaryKeyPropertyType);
                    Supertype parentType = new Supertype(beanSuperType);
                    type.addSupertype(parentType);
                }
                String path = packageName.replace('.', '/') + "/" + type.getSimpleName() + fileSuffix;
                write(beanSerializer, path, type);

                String otherPath = entityToWrapped.get(type).getFullName().replace('.', '/') + fileSuffix;
                write(serializer, otherPath, type);

                // 写dao接口
                if (daoIfcSerializer != null && primaryKeyPropertyType != null) {
                    String daoIfcPkgName = normalizePackage(daoPackageName, schemaAndTable);
                    String simpleName = type.getSimpleName() + "Repository";
                    String daoIfcPath = daoIfcPkgName.replace('.', '/') + "/"
                            + simpleName + fileSuffix;

                    Type superType = new ClassType(BaseDao.class, type, primaryKeyPropertyType);
                    Supertype daoParentType = new Supertype(superType);

                    Type classTypeModel = new SimpleType(TypeCategory.ENTITY,
                            daoIfcPkgName + "." + simpleName, daoIfcPkgName,
                            simpleName, false, false);
                    EntityType daoClassModel = new EntityType(classTypeModel);
                    daoClassModel.addSupertype(daoParentType);
                    write(daoIfcSerializer, daoIfcPath, daoClassModel);

                    // 写dao实现类
                    if (daoImplSerializer != null) {
                        String daoImplPkgName = daoIfcPkgName + ".impl";
                        String daoImplSimpleName = simpleName + "Impl";
                        String daoImplPath = daoImplPkgName.replace('.', '/')
                                + "/" + daoImplSimpleName + fileSuffix;

                        Type abstractBaseDaoType = new ClassType(AbstractBaseDao.class, type, primaryKeyPropertyType);
                        Supertype daoImplSuperType = new Supertype(abstractBaseDaoType);

                        Type daoImplClassTypeModel = new SimpleType(
                                TypeCategory.ENTITY, daoImplPkgName + "."
                                + daoImplSimpleName, daoImplPkgName,
                                daoImplSimpleName, false, false);
                        EntityType daoImplClassModel = new EntityType(daoImplClassTypeModel);
                        daoImplClassModel.addSupertype(daoImplSuperType);
                        daoImplClassModel.addSupertype(new Supertype(daoClassModel));
                        write(daoImplSerializer, daoImplPath, daoImplClassModel);
                    }
                }

            } else {
                String packageName = normalizePackage(module.getPackageName(), schemaAndTable);
                String path = packageName.replace('.', '/') + "/" + type.getSimpleName() + fileSuffix;
                write(serializer, path, type);
            }

        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void write(Serializer serializer, String targetPath, EntityType type) throws IOException {
        File targetFile = new File(targetFolder, targetPath);
        if (!classes.add(targetFile.getPath())) {
            throw new IllegalStateException("Attempted to write multiple times to " +
                    targetFile.getPath() + ", please check your configuration");
        }
        StringWriter w = new StringWriter();
        CodeWriter writer = createScalaSources ? new ScalaWriter(w) : new JavaWriter(w);
        serializer.serialize(type, SimpleSerializerConfig.DEFAULT, writer);

        // conditional creation
        boolean generate = true;
        byte[] bytes = w.toString().getBytes(sourceEncoding);
        if (targetFile.exists()) {
            if (cover) {
                if (targetFile.length() == bytes.length) {
                    String str = Files.toString(targetFile, Charset.forName(sourceEncoding));
                    if (str.equals(w.toString())) {
                        generate = false;
                    }
                }
            } else {
                generate = false;
            }
        } else {
            targetFile.getParentFile().mkdirs();
        }
        if (generate) {
            Files.write(bytes, targetFile);
        }
    }


    /**
     * Set the schema pattern filter to be used
     *
     * @param schemaPattern a schema name pattern; must match the schema name
     *                      as it is stored in the database; "" retrieves those without a schema;
     *                      {@code null} means that the schema name should not be used to narrow
     *                      the search (default: null)
     */
    public void setSchemaPattern(@Nullable String schemaPattern) {
        this.schemaPattern = schemaPattern;
    }

    /**
     * Set the table name pattern filter to be used
     *
     * @param tableNamePattern a table name pattern; must match the
     *                         table name as it is stored in the database (default: null)
     */
    public void setTableNamePattern(@Nullable String tableNamePattern) {
        this.tableNamePattern = tableNamePattern;
    }

    /**
     * Override the configuration
     *
     * @param configuration override configuration for custom type mappings etc
     */
    public void setConfiguration(Configuration configuration) {
        module.bind(Configuration.class, configuration);
    }

    /**
     * Set true to create Scala sources instead of Java sources
     *
     * @param createScalaSources whether to create Scala sources (default: false)
     */
    public void setCreateScalaSources(boolean createScalaSources) {
        this.createScalaSources = createScalaSources;
    }

    /**
     * Set the target folder
     *
     * @param targetFolder target source folder to create the sources into
     *                     (e.g. target/generated-sources/java)
     */
    public void setTargetFolder(File targetFolder) {
        this.targetFolder = targetFolder;
    }

    /**
     * Set the target folder for beans
     *
     * <p>defaults to the targetFolder value</p>
     *
     * @param targetFolder target source folder to create the bean sources into
     */
    public void setBeansTargetFolder(File targetFolder) {
        this.beansTargetFolder = targetFolder;
    }

    /**
     * Set the package name
     *
     * @param packageName package name for sources
     */
    public void setPackageName(String packageName) {
        module.bind(SQLCodegenModule.PACKAGE_NAME, packageName);
    }

    /**
     * Override the bean package name (default: packageName)
     */
    public void setBasePackageName(@Nullable String basePackageName) {
        this.basePackageName = basePackageName;
    }

    /**
     * Override the name prefix for the classes (default: Q)
     *
     * @param namePrefix name prefix for querydsl-types (default: Q)
     */
    public void setNamePrefix(String namePrefix) {
        module.bind(CodegenModule.PREFIX, namePrefix);
    }

    /**
     * Override the name suffix for the classes (default: "")
     *
     * @param nameSuffix name suffix for querydsl-types (default: "")
     */
    public void setNameSuffix(String nameSuffix) {
        module.bind(CodegenModule.SUFFIX, nameSuffix);
    }

    /**
     * Override the bean prefix for the classes (default: "")
     *
     * @param beanPrefix bean prefix for bean-types (default: "")
     */
    public void setBeanPrefix(String beanPrefix) {
        module.bind(SQLCodegenModule.BEAN_PREFIX, beanPrefix);
    }

    /**
     * Override the bean suffix for the classes (default: "")
     *
     * @param beanSuffix bean suffix for bean-types (default: "")
     */
    public void setBeanSuffix(String beanSuffix) {
        module.bind(SQLCodegenModule.BEAN_SUFFIX, beanSuffix);
    }

    /**
     * Override the NamingStrategy (default: new DefaultNamingStrategy())
     *
     * @param namingStrategy naming strategy to override (default: new DefaultNamingStrategy())
     */
    public void setNamingStrategy(NamingStrategy namingStrategy) {
        module.bind(NamingStrategy.class, namingStrategy);
    }

    /**
     * Set the Bean serializer to create bean types as well
     *
     * @param beanSerializer serializer for JavaBeans (default: null)
     */
    public void setBeanSerializer(@Nullable Serializer beanSerializer) {
        module.bind(SQLCodegenModule.BEAN_SERIALIZER, beanSerializer);
    }

    /**
     * Set the Bean serializer class to create bean types as well
     *
     * @param beanSerializerClass serializer for JavaBeans (default: null)
     */
    public void setBeanSerializerClass(Class<? extends Serializer> beanSerializerClass) {
        module.bind(SQLCodegenModule.BEAN_SERIALIZER, beanSerializerClass);
    }

    /**
     * Set whether inner classes should be created for keys
     *
     * @param innerClassesForKeys
     */
    public void setInnerClassesForKeys(boolean innerClassesForKeys) {
        module.bind(SQLCodegenModule.INNER_CLASSES_FOR_KEYS, innerClassesForKeys);
    }

    /**
     * Set the column comparator class
     *
     * @param columnComparatorClass
     */
    public void setColumnComparatorClass(Class<? extends Comparator<Property>> columnComparatorClass) {
        module.bind(SQLCodegenModule.COLUMN_COMPARATOR, columnComparatorClass);
    }

    /**
     * Set the serializer class
     *
     * @param serializerClass
     */
    public void setSerializerClass(Class<? extends Serializer> serializerClass) {
        module.bind(Serializer.class, serializerClass);
    }

    /**
     * Set the type mappings to use
     *
     * @param typeMappings
     */
    public void setTypeMappings(TypeMappings typeMappings) {
        module.bind(TypeMappings.class, typeMappings);
    }

    /**
     * Set whether column annotations should be created
     *
     * @param columnAnnotations
     */
    public void setColumnAnnotations(boolean columnAnnotations) {
        this.columnAnnotations = columnAnnotations;
    }

    /**
     * Set whether validation annotations should be created
     *
     * @param validationAnnotations
     */
    public void setValidationAnnotations(boolean validationAnnotations) {
        this.validationAnnotations = validationAnnotations;
    }

    /**
     * Set the source encoding
     *
     * @param sourceEncoding
     */
    public void setSourceEncoding(String sourceEncoding) {
        this.sourceEncoding = sourceEncoding;
    }

    /**
     * Set whether schema names should be appended to the package name.
     *
     * <p><b>!!! Important !!!</b><i> {@link NamingStrategy#getPackage(String, SchemaAndTable)}
     * will be invoked only if <code>schemaToPackage</code> is set to <code>true</code>.</i></p>
     *
     * @param schemaToPackage
     * @deprecated This flag will not be necessary in the future because the generated package name
     * can be controlled in method {@link NamingStrategy#getPackage(String, SchemaAndTable)}.
     */
    @Deprecated
    public void setSchemaToPackage(boolean schemaToPackage) {
        this.schemaToPackage = schemaToPackage;
        module.bind(SQLCodegenModule.SCHEMA_TO_PACKAGE, schemaToPackage);
    }

    /**
     * Set whether names should be normalized to lowercase
     *
     * @param lowerCase
     */
    public void setLowerCase(boolean lowerCase) {
        this.lowerCase = lowerCase;
    }

    /**
     * Set whether tables should be exported
     *
     * @param exportTables
     */
    public void setExportTables(boolean exportTables) {
        this.exportTables = exportTables;
    }

    /**
     * Set whether views should be exported
     *
     * @param exportViews
     */
    public void setExportViews(boolean exportViews) {
        this.exportViews = exportViews;
    }

    /**
     * Set whether all table types should be exported
     *
     * @param exportAll
     */
    public void setExportAll(boolean exportAll) {
        this.exportAll = exportAll;
    }

    /**
     * Set whether primary keys should be exported
     *
     * @param exportPrimaryKeys
     */
    public void setExportPrimaryKeys(boolean exportPrimaryKeys) {
        this.exportPrimaryKeys = exportPrimaryKeys;
    }

    /**
     * Set whether foreign keys should be exported
     *
     * @param exportForeignKeys
     */
    public void setExportForeignKeys(boolean exportForeignKeys) {
        this.exportForeignKeys = exportForeignKeys;
    }

    /**
     * Set whether direct foreign keys should be exported
     *
     * @param exportDirectForeignKeys
     */
    public void setExportDirectForeignKeys(boolean exportDirectForeignKeys) {
        this.exportDirectForeignKeys = exportDirectForeignKeys;
    }

    /**
     * Set whether inverse foreign keys should be exported
     *
     * @param exportInverseForeignKeys
     */
    public void setExportInverseForeignKeys(boolean exportInverseForeignKeys) {
        this.exportInverseForeignKeys = exportInverseForeignKeys;
    }

    /**
     * Set the java imports
     *
     * @param imports java imports array
     */
    public void setImports(String[] imports) {
        module.bind(CodegenModule.IMPORTS, new HashSet<String>(Arrays.asList(imports)));
    }

    /**
     * Set whether spatial type support should be used
     *
     * @param spatial
     */
    public void setSpatial(boolean spatial) {
        this.spatial = spatial;
    }

    /**
     * Set the table types to export as a comma separated string
     *
     * @param tableTypesToExport
     */
    public void setTableTypesToExport(String tableTypesToExport) {
        this.tableTypesToExport = tableTypesToExport;
    }

    @Nullable
    public Serializer getDaoIfcSerializer() {
        return daoIfcSerializer;
    }

    public void setDaoIfcSerializer(@Nullable Serializer daoIfcSerializer) {
        this.daoIfcSerializer = daoIfcSerializer;
    }

    @Nullable
    public Serializer getDaoImplSerializer() {
        return daoImplSerializer;
    }

    public void setDaoImplSerializer(@Nullable Serializer daoImplSerializer) {
        this.daoImplSerializer = daoImplSerializer;
    }

    public void setCover(boolean cover) {
        this.cover = cover;
    }

    public void setDatasource(String datasource) {
        this.datasource = datasource;
    }
}
