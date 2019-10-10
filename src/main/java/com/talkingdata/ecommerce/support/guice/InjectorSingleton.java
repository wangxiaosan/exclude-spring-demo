package com.talkingdata.ecommerce.support.guice;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.name.Names;

import java.util.Properties;

/**
 * @author wwy
 * @date 2019-08-23
 */
public class InjectorSingleton {
    private Injector injector = null;

    private InjectorSingleton() {
    }

    private static class InstanceHolder {
        private static InjectorSingleton instance = new InjectorSingleton();
    }

    public static InjectorSingleton getInstance() {
        return InstanceHolder.instance;
    }

    public Injector getInjector() {
        if(this.injector != null) {
            return injector;
        }
        this.injector = Guice.createInjector(new GuiceModule() {
            @Override
            protected void initialize() {
                install(new AppModule());
            }
        });
        return this.injector;
    }
}
