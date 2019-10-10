package com.talkingdata.ecommerce.support.undertow;

import com.talkingdata.ecommerce.utils.Configs;
import io.undertow.Undertow;
import io.undertow.servlet.api.DeploymentInfo;
import lombok.Getter;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * @author wwy
 * @date 2019-08-23
 */
@Getter
@Singleton
public final class UndertowServer {
    /**
     * 主机
      */
    private String host;

    /**
     * 端口
     */
    private int port;

    /**
     * 默认构造函数
     *
     */
    public UndertowServer() {
        this.host = Configs.getString("undertow.host");
        this.port = Configs.getInt("undertow.port");
    }

    /**
     * 启动服务器
     * @param appName 部署的应用名称
     * @param rootPath 根路径
     * @param appPath 应用路径
     */
    public void start(String appName, String rootPath, String appPath) {
        Undertow.Builder serverBuilder = Undertow.builder().addHttpListener(port, host);
        UndertowJaxrsServer server = new UndertowJaxrsServer();
        server.start(serverBuilder);

        DeploymentInfo di = server.undertowDeployment(ApplicationClass.class, appPath)
                .setClassLoader(UndertowServer.class.getClassLoader())
                .setContextPath(rootPath)
                .setDeploymentName(appName);
        server.deploy(di);
    }
}
