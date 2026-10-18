package com.feyfey.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 内嵌 Tomcat 连接器配置：调整请求体处理上限和查询参数允许字符。
 * 外部 WAR 容器的连接器参数需在容器侧配置。
 *
 * @author zhuxianfei
 * @date 2021/12/20 14:10
 */
@Configuration
public class SystemConfig {
    private static final Logger logger = LoggerFactory.getLogger(SystemConfig.class);

    /**
     * 创建内嵌 Tomcat 工厂；大小限制单位为字节，不代表 PDF 输入文件大小限制。
     *
     * @return 应用使用的内嵌服务器工厂
     */
    @Bean
    public TomcatServletWebServerFactory containerFactory() {
        return new TomcatServletWebServerFactory() {
            protected void customizeConnector(Connector connector) {
                int maxSize = 50000000;
                // 先保留父工厂的连接器定制，再覆盖本项目关心的请求处理参数。
                super.customizeConnector(connector);
                // 限制容器解析 POST 表单数据的大小；接口参数指向本机文件，并非 PDF 上传内容。
                connector.setMaxPostSize(maxSize);
                // 控制认证或协议升级等场景中，容器暂存请求体的最大字节数。
                connector.setMaxSavePostSize(maxSize);
                // 保留文件路径查询参数的字符配置，调用方仍应对中文和特殊字符进行 URL 编码。
                connector.setProperty("relaxedQueryChars", "/");
                // 仅在 HTTP/1.1 协议处理器支持该参数时，设置拒绝请求后继续吞入请求体的上限。
                if (connector.getProtocolHandler() instanceof AbstractHttp11Protocol) {
                    ((AbstractHttp11Protocol<?>) connector.getProtocolHandler()).setMaxSwallowSize(maxSize);
                    logger.info("Set MaxSwallowSize " + maxSize);
                }
            }
        };
    }
}
