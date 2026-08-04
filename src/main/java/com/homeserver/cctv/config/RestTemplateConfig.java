package com.homeserver.cctv.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // PENTING: pakai SimpleClientHttpRequestFactory (klasik, berbasis
        // HttpURLConnection) secara eksplisit, BUKAN biarkan RestTemplateBuilder
        // auto-pilih factory. Spring Boot 3.x defaultnya bisa memilih
        // java.net.http.HttpClient (JDK HttpClient modern), yang terbukti
        // menyebabkan multipart request ke face-service gagal ("field file
        // required" walau body-nya valid) - kemungkinan soal penanganan
        // Content-Length/Expect:100-continue yang beda. SimpleClientHttpRequestFactory
        // perilakunya lebih predictable untuk kasus ini.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);   // 5 detik
        factory.setReadTimeout(60_000);     // 60 detik - deteksi wajah di CPU bisa agak lama
        return new RestTemplate(factory);
    }
}