package io.github.easytrans.demo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"io.github.easytrans"})
@MapperScan("io.github.easytrans.demo.repository")
public class EasyTransDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyTransDemoApplication.class, args);
    }
}
