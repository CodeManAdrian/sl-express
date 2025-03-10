package com.sl.gateway.filter;

import com.itheima.auth.sdk.dto.AuthUserInfoDTO;
import com.sl.transport.common.constant.Constants;

/*
 * Date: 2025/1/22 1:53
 * Author: Adrian
 * Version: 1.0
 * Description:
 * */
public interface AuthFilter {
    /**
     * 校验token
     *
     * @param token 请求中的token
     * @return token中携带的数据
     */
    AuthUserInfoDTO check(String token);

    /**
     * 鉴权
     *
     * @param token        请求中的token
     * @param authUserInfo token中携带的数据
     * @param path         当前请求的路径
     * @return 是否通过
     */
    Boolean auth(String token, AuthUserInfoDTO authUserInfo, String path);

    /**
     * 请求中携带token的名称
     *
     * @return 头名称
     */
    default String tokenHeaderName() {
        return Constants.GATEWAY.AUTHORIZATION;
    }
}
