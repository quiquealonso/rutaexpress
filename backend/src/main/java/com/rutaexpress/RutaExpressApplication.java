package com.rutaexpress;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Modulithic(sharedModules = "shared")
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
@EnableCaching
public class RutaExpressApplication {

    public static void main(String[] args) {
        SpringApplication.run(RutaExpressApplication.class, args);
    }
}
