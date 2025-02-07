package com.sl.ms.work.service;

/*
 * Date: 2025/2/7 23:23
 * Author: Adrian
 * Version: 1.0
 * Description:
 * */

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class TransportOrderServiceTest {

    @Resource
    private TransportOrderService transportOrderService;

    @Test
    void updateByTaskId() {
        //设置运输任务id
        this.transportOrderService.updateByTaskId(1568165717632933889L);
    }
}
