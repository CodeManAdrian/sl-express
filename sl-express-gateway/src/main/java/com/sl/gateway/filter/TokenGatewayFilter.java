package com.sl.gateway.filter;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.itheima.auth.sdk.dto.AuthUserInfoDTO;
import com.itheima.auth.sdk.service.TokenCheckService;
import com.sl.gateway.config.MyConfig;
import com.sl.transport.common.constant.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.util.List;

/*
 * Date: 2025/1/22 1:06
 * Author: Adrian
 * Version: 1.0
 * Description:
 * */
@Slf4j
public class TokenGatewayFilter implements GatewayFilter, Ordered {

    private MyConfig myConfig;

    private AuthFilter authFilter;

    public TokenGatewayFilter(MyConfig myConfig, AuthFilter authFilter) {
        this.myConfig = myConfig;
        this.authFilter = authFilter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 白名单放行
        // exchange可以拿到路径上下文
        String path = exchange.getRequest().getPath().toString();
        if (StrUtil.startWithAny(path, this.myConfig.getNoAuthPaths())) {
            // 直接放行
            return chain.filter(exchange);
        }

        // 请求头中的token是否有效
        String token = exchange.getRequest().getHeaders().getFirst(this.authFilter.tokenHeaderName());
        if (StrUtil.isEmpty(token)) {
            // 设置响应状态为401
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            // 拦截请求
            return exchange.getResponse().setComplete();
        }

        // 校验token
        AuthUserInfoDTO authUserInfoDTO = this.authFilter.check(token);
        if (ObjectUtil.isEmpty(authUserInfoDTO)) {
            // token不可用，设置响应状态码为401
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            // 拦截请求
            return exchange.getResponse().setComplete();
        }


        Boolean bool = this.authFilter.auth(token, authUserInfoDTO, path);
        // 交集为空，说明无权限，返回400
        if (ObjectUtil.equal(bool, Boolean.FALSE)) {
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }

        // 4. 校验通过，向下游传递用户信息和token
        exchange.getRequest().mutate().header(Constants.GATEWAY.USERINFO, JSONUtil.toJsonStr(authUserInfoDTO));
        exchange.getRequest().mutate().header(Constants.GATEWAY.TOKEN, token);

        // 放行
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }
}
