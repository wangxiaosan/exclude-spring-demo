package com.talkingdata.ecommerce.utils;

import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

/**
 * @author wwy
 * @date 2019/6/20
 */
public final class Configs {

    private static Properties config = new Properties();

    static {
        init();
    }

    private static void init() {
        InputStream in = Configs.class.getClassLoader().getResourceAsStream("application.yml");
        Yaml yaml = new Yaml();
        Map<String, Object> load = yaml.load(in);
        Map<String, Object> flatMap = Maps.newHashMap();
        buildFlattenedMap(flatMap, load, null);
        config.putAll(flatMap);
    }

    public static String getString(String key) {
        return getString(key, null);
    }

    public static String getString(String key, String defaultValue) {
        Object object = getObject(key);
        if (null == object) {
            return defaultValue;
        }
        return String.valueOf(object);
    }

    public static Object getObject(String key) {
        return config.get(key);
    }

    public static Integer getInt(String key) {
        return getInt(key, 0);
    }

    public static Integer getInt(String key, Integer defaultValue) {
        Object object = getObject(key);
        if (null == object) {
            return defaultValue;
        }
        if (object instanceof Integer) {
            Integer value = (Integer) object;
            return value;
        } else {
            return Integer.valueOf(String.valueOf(object));
        }
    }

    private static void buildFlattenedMap(Map<String, Object> result, Map<String, Object> source, String path) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (StringUtils.isNotBlank(path)) {
                if (key.startsWith("[")) {
                    key = path + key;
                } else {
                    key = path + "." + key;
                }
            }
            Object value = entry.getValue();
            if (value instanceof String) {
                result.put(key, value);
            } else if (value instanceof Map) {
                // Need a compound key
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) value;
                buildFlattenedMap(result, map, key);
            } else if (value instanceof Collection) {
                result.put(key, value);
                // Need a compound key
                @SuppressWarnings("unchecked")
                Collection<Object> collection = (Collection<Object>) value;
                int count = 0;
                for (Object object : collection) {
                    buildFlattenedMap(result,
                            Collections.singletonMap("[" + (count++) + "]", object), key);
                }
            } else {
                result.put(key, value == null ? "" : value);
            }
        }
    }
}
