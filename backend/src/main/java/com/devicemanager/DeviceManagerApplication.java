package com.devicemanager;

import com.devicemanager.config.DotEnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DeviceManagerApplication {

    public static void main(String[] args) {
        DotEnvLoader.load();
        SpringApplication.run(DeviceManagerApplication.class, args);
    }
}
