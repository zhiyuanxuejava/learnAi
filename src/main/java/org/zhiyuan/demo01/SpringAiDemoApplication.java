package org.zhiyuan.demo01;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring AI 演示项目启动类。
 * 这里额外开启了 ConfigurationProperties 扫描，便于集中管理业务配置。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringAiDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiDemoApplication.class, args);
    }

}
