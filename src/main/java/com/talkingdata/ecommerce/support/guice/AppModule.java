package com.talkingdata.ecommerce.support.guice;

import com.google.common.collect.Maps;
import com.google.inject.AbstractModule;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static com.talkingdata.ecommerce.support.undertow.ApplicationClass.obtainFileDir;

/**
 * @author wwy
 * @date 2019-08-23
 */
@Slf4j
public class AppModule extends AbstractModule {

    @Override
    protected void configure() {
        Map<Class<Object>, Class<Object>> serviceClassMap = obtainImplMap("com.talkingdata.ecommerce.service");
        Map<Class<Object>, Class<Object>> daoClassClassMap = obtainImplMap("com.talkingdata.ecommerce.repository");
        serviceClassMap.entrySet().forEach(e -> bind(e.getKey()).to(e.getValue()));
        daoClassClassMap.entrySet().forEach(e -> bind(e.getKey()).to(e.getValue()));
    }

    private <T> Map<Class<T>, Class<T>> obtainImplMap(String packageName) {
        HashMap<Class<T>, Class<T>> implMap = Maps.newHashMap();
        File dir = obtainFileDir(packageName);
        for (File f : dir.listFiles((dir1, name) -> name.endsWith(".class"))) {
            String interfaceClassName = packageName + "." + f.getName().replace(".class", "");
            String implClassName = packageName + ".impl." + f.getName().replace(".class", "") + "Impl";
            try {
                Class interfaceClazz = Class.forName(interfaceClassName);
                Class implClazz = Class.forName(implClassName);
                implMap.put(interfaceClazz, implClazz);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return implMap;
    }
}
