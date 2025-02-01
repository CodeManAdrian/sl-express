package com.sl.ms.dispatch.mq;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

/*
 * Date: 2025/2/1 20:19
 * Author: Adrian
 * Version: 1.0
 * Description:
 * */
@SpringBootTest
public class OrderMQListenerTest {
    @Resource
    private OrderMQListener orderMQListener;

    @Test
    void listenOrderMsg() {
        String msg = "{\"orderId\":123, \"agencyId\": 8001, \"taskType\":1, \"mark\":\"带包装\", \"longitude\":116.111, " +
                "\"latitude\":39.00, \"created\":1738412512000, \"estimatedEndTime\": 1738422000000}";
        this.orderMQListener.listenOrderMsg(msg);
    }
}
