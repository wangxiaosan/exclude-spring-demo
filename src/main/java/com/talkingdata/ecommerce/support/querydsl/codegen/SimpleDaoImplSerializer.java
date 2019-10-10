package com.talkingdata.ecommerce.support.querydsl.codegen;

import com.google.common.collect.Lists;
import com.mysema.codegen.CodeWriter;
import com.mysema.codegen.model.Type;
import com.querydsl.codegen.EntityType;
import com.querydsl.codegen.Serializer;
import com.querydsl.codegen.SerializerConfig;
import com.querydsl.codegen.Supertype;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author wwy
 * @date 2019/7/16
 */
public class SimpleDaoImplSerializer implements Serializer {

    private final String javadocSuffix;

    private boolean printSupertype = true;

    private String cachedRepositories;

    /**
     * Create a new BeanSerializer
     */
    public SimpleDaoImplSerializer() {
        this(" is a Querydsl repository implement type");
    }

    /**
     * Create a new BeanSerializer with the given javadoc suffix
     *
     * @param javadocSuffix
     */
    public SimpleDaoImplSerializer(String javadocSuffix) {
        this.javadocSuffix = javadocSuffix;
    }

    public void serialize(EntityType model, SerializerConfig serializerConfig,
                          CodeWriter writer) throws IOException {
        String simpleName = model.getSimpleName();

        Set<Supertype> superType = model.getSuperTypes();
        List<Type> parameters = superType.stream().map(s -> s.getType()).collect(Collectors.toList());

        // package
        if (!model.getPackageName().isEmpty()) {
            writer.packageDecl(model.getPackageName());
        }

        // header
//        RepositoryImpl repAnnotation = new RepositoryImpl(
//                Character.toLowerCase(simpleName.charAt(0))
//                        + simpleName.substring(1, simpleName.length() - 4));
//        model.addAnnotation(repAnnotation);

        // imports
        Set<String> importedClasses = getAnnotationTypes(model);
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
        importedClasses.add(Inject.class.getName());
        importedClasses.add(DataSource.class.getName());
        parameters.forEach(p -> {
            try {
                writer.importClasses(p.getFullName());
            } catch (IOException e) {
            }
        });
        writer.importClasses(importedClasses.toArray(new String[importedClasses
                .size()]));

        // javadoc
        writer.javadoc(simpleName + javadocSuffix);

        for (Annotation annotation : model.getAnnotations()) {
            writer.annotation(annotation);
        }

        Set<Supertype> superTypes = model.getSuperTypes();
        if (printSupertype && superTypes != null) {
            writer.beginClass(model, Lists.newArrayList(superTypes).get(0).getType(), Lists.newArrayList(superTypes).get(1).getType());
        } else {
            writer.beginClass(model);
        }

        bodyStart(model, writer);

        addFullConstructor(model, writer);

        bodyEnd(model, writer);

        writer.end();
    }

    protected void bodyStart(EntityType model, CodeWriter writer)
            throws IOException {

    }

    protected void bodyEnd(EntityType model, CodeWriter writer)
            throws IOException {
        // template method
    }

    protected void addFullConstructor(EntityType model, CodeWriter writer) throws IOException {

        // full constructor
        String[] c = null;
        if (StringUtils.isNotBlank(cachedRepositories)) {
            c = cachedRepositories.split(",");
        }
//        writer.line("@Autowired");
        writer.line("public " + model.getSimpleName() + "(@Autowired DataSource dataSource) {");
        writer.line("	super(dataSource);");
        if (null != c && contains(model.getSimpleName(), c)) {
            writer.line("	hasCache=true;");
        }
        writer.line("}");
        writer.line("");
    }

    private Set<String> getAnnotationTypes(EntityType model) {
        Set<String> imports = new HashSet<String>();
        for (Annotation annotation : model.getAnnotations()) {
            imports.add(annotation.annotationType().getName());
        }
        return imports;
    }

    private boolean contains(String simpleName, String[] c) {
        for (int i = 0; i < c.length; i++) {
            if (simpleName.equals(c[i])) {
                return true;
            }
        }
        return false;
    }

    public void setPrintSupertype(boolean printSupertype) {
        this.printSupertype = printSupertype;
    }

    public void setCachedRepositories(String cachedRepositories) {
        this.cachedRepositories = cachedRepositories;
    }
}
