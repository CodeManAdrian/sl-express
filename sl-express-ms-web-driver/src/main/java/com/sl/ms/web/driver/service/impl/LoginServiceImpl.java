package com.sl.ms.web.driver.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.itheima.auth.sdk.AuthTemplate;
import com.itheima.auth.sdk.common.Result;
import com.itheima.auth.sdk.dto.LoginDTO;
import com.sl.ms.web.driver.service.LoginService;
import com.sl.ms.web.driver.vo.request.AccountLoginVO;
import com.sl.transport.common.exception.SLWebException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class LoginServiceImpl implements LoginService {
    @Resource
    private AuthTemplate authTemplate;

    @Override
    public String accountLogin(AccountLoginVO accountLoginVO) {
        String account = accountLoginVO.getAccount();
        String password = accountLoginVO.getPassword();
        Result<LoginDTO> result = this.authTemplate.opsForLogin().token(account, password);
        if (result.getCode() != Result.success().getCode()) {
            throw new SLWebException("登录失败");
        }
        // 成功返回token
        return result.getData().getToken().getToken();
    }
}
