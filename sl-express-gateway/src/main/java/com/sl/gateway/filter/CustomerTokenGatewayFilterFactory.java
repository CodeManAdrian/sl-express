package com.sl.gateway.filter;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import com.itheima.auth.factory.AuthTemplateFactory;
import com.itheima.auth.sdk.AuthTemplate;
import com.itheima.auth.sdk.dto.AuthUserInfoDTO;
import com.itheima.auth.sdk.service.TokenCheckService;
import com.sl.gateway.config.MyConfig;
import com.sl.gateway.properties.JwtProperties;
import com.sl.transport.common.constant.Constants;
import com.sl.transport.common.util.JwtUtils;
import com.sl.transport.common.util.ObjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/*
 * Date: 2025/1/21 18:54
 * Author: Adrian
 * Version: 1.0
 * Description: 这个优化通过接口抽象和职责分离，提高了代码的灵活性、可维护性，并且更易于扩展新的过滤逻辑。
 * */
@Slf4j
@Component
public class CustomerTokenGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> implements AuthFilter {

    @Resource
    private MyConfig myConfig;

    @Resource
    private JwtProperties jwtProperties;


    @Override
    public GatewayFilter apply(Object config) {
        return new TokenGatewayFilter(myConfig, this);
    }

    @Override
    public AuthUserInfoDTO check(String token) {
        Map<String, Object> claims = JwtUtils.checkToken(token, jwtProperties.getPublicKey());
        if (ObjectUtil.isEmpty(claims)) {
            return null;
        }
        Long userId = MapUtil.get(claims, Constants.GATEWAY.USER_ID, Long.class);
        AuthUserInfoDTO authUserInfoDTO = new AuthUserInfoDTO();
        authUserInfoDTO.setUserId(userId);
        return authUserInfoDTO;
    }

    @Override
    public Boolean auth(String token, AuthUserInfoDTO authUserInfo, String path) {
        return true;
    }

    @Override
    public String tokenHeaderName() {
        return Constants.GATEWAY.ACCESS_TOKEN;
    }
}
