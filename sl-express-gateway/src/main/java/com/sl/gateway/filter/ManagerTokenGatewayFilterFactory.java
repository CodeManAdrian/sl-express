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
 * Description: 这个优化通过接口抽象和职责分离，提高了代码的灵活性、可维护性，并且更易于扩展新的过滤逻辑。
 * */
@Component
public class ManagerTokenGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> implements AuthFilter {

    @Resource
    private MyConfig myConfig;

    @Resource
    private TokenCheckService tokenCheckService;

    @Value("${role.manager}")
    private List<Long> managerRoleIds; //获取配置文件中的管理员角色id


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
        // 取交集，说明没有权限，设置响应状态码为400
        Collection<Long> intersection = CollUtil.intersection(roleIds, this.managerRoleIds);
        return CollUtil.isNotEmpty(intersection);
    }
}
