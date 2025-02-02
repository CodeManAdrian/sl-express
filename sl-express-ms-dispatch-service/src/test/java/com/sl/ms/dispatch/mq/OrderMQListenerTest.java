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
        String msg = "{\"orderId\":123, \"agencyId\": 1024981239465110017, \"taskType\":1, \"mark\":\"带包装\", \"longitude\":121.61, " +
                "\"latitude\":31.03, \"created\":1738484236000, \"estimatedEndTime\": 1738494120000}";
        this.orderMQListener.listenOrderMsg(msg);
    }
}
