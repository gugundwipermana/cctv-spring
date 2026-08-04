package com.homeserver.cctv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point aplikasi.
 *
 * @EnableScheduling diaktifkan dari awal walau scheduler compile video
 * belum kita buat - biar nanti tinggal tambah method dengan @Scheduled
 * di package `service` atau bikin package `scheduler` tanpa perlu ubah
 * konfigurasi lagi.
 */
@SpringBootApplication
@EnableScheduling
public class CctvApplication {
    public static void main(String[] args) {
        SpringApplication.run(CctvApplication.class, args);
    }
}
