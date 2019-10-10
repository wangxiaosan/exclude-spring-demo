package com.talkingdata.ecommerce.utils;

import com.google.common.collect.Lists;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.nonNull;

/**
 * @author wwy
 * @date 2019/7/18
 */
public class BeanUtils {

    private static final Logger log = LoggerFactory.getLogger(BeanUtils.class);

    public static void copyProperties(Object source, Object target) {
        if (source != null) {
            setFieldValue(source,target);
        }
    }

    public static <T> void copyListProperties(Object source, List<T> target, Class<T> tClass) {
        if (nonNull(source) && source instanceof Collection) {
            Collection sourceList = (Collection) source;
            sourceList.forEach(s -> {
                try{
                    T t = tClass.newInstance();
                    copyProperties(s, t);
                    target.add(t);
                } catch (IllegalAccessException | InstantiationException e) {
                    log.error("copy list error");
                }
            });
        }
    }

    public static void setFieldValue(Object sourceEntity, Object targetEntity) {
        Class<?> cls = targetEntity.getClass();
        // 取出bean里的所有方法
        Method[] methods = cls.getDeclaredMethods();
        Field[] fields = cls.getDeclaredFields();
        Class<?> sourceEntityClass = sourceEntity.getClass();
        List<Field> sourceFields = Lists.newArrayList(sourceEntityClass.getDeclaredFields());
        Class<?> superclass = sourceEntityClass.getSuperclass();
        if (nonNull(superclass)) {
            Field[] superFields = superclass.getDeclaredFields();
            sourceFields.addAll(Lists.newArrayList(superFields));

        }
        for (Field field : fields) {
            field.setAccessible(true);

            Optional<Field> any = sourceFields.stream().filter(f -> f.getName().equals(field.getName())).findAny();
            if (!any.isPresent()) {
                continue;
            }
            Field sourceField = any.get();
            sourceField.setAccessible(true);
            try {
                String fieldSetName = parSetName(field.getName());
                if (!checkSetMet(methods, fieldSetName)) {
                    continue;
                }
                Method fieldSetMet = cls.getMethod(fieldSetName,
                        field.getType());
                Object value = sourceField.get(sourceEntity);
                if (null != value) {
                    String fieldType = field.getType().getSimpleName();
                    if ("String".equals(fieldType)) {
                        fieldSetMet.invoke(targetEntity, String.valueOf(value));
                    } else if ("Date".equals(fieldType)) {
                        if (sourceField.getType().getSimpleName().equals("DateTime")) {
                            fieldSetMet.invoke(targetEntity, ((DateTime) value).toDate());
                        } else if (sourceField.getType().getSimpleName().equals("Date")) {
                            fieldSetMet.invoke(targetEntity, (Date) value);
                        } else if (sourceField.getType().getSimpleName().equals("String")) {
                            Date temp = parseDate(String.valueOf(value));
                            fieldSetMet.invoke(targetEntity, temp);
                        }
                    } else if ("DateTime".equals(fieldType)) {
                        if (sourceField.getType().getSimpleName().equals("DateTime")) {
                            fieldSetMet.invoke(targetEntity, (DateTime) value);
                        } else if (sourceField.getType().getSimpleName().equals("Date")) {
                            fieldSetMet.invoke(targetEntity, new DateTime((Date) value));
                        } else if (sourceField.getType().getSimpleName().equals("String")) {
                            Date temp = parseDate(String.valueOf(value));
                            if (nonNull(temp)) {
                                fieldSetMet.invoke(targetEntity, new DateTime(temp));
                            }
                        }
                    } else if ("Integer".equals(fieldType) || "int".equals(fieldType)) {
                        Integer intval = Integer.parseInt(String.valueOf(value));
                        fieldSetMet.invoke(targetEntity, intval);
                    } else if ("Long".equalsIgnoreCase(fieldType)) {
                        Long temp = Long.parseLong(String.valueOf(value));
                        fieldSetMet.invoke(targetEntity, temp);
                    } else if ("BigDecimal".equalsIgnoreCase(fieldType)) {
                        BigDecimal temp = new BigDecimal(String.valueOf(value));
                        fieldSetMet.invoke(targetEntity, temp);
                    } else if ("Boolean".equalsIgnoreCase(fieldType)) {
                        Boolean temp = Boolean.parseBoolean(String.valueOf(value));
                        fieldSetMet.invoke(targetEntity, temp);
                    }else if("Object".equals(fieldType)){
                        fieldSetMet.invoke(targetEntity, value);
                    } else {
                        log.error("not supper type" + fieldType);
                    }
                }
            } catch (Exception e) {
                log.error("field transfer error,{} -> {}", sourceField.getName(), field.getName(), e);
                continue;
            }
        }
    }

    public static String parSetName(String fieldName) {
        if (null == fieldName || "".equals(fieldName)) {
            return null;
        }
        int startIndex = 0;
        if (fieldName.charAt(0) == '_')
            startIndex = 1;
        return "set"
                + fieldName.substring(startIndex, startIndex + 1).toUpperCase()
                + fieldName.substring(startIndex + 1);
    }

    public static Date parseDate(String datestr) {
        if (null == datestr || "".equals(datestr)) {
            return null;
        }
        try {
            String fmtstr;
            if (datestr.indexOf(':') > 0) {
                fmtstr = "yyyy-MM-dd HH:mm:ss";
            } else {
                fmtstr = "yyyy-MM-dd";
            }
            DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(fmtstr);
            return dateTimeFormatter.parseDateTime(datestr).toDate();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean checkSetMet(Method[] methods, String fieldSetMet) {
        for (Method met : methods) {
            if (fieldSetMet.equals(met.getName())) {
                return true;
            }
        }
        return false;
    }

}
