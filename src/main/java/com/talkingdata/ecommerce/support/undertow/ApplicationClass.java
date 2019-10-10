package com.talkingdata.ecommerce.support.undertow;

import com.google.common.collect.Lists;
import com.google.inject.Injector;
import com.talkingdata.ecommerce.StartWorkerServer;
import com.talkingdata.ecommerce.support.guice.InjectorSingleton;

import javax.ws.rs.core.Application;
import java.io.File;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author wwy
 * @date 2019-08-23
 */
public class ApplicationClass extends Application {

    @Override
    public Set<Object> getSingletons() {
        Set<Object> resources = new LinkedHashSet<>();
        Injector injector = InjectorSingleton.getInstance().getInjector();
        //在此注册资源类
//        resources.add(injector.getInstance(GoodsResource.class));
//        resources.add(injector.getInstance(OrderResource.class));
//        resources.add(injector.getInstance(OrderItemResource.class));
        List<Class<?>> classes = obtainResourceClass("com.talkingdata.ecommerce.resource");
        classes.forEach(c -> resources.add(injector.getInstance(c)));
        return resources;
    }

    private List<Class<?>> obtainResourceClass(String packageName) {
        List<Class<?>> resourceClassList = Lists.newArrayList();
        File dir = obtainFileDir(packageName);
        for (File f : dir.listFiles((dir1, name) -> name.endsWith(".class"))) {
            String resourceClass = packageName + "." + f.getName().replace(".class", "");
            try {
                resourceClassList.add(Class.forName(resourceClass));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return resourceClassList;
    }

    public static File obtainFileDir(String packageName) {
        URL url = StartWorkerServer.class.getClassLoader().getResource(packageName.replace('.', '/'));
        if (null == url) {
            throw new IllegalStateException("无法通过包名找到路径: " + packageName);
        }
        File dir = new File(url.getFile());
        if (dir.isFile() || !dir.canRead()) {
            throw new IllegalStateException("目录无法读取:" + dir.getAbsolutePath());
        }
        return dir;
    }

}
