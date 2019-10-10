package com.talkingdata.ecommerce.support.querydsl.codegen;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.mysema.codegen.CodeWriter;
import com.mysema.codegen.model.ClassType;
import com.mysema.codegen.model.Parameter;
import com.mysema.codegen.model.Type;
import com.mysema.codegen.model.TypeCategory;
import com.mysema.codegen.model.Types;
import com.querydsl.codegen.EntityType;
import com.querydsl.codegen.Property;
import com.querydsl.codegen.Serializer;
import com.querydsl.codegen.SerializerConfig;
import com.querydsl.core.util.BeanUtils;
import com.talkingdata.ecommerce.support.querydsl.common.PrimaryKey;
import org.joda.time.format.DateTimeFormat;

import javax.annotation.Generated;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author wwy
 * @date 2019/7/16
 */
public class SimpleBeanSerializer implements Serializer {
    private static final Function<Property, Parameter> propertyToParameter = new Function<Property, Parameter>() {
        public Parameter apply(Property input) {
            return new Parameter(input.getName(), input.getType());
        }
    };
    private final boolean propertyAnnotations;
    private final List<Type> interfaces;
    private final String javadocSuffix;
    private boolean addToString;
    private boolean addFullConstructor;
    private boolean printSupertype;

    public SimpleBeanSerializer() {
        this(true, " is a Querydsl bean type");
    }

    public SimpleBeanSerializer(String javadocSuffix) {
        this(true, javadocSuffix);
    }

    public SimpleBeanSerializer(boolean propertyAnnotations) {
        this(propertyAnnotations, " is a Querydsl bean type");
    }

    public SimpleBeanSerializer(boolean propertyAnnotations, String javadocSuffix) {
        this.interfaces = Lists.newArrayList();
        this.printSupertype = true;
        this.propertyAnnotations = propertyAnnotations;
        this.javadocSuffix = javadocSuffix;
    }

    public void serialize(EntityType model, SerializerConfig serializerConfig, CodeWriter writer) throws IOException {
        String simpleName = model.getSimpleName();
        if (!model.getPackageName().isEmpty()) {
            writer.packageDecl(model.getPackageName());
        }

        Set<String> importedClasses = this.getAnnotationTypes(model);
        Iterator var6 = this.interfaces.iterator();

        while(var6.hasNext()) {
            Type iface = (Type)var6.next();
            importedClasses.add(iface.getFullName());
        }

        importedClasses.add(Generated.class.getName());
        if (model.hasLists()) {
            importedClasses.add(List.class.getName());
        }

        if (model.hasCollections()) {
            importedClasses.add(Collection.class.getName());
        }

        if (model.hasSets()) {
            importedClasses.add(Set.class.getName());
        }

        if (model.hasMaps()) {
            importedClasses.add(Map.class.getName());
        }

        if (this.addToString && model.hasArrays()) {
            importedClasses.add(Arrays.class.getName());
        }

        if (hashDateTime(model)) {
            importedClasses.add(DateTimeFormat.class.getName());
        }

        writer.importClasses((String[])importedClasses.toArray(new String[importedClasses.size()]));
        writer.javadoc(new String[]{simpleName + this.javadocSuffix});
        var6 = model.getAnnotations().iterator();

        while(var6.hasNext()) {
            Annotation annotation = (Annotation)var6.next();
            writer.annotation(annotation);
        }

        writer.line(new String[]{"@Generated(\"", this.getClass().getName(), "\")"});
        if (!this.interfaces.isEmpty()) {
            Type superType = null;
            if (this.printSupertype && model.getSuperType() != null) {
                superType = model.getSuperType().getType();
            }

            Type[] ifaces = (Type[])this.interfaces.toArray(new Type[this.interfaces.size()]);
            writer.beginClass(model, superType, ifaces);
        } else if (this.printSupertype && model.getSuperType() != null) {
            writer.beginClass(model, null, model.getSuperType().getType());
        } else {
            writer.beginClass(model);
        }

        this.bodyStart(model, writer);
        if (this.addFullConstructor) {
            this.addFullConstructor(model, writer);
        }

        Property property;
        boolean getPrimaryKey = false;
        Property primaryKeyProperty = null;
        for(var6 = model.getProperties().iterator(); var6.hasNext(); writer.privateField(property.getType(), property.getEscapedName())) {
            property = (Property)var6.next();
            if (this.propertyAnnotations) {
                Iterator var8 = property.getAnnotations().iterator();

                while(var8.hasNext()) {
                    Annotation annotation = (Annotation)var8.next();
                    writer.annotation(annotation);
                    if (annotation instanceof PrimaryKey) {
                        getPrimaryKey = true;
                        primaryKeyProperty = property;
                    }
                }
            }
        }


        var6 = model.getProperties().iterator();

        while(var6.hasNext()) {
            property = (Property)var6.next();
            String propertyName = property.getEscapedName();
            writer.beginPublicMethod(property.getType(), "get" + BeanUtils.capitalize(propertyName), new Parameter[0]);
            writer.line(new String[]{"return ", propertyName, ";"});
            writer.end();
            Parameter parameter = new Parameter(propertyName, property.getType());
            writer.beginPublicMethod(Types.VOID, "set" + BeanUtils.capitalize(propertyName), new Parameter[]{parameter});
            writer.line(new String[]{"this.", propertyName, " = ", propertyName, ";"});
            writer.end();
        }

        if (this.addToString) {
            this.addToString(model, writer);
        }

        if (getPrimaryKey) {
            addGetPrimaryKey(writer, primaryKeyProperty);
            addSetPrimaryKey(writer, primaryKeyProperty);
        }

        this.bodyEnd(model, writer);
        writer.end();
    }

