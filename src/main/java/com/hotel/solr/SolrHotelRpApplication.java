package com.hotel.solr;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author gaofeng
 */
@Slf4j
@SpringBootApplication
public class SolrHotelRpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SolrHotelRpApplication.class, args);
        log.info("Solr数据服务启动, 欢迎来撩....");
    }
}
