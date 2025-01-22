package com.sl.gateway.filter;

import com.itheima.auth.factory.AuthTemplateFactory;
import com.itheima.auth.sdk.AuthTemplate;
import com.itheima.auth.sdk.dto.AuthUserInfoDTO;
import com.itheima.auth.sdk.service.TokenCheckService;
import com.sl.gateway.config.MyConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/*
 * Date: 2025/1/21 18:54
 * Author: Adrian
 * Version: 1.0
 * Description:
 * */
@Slf4j
@Component
public class CourierTokenGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> implements AuthFilter {

    @Resource
    private MyConfig myConfig;

    @Resource
    private TokenCheckService tokenCheckService;

    @Value("${role.courier}")
    private String courierId;


    @Override
    public GatewayFilter apply(Object config) {
        return new TokenGatewayFilter(myConfig, this);
    }

    @Override
    public AuthUserInfoDTO check(String token) {
        // 校验token
        AuthUserInfoDTO authUserInfoDTO = null;
        try {
            authUserInfoDTO = this.tokenCheckService.parserToken(token);
        } catch (Exception e) {
            // token不可用，不做处理
        }
        return authUserInfoDTO;
    }

    @Override
    public Boolean auth(String token, AuthUserInfoDTO authUserInfo, String path) {
        AuthTemplate authTemplate = AuthTemplateFactory.get(token);
        // 获取用户拥有的角色id列表
        List<Long> roleIds = authTemplate.opsForRole().findRoleByUserId(authUserInfo.getUserId()).getData();
        return roleIds.contains(Long.valueOf(courierId));
    }
}
