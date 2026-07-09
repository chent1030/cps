package com.company.cps;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.company.cps.mapper")
public class CpsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CpsBackendApplication.class, args);
    }
}
