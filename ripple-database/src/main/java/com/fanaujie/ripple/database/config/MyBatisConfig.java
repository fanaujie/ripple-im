package com.fanaujie.ripple.database.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
@MapperScan("com.fanaujie.ripple.database.mapper")
public class MyBatisConfig {
}