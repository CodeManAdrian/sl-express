package com.sl.ms.web.customer.service.impl;

import cn.hutool.core.map.MapUtil;
import com.sl.ms.web.customer.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.Map;

/*
 * Date: 2025/1/21 14:03
 * Author: Adrian
 * Version: 1.0
 * Description:
 * */
@SpringBootTest
public class TokenServiceTest {

    @Resource
    private TokenService tokenService;


    @Test
    void createRefreshToken() {
        Map<String, Object> claims = MapUtil.<String, Object>builder().put("id", 123)
                .build();
        String refreshToken = this.tokenService.createRefreshToken(claims);
        System.out.println(refreshToken);
    }
}
