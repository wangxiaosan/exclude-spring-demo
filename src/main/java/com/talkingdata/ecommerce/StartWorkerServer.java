package com.talkingdata.ecommerce;

import com.google.inject.Injector;
import com.talkingdata.ecommerce.support.guice.InjectorSingleton;
import com.talkingdata.ecommerce.support.undertow.UndertowServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author wwy
 * @date 2019-08-23
 */
public class StartWorkerServer {

    private final static Logger logger = LoggerFactory.getLogger(StartWorkerServer.class);

    public static void main(String[] args) {

        logger.info("Starting Application service...");

        try {
            Injector injector = InjectorSingleton.getInstance().getInjector();
            UndertowServer undertowServer = injector.getInstance(UndertowServer.class);
            undertowServer.start("ecommerce", "", "");
            logger.info("SimpleRest service started on port {}.", undertowServer.getPort());
        } catch (Exception e) {
            logger.error("Start SimpleRest service failed,{}.", e.getMessage());
        }
    }
}