    private boolean hashDateTime(EntityType model) {
        Class<? extends EntityType> aClass = model.getClass();
        Field[] declaredFields = aClass.getDeclaredFields();
        Optional<Field> any = Arrays.stream(declaredFields).filter(f -> f.getGenericType().getTypeName().contains("DateTime")).findAny();
        return any.isPresent();
    }

    protected void addFullConstructor(EntityType model, CodeWriter writer) throws IOException {
        writer.beginConstructor(new Parameter[0]);
        writer.end();
        writer.beginConstructor(model.getProperties(), propertyToParameter);
        Iterator var3 = model.getProperties().iterator();

        while(var3.hasNext()) {
            Property property = (Property)var3.next();
            writer.line(new String[]{"this.", property.getEscapedName(), " = ", property.getEscapedName(), ";"});
        }

        writer.end();
    }

    protected void addToString(EntityType model, CodeWriter writer) throws IOException {
        writer.line(new String[]{"@Override"});
        writer.beginPublicMethod(Types.STRING, "toString", new Parameter[0]);
        StringBuilder builder = new StringBuilder();
        Iterator var4 = model.getProperties().iterator();

        while(var4.hasNext()) {
            Property property = (Property)var4.next();
            String propertyName = property.getEscapedName();
            if (builder.length() > 0) {
                builder.append(" + \", ");
            } else {
                builder.append("\"");
            }

            builder.append(propertyName + " = \" + ");
            if (property.getType().getCategory() == TypeCategory.ARRAY) {
                builder.append("Arrays.toString(" + propertyName + ")");
            } else {
                builder.append(propertyName);
            }
        }

        writer.line(new String[]{" return ", builder.toString(), ";"});
        writer.end();
    }

    protected void addGetPrimaryKey(CodeWriter writer, Property primaryKeyProperty) throws IOException {
        writer.line(new String[]{"@Override"});

        Type type = primaryKeyProperty.getType();

        writer.beginPublicMethod(type, "getPrimaryKey", new Parameter[0]);
        String value = primaryKeyProperty.getName();
        writer.line(new String[]{" return ", value, ";"});
        writer.end();
    }

    protected void addSetPrimaryKey(CodeWriter writer, Property primaryKeyProperty) throws IOException {
        Parameter parameter = new Parameter(primaryKeyProperty.getName(), primaryKeyProperty.getType());
        writer.line(new String[]{"@Override"});

        writer.beginPublicMethod(Types.VOID, "setPrimaryKey", new Parameter[]{parameter});
        String value = primaryKeyProperty.getName();
        writer.line(new String[]{"this.", primaryKeyProperty.getName(), " = ", primaryKeyProperty.getName(), ";"});
        writer.end();
    }

    protected void bodyStart(EntityType model, CodeWriter writer) throws IOException {
    }

    protected void bodyEnd(EntityType model, CodeWriter writer) throws IOException {
    }

    private Set<String> getAnnotationTypes(EntityType model) {
        Set<String> imports = new HashSet();
        Iterator var3 = model.getAnnotations().iterator();

        while(var3.hasNext()) {
            Annotation annotation = (Annotation)var3.next();
            imports.add(annotation.annotationType().getName());
        }

        if (this.propertyAnnotations) {
            var3 = model.getProperties().iterator();

            while(var3.hasNext()) {
                Property property = (Property)var3.next();
                Iterator var5 = property.getAnnotations().iterator();

                while(var5.hasNext()) {
                    Annotation annotation = (Annotation)var5.next();
                    imports.add(annotation.annotationType().getName());
                }
            }
        }

        return imports;
    }

    public void addInterface(Class<?> iface) {
        this.interfaces.add(new ClassType(iface, new Type[0]));
    }

    public void addInterface(Type type) {
        this.interfaces.add(type);
    }

    public void setAddToString(boolean addToString) {
        this.addToString = addToString;
    }

    public void setAddFullConstructor(boolean addFullConstructor) {
        this.addFullConstructor = addFullConstructor;
    }

    public void setPrintSupertype(boolean printSupertype) {
        this.printSupertype = printSupertype;
    }
}
