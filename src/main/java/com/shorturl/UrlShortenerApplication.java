package com.shorturl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UrlShortenerApplication {

    public static void main(String[] args) {

        System.out.println("Default TZ = "
                + java.util.TimeZone.getDefault().getID());

        java.util.TimeZone.setDefault(
                java.util.TimeZone.getTimeZone("UTC"));

        System.out.println("After Change = "
                + java.util.TimeZone.getDefault().getID());

        SpringApplication.run(UrlShortenerApplication.class, args);
    }

}
