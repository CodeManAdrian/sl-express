package com.sl.ms.web.manager.auth;

import cn.hutool.json.JSONUtil;
import com.itheima.auth.sdk.AuthTemplate;
import com.itheima.auth.sdk.common.Result;
import com.itheima.auth.sdk.dto.AuthUserInfoDTO;
import com.itheima.auth.sdk.dto.LoginDTO;
import com.itheima.auth.sdk.service.TokenCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;

/*
 * Date: 2025/1/19 17:34
 * Author: Adrian
 * Version: 1.0
 * Description:
 * */
@SpringBootTest
public class AuthTemplateTest {

    @Resource
    private AuthTemplate authTemplate;

    @Resource
    private TokenCheckService tokenCheckService;

    @Test
    public void testLogin() {
        Result<LoginDTO> result = this.authTemplate.opsForLogin().token("Adrian", "123456");
//        System.out.println(result.getData());
//        System.out.println(result.getData().getToken());
        System.out.println(result.getData().getUser().getId());
        System.out.println(result.getData().getToken().getToken());
    }

    @Test
    public void testQueryRole() {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMzMwNTkyMDgzMzI0NDM5NTg1IiwiYWNjb3VudCI6IkFkcmlhbiIsIm5hbWUiOiLpmYjmtanljZciLCJvcmdpZCI6MTAyNTEwODU3MjcyMTM2NjU2MSwic3RhdGlvbmlkIjoxMDI0NzA1NDg5NDM2NDk0NzIxLCJhZG1pbmlzdHJhdG9yIjpmYWxzZSwiZXhwIjoxNzM3MzIzOTQzfQ.bDTMEDmPbt5U52SVBSJ4p-RjHwxN4kHSoFTXpUJTXRyTHGbnUW2T9cS9Gjy0OIriVifFiJQ4SU4_Upzl8V--9g";
        this.authTemplate.getAuthorityConfig().setToken(token);
        Result<List<Long>> roleByUserId = this.authTemplate.opsForRole().findRoleByUserId(1330592083324439585L);
        System.out.println(roleByUserId.getMsg());
    }

    @Test
    public void checkToken() {
        //解析token
        //admin
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMzMwNTkyMDgzMzI0NDM5NTg1IiwiYWNjb3VudCI6IkFkcmlhbiIsIm5hbWUiOiLpmYjmtanljZciLCJvcmdpZCI6MTAyNTEwODU3MjcyMTM2NjU2MSwic3RhdGlvbmlkIjoxMDI0NzA1NDg5NDM2NDk0NzIxLCJhZG1pbmlzdHJhdG9yIjpmYWxzZSwiZXhwIjoxNzM3MzIzOTQzfQ.bDTMEDmPbt5U52SVBSJ4p-RjHwxN4kHSoFTXpUJTXRyTHGbnUW2T9cS9Gjy0OIriVifFiJQ4SU4_Upzl8V--9g";

        AuthUserInfoDTO authUserInfo = this.tokenCheckService.parserToken(token);
        System.out.println(authUserInfo);

        System.out.println(JSONUtil.toJsonStr(authUserInfo));

    }
}
