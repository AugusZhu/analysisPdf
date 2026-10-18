package com.feyfey;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用入口，支持独立启动与 WAR 部署。
 */
@SpringBootApplication
public class AnalysispdfApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalysispdfApplication.class, args);
    }
}
