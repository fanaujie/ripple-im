package com.fanaujie.ripple.database.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "com.fanaujie.ripple.database.mapper")
public class MyBatisConfig {}
