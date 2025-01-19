package com.sl.ms.web.manager.controller.auth;

import com.itheima.auth.sdk.common.Result;
import com.itheima.auth.sdk.dto.LoginDTO;
import com.itheima.auth.sdk.dto.LoginParamDTO;
import com.itheima.auth.sdk.dto.MenuDTO;
import com.sl.ms.web.manager.service.AuthService;
import com.sl.transport.common.util.AuthTemplateThreadLocal;
import com.sl.transport.common.vo.R;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * 鉴权服务
 */
@Slf4j
@RestController
@Api(tags = "鉴权服务")
public class AuthController {

    @Resource
    private AuthService authService;

    /**
     * 管理端登录
     *
     * @param login 登录信息
     * @return 用户信息
     */
    @PostMapping(value = "/login")
    @ApiOperation(value = "登录", notes = "登录")
    public R<LoginDTO> login(@RequestBody LoginParamDTO login) {
        return this.authService.login(login);
    }

    @ApiOperation(value = "验证码", notes = "验证码")
    @GetMapping(value = "/captcha", produces = "image/png")
    public void captcha(@RequestParam(value = "key") String key, HttpServletResponse response) throws IOException {
        this.authService.createCaptcha(key, response);
    }

    /**
     * 查询用户可用的所有资源
     *
     * @return 菜单
     */
    @GetMapping(value = "/menus")
    @ApiOperation(value = "查询用户可用的所有菜单", notes = "查询用户可用的所有菜单")
    public R<List<MenuDTO>> myMenus() {
        Result<List<MenuDTO>> menu = AuthTemplateThreadLocal.get().opsForPermission().getMenu();
        return R.success(menu.getData());
    }
}
