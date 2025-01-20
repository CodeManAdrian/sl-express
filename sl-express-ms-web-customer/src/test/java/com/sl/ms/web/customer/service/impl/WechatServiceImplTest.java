package com.sl.ms.web.customer.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.io.IOException;

@SpringBootTest
class WechatServiceImplTest {

    @Resource
    WechatServiceImpl wechatService;

    @Test
    void getOpenid() {
        try {
            System.out.println(wechatService.getOpenid("0c3GFv000uLhzT1HkW200COkSF2GFv0X"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void getPhone() {
        try {
            String phone = wechatService.getPhone("a698c14ac23a30e58e5f89ca32e0f297b034ae08059ea18762d9bf034318e50b");
            System.out.println(phone);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}