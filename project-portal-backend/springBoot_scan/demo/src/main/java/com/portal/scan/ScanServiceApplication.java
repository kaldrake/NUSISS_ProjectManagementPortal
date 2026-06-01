// src/main/java/com/portal/scan/ScanServiceApplication.java
package com.portal.scan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ScanServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ScanServiceApplication.class, args);
        System.out.println("========================================");
        System.out.println("  Scan Service Started on Port 8083");
        System.out.println("========================================");
    }
}