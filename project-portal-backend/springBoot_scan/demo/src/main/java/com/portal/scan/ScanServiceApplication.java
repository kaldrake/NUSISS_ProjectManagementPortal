// src/main/java/com/portal/scan/ScanServiceApplication.java
package com.portal.scan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ScanServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(ScanServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ScanServiceApplication.class, args);
        log.info("Scan Service started on port 8083");
    }
}