package com.sl.ms.web.customer.service.impl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.sl.ms.web.customer.service.WechatService;
import com.sl.transport.common.exception.SLWebException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
@Slf4j
public class WechatServiceImpl implements WechatService {

    @Value("${sl.wechat.appid}")
    private String appid;
    @Value("${sl.wechat.secret}")
    private String secret;
    public static final String LOGIN_URL = "https://api.weixin.qq.com/sns/jscode2session";
    public static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    public static final String PHONE_URL = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token={}";
    private static final int TIMEOUT = 20000;


    @Override
    public JSONObject getOpenid(String code) throws IOException {
        // 设置请求参数
        Map<String, Object> requestParam = MapUtil.<String, Object>builder()
                .put("appid", this.appid)//小程序 appId
                .put("secret", this.secret)//小程序 appSecret
                .put("js_code", code)//登录时获取的 code，可通过wx.login获取
                .put("grant_type", "authorization_code")//授权类型，此处只需填写 authorization_code返回参数
                .build();

        // 发送请求
        HttpResponse response = HttpRequest.get(LOGIN_URL)// 设置请求路径
                .form(requestParam)// 设置请求参数
                .timeout(TIMEOUT)// 设置超时时间
                .execute();// 执行请求

        // 解析结果
        if (response.isOk()) {
            JSONObject jsonObject = JSONUtil.parseObj(response.body());
            if (jsonObject.containsKey("errcode")) {
                throw new SLWebException(jsonObject.toString());
            }
            return jsonObject;
        }
        String errMsg = StrUtil.format("调用微信登录接口出错！ code = {}", code);
        throw new SLWebException(errMsg);
    }

    @Override
    public String getPhone(String code) throws IOException {
        // 获取access_token
        String accessToken = this.getAccessToken();

        // 封装参数
        Map<String, Object> requestParam = MapUtil.<String, Object>builder()
                .put("code", code)
                .build();

        // 发送请求 POST https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=ACCESS_TOKEN
        String url = StrUtil.format(PHONE_URL, accessToken);
        HttpResponse response = HttpRequest
                .post(url)
                .body(JSONUtil.toJsonStr(requestParam))
                .timeout(TIMEOUT)
                .execute();

        // 解析结果
        if (response.isOk()) {
            JSONObject jsonObject = JSONUtil.parseObj(response.body());
            if (ObjectUtil.notEqual(jsonObject.getInt("errcode"), 0)) {
                throw new SLWebException(jsonObject.toString());
            }
            return jsonObject.getByPath("phone_info.phoneNumber", String.class);
        }
        throw new SLWebException("调用获取手机号接口出错");
    }

    private String getAccessToken() {
        // 接口文档地址
        // https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/mp-access-token/getAccessToken.html
        // 封装参数
        Map<String, Object> requestParam = MapUtil.<String, Object>builder()
                .put("appid", this.appid) //小程序 appId
                .put("secret", this.secret) //小程序 appSecret
                .put("grant_type", "client_credential") //授权类型
                .build();

        // 发送请求
        HttpResponse response = HttpRequest
                .get(ACCESS_TOKEN_URL)
                .form(requestParam)
                .timeout(TIMEOUT)
                .execute();


        // 解析返回结果
        if (response.isOk()) {
            String body = response.body();
            JSONObject jsonObject = JSONUtil.parseObj(body);
            if (jsonObject.containsKey("access_token")) {
                //TODO 缓存token到redis，不应该每次都获取token
                return jsonObject.getStr("access_token");
            }
        }
        throw new SLWebException("调用获取接口调用凭据接口出错!");
    }
}
