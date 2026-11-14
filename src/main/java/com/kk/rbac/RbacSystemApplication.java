package com.kk.rbac;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RbacSystemApplication {

    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(RbacSystemApplication.class, args);
    }
}
