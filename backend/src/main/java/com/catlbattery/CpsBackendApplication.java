package com.catlbattery;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan("com.catlbattery.infra.mapper.cps")
public class CpsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CpsBackendApplication.class, args);
    }
}
