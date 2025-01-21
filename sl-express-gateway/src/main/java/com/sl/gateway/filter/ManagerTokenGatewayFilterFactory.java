package com.sl.gateway.filter;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.itheima.auth.factory.AuthTemplateFactory;
import com.itheima.auth.sdk.AuthTemplate;
import com.itheima.auth.sdk.common.AuthSdkException;
import com.itheima.auth.sdk.dto.AuthUserInfoDTO;
import com.itheima.auth.sdk.service.TokenCheckService;
import com.sl.gateway.config.MyConfig;
import com.sl.transport.common.constant.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.List;

/*
 * Date: 2025/1/21 18:54
 * Author: Adrian
 * Version: 1.0
 * Description:
 * 第一个版本把过滤和校验的逻辑放在了一起，而第二个版本则通过分离过滤和校验的逻辑来提高代码的可读性和可维护性。具体来说，第二个版本是通过将校验逻辑提取到一个独立的 TokenGatewayFilter 类中，从而实现了过滤和校验的分离。符合单一职责设计模式，把过滤和校验从一个类中分离出来，有利于（校验）代码复用。
 * */
@Component
public class ManagerTokenGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    @Resource
    private TokenGatewayFilter tokenGatewayFilter;

    @Override
    public GatewayFilter apply(Object config) {
        return this.tokenGatewayFilter;
    }
}
