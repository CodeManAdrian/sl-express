package com.sl.gateway.filter;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.itheima.auth.factory.AuthTemplateFactory;
import com.itheima.auth.sdk.AuthTemplate;
import com.itheima.auth.sdk.dto.AuthUserInfoDTO;
import com.itheima.auth.sdk.service.TokenCheckService;
import com.sl.gateway.config.MyConfig;
import com.sl.transport.common.constant.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.List;

/*
 * Date: 2025/1/22 1:06
 * Author: Adrian
 * Version: 1.0
 * Description:
 * */
@Component
public class TokenGatewayFilter implements GatewayFilter {

    @Resource
    private MyConfig myConfig;
    @Resource
    private TokenCheckService tokenCheckService;
    @Value("${role.manager}")
    private List<Long> managerRoleIds; //获取配置文件中的管理员角色id

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
        String token = exchange.getRequest().getHeaders().getFirst(Constants.GATEWAY.AUTHORIZATION);
        if (StrUtil.isEmpty(token)) {
            // 设置响应状态为401
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            // 拦截请求
            return exchange.getResponse().setComplete();
        }
        // 校验token
        AuthUserInfoDTO authUserInfoDTO = null;
        try {
            authUserInfoDTO = this.tokenCheckService.parserToken(token);
        } catch (Exception e) {
            // token不可用，不做处理
        }

        if (ObjectUtil.isEmpty(authUserInfoDTO)) {
            // token不可用，设置响应状态码为401
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            // 拦截请求
            return exchange.getResponse().setComplete();
        }


        // 3.检验权限，如果是非管理员不能登录
        AuthTemplate authTemplate = AuthTemplateFactory.get(token);
        // 获取用户拥有的角色id列表
        List<Long> roleIds = authTemplate.opsForRole().findRoleByUserId(authUserInfoDTO.getUserId()).getData();
        // 取交集，说明没有权限，设置响应状态码为400
        Collection<Long> intersection = CollUtil.intersection(roleIds, this.managerRoleIds);
        // 交集为空，说明无权限，返回400
        if (intersection.isEmpty()) {
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }

        // 4. 校验通过，向下游传递用户信息和token
        exchange.getRequest().mutate().header(Constants.GATEWAY.USERINFO, JSONUtil.toJsonStr(authUserInfoDTO));
        exchange.getRequest().mutate().header(Constants.GATEWAY.TOKEN, token);

        // 放行
        return chain.filter(exchange);
    }
}
