package com.sl.ms.web.manager.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.generator.MathGenerator;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.itheima.auth.sdk.AuthTemplate;
import com.itheima.auth.sdk.common.Result;
import com.itheima.auth.sdk.common.Token;
import com.itheima.auth.sdk.dto.*;
import com.sl.ms.base.api.common.WorkSchedulingFeign;
import com.sl.ms.base.domain.base.WorkSchedulingDTO;
import com.sl.ms.base.domain.enums.StatusEnum;
import com.sl.ms.base.domain.enums.WorkUserTypeEnum;
import com.sl.ms.web.manager.service.AuthService;
import com.sl.ms.web.manager.vo.agency.AgencySimpleVO;
import com.sl.ms.web.manager.vo.auth.CourierVO;
import com.sl.ms.web.manager.vo.auth.SysUserVO;
import com.sl.transport.common.util.AuthTemplateThreadLocal;
import com.sl.transport.common.util.PageResponse;
import com.sl.transport.common.vo.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 鉴权服务
 * 登录 验证码 员工列表 快递员列表 角色
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Value("${role.courier}")
    private String roleId;
    @Resource
    private WorkSchedulingFeign workSchedulingFeign;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private AuthTemplate authTemplate;
    private static final String CAPTCHA_REDIS_PREFIX = "CAPTCHA_";

    /**
     * 登录
     *
     * @param login 用户登录信息
     * @return 登录结果
     */
    @Override
    public R<LoginDTO> login(LoginParamDTO login) {
        // 1.对参数做校验
        if (ObjectUtil.hasEmpty(login.getKey(), login.getCode())) {
            return R.error("验证码不能为空");
        }
        if (ObjectUtil.hasEmpty(login.getAccount(), login.getPassword())) {
            return R.error("用户名、密码不能为空");
        }

        // 2.验证码验证
        String redisKey = CAPTCHA_REDIS_PREFIX + login.getKey();
        String redisValue = stringRedisTemplate.opsForValue().get(redisKey);
        if (ObjectUtil.hasEmpty(redisValue)) {
            return R.error("验证码已过期");
        }
        // 删除验证码
        this.stringRedisTemplate.delete(redisKey);
        boolean verify = new MathGenerator().verify(redisValue, login.getCode());
        if (!verify) {
            return R.error("验证码不正确");
        }

        // 3.校验用户名和密码，如果正确响应token
        return this.login(login.getAccount(), login.getPassword());
    }

    /**
     * 登录获取token
     *
     * @param account  账号
     * @param password 密码
     * @return 登录信息
     */
    @Override
    public R<LoginDTO> login(String account, String password) {
        //调用权限管家接口，传递用户名和密码
        Result<LoginDTO> result = this.authTemplate.opsForLogin().token(account, password);
        if (ObjectUtil.equal(result.getCode(), 0)) {
            //登录成功
            return R.success(result.getData());
        }
        //登录失败
        return R.error(result.getMsg());
    }

    @Override
    public void createCaptcha(String key, HttpServletResponse response) throws IOException {
        // 1.生成验证码
        LineCaptcha lineCaptcha = CaptchaUtil.createLineCaptcha(115, 42, 0, 30);//设置生成图片的宽高、字母个数、以及干扰线条数
        lineCaptcha.setGenerator(new MathGenerator(1));//设置为四则运算，指定参与运算数字位数为1
        String code = lineCaptcha.getCode();//验证码的值

        // 2.将验证码写入到redis中，有效时间为1分钟
        String redisKey = CAPTCHA_REDIS_PREFIX + key;
        this.stringRedisTemplate.opsForValue().set(redisKey, code, Duration.ofMinutes(1));

        // 3.将验证码输出到页面,设置页面不缓存
        response.setHeader(HttpHeaders.PRAGMA, "No-cache");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "No-cache");
        response.setDateHeader(HttpHeaders.EXPIRES, 0L);
        lineCaptcha.write(response.getOutputStream());
    }

    @Override
    public boolean check(String key, String value) {
        //TODO 待实现
        return false;
    }

    /**
     * 转换用户
     *
     * @param userDTO 用户DTO
     * @return 用户VO
     */
    @Override
    public SysUserVO parseUser2Vo(UserDTO userDTO) {
        SysUserVO vo = new SysUserVO();
        //填充基本信息
        vo.setUserId(userDTO.getId());
        vo.setAvatar(userDTO.getAvatar());
        vo.setEmail(userDTO.getEmail());
        vo.setMobile(userDTO.getMobile());
        vo.setAccount(userDTO.getAccount());
        vo.setName(userDTO.getName());
        vo.setStatus(userDTO.isStatus() ? StatusEnum.NORMAL.getCode() : StatusEnum.DISABLED.getCode());

        //处理所属机构信息
        AgencySimpleVO agency = new AgencySimpleVO();
        agency.setName(userDTO.getOrgName());
        vo.setAgency(agency);

        //处理岗位信息
        vo.setStationName(userDTO.getStationName());
        // 角色
        vo.setRoleNames(userDTO.getRoleNames());
        return vo;
    }

    /**
     * 获取用户信息
     *
     * @param id 用户id
     * @return 执行结果
     */
    @Override
    public SysUserVO user(Long id) {
        Result<UserDTO> result = AuthTemplateThreadLocal.get().opsForUser().getUserById(id);
        if (result.getCode() != 0) {
            return new SysUserVO();
        }
        return parseUser2Vo(result.getData());
    }

    /**
     * 批量获取用户信息
     *
     * @param ids 用户id
     * @return 执行结果
     */
    @Override
    public List<SysUserVO> users(List<Long> ids) {
        List<Long> longList = ids.stream().filter(Objects::nonNull).collect(Collectors.toList());
        Result<List<UserDTO>> result = AuthTemplateThreadLocal.get().opsForUser().list(longList);
        if (result.getCode() != 0) {
            return new ArrayList<>();
        }
        return result.getData().parallelStream().map(this::parseUser2Vo).collect(Collectors.toList());
    }

    /**
     * 员工分页
     *
     * @param page     页数
     * @param pageSize 页大小
     * @param agencyId 机构ID
     * @return 员工列表
     */
    @Override
    public PageResponse<SysUserVO> findUserByPage(Integer page, Integer pageSize, Long agencyId, String account, String name, String mobile) {
        Result<PageDTO<UserDTO>> result = AuthTemplateThreadLocal.get().opsForUser().getUserByPage(new UserPageDTO(page, pageSize, account, name, ObjectUtil.isNotEmpty(agencyId) ? agencyId : null, mobile));
        return getPageResponseR(page, pageSize, result);
    }

    /**
     * 快递员分页
     *
     * @param page     页数
     * @param pageSize 页大小
     * @param name     名称
     * @param mobile   手机号
     * @return 快递员列表
     */
    @Override
    public PageResponse<SysUserVO> findCourierByPage(Integer page, Integer pageSize, String name, String mobile, String account, Long orgId) {
        UserPageDTO userPageDTO = new UserPageDTO(page, pageSize, account, name, orgId, mobile);
        userPageDTO.setRoleId(roleId);
        Result<PageDTO<UserDTO>> result = AuthTemplateThreadLocal.get().opsForUser().getUserByPage(userPageDTO);

        // 转换vo
        PageResponse<SysUserVO> pageResponseR = getPageResponseR(page, pageSize, result);
        if (CollUtil.isEmpty(pageResponseR.getItems())) {
            return pageResponseR;
        }

        List<Long> userIds = pageResponseR.getItems().parallelStream().map(SysUserVO::getUserId).collect(Collectors.toList());
        if (CollUtil.isEmpty(userIds)) {
            return pageResponseR;
        }

        // 补充数据
        String bidStr = CollUtil.isEmpty(userIds) ? "" : CharSequenceUtil.join(",", userIds);
        List<WorkSchedulingDTO> workSchedulingDTOS = workSchedulingFeign.monthSchedule(bidStr, null, WorkUserTypeEnum.COURIER.getCode(), LocalDateTimeUtil.toEpochMilli(LocalDateTimeUtil.now()));
        if (CollUtil.isEmpty(workSchedulingDTOS)) {
            return pageResponseR;
        }
        Map<Long, Boolean> workMap = workSchedulingDTOS.parallelStream().filter(workSchedulingDTO -> ObjectUtil.isNotEmpty(workSchedulingDTO.getWorkSchedules())).collect(Collectors.toMap(WorkSchedulingDTO::getUserId, workSchedulingDTO -> workSchedulingDTO.getWorkSchedules().get(0)));

        pageResponseR.getItems().parallelStream().forEach(userDTO -> {
            // 上班状态
            try {
                Boolean aBoolean = workMap.get(userDTO.getUserId());
                if (ObjectUtil.isNotEmpty(aBoolean)) {
                    userDTO.setWorkStatus(aBoolean ? 1 : 0);
                }
            } catch (Exception ignored) {
                log.info("Exception:{}", ignored.getMessage());
            }
        });
        return pageResponseR;
    }

    /**
     * 转换用户返回结果
     *
     * @param page     页数
     * @param pageSize 页大小
     * @param result   用户信息
     * @return 用户信息
     */
    private PageResponse<SysUserVO> getPageResponseR(@RequestParam(name = "page") Integer page, @RequestParam(name = "pageSize") Integer pageSize, Result<PageDTO<UserDTO>> result) {
        if (result.getCode() == 0 && ObjectUtil.isNotEmpty(result.getData())) {
            PageDTO<UserDTO> userPage = result.getData();
            //处理对象转换
            List<SysUserVO> voList = userPage.getRecords().parallelStream().map(this::parseUser2Vo).collect(Collectors.toList());
            return PageResponse.of(voList, page, pageSize, userPage.getTotal() % userPage.getSize(), userPage.getTotal());
        }
        return PageResponse.getInstance();
    }

    /**
     * 根据机构查询快递员
     *
     * @param agencyId 机构id
     * @return 快递员列表
     */
    @Override
    public List<CourierVO> findByAgencyId(Long agencyId) {
        //构件查询条件
        UserPageDTO userPageDTO = new UserPageDTO(1, 999, null, null, agencyId, null);
        userPageDTO.setRoleId(roleId);

        //分页查询
        Result<PageDTO<UserDTO>> result = AuthTemplateThreadLocal.get().opsForUser().getUserByPage(userPageDTO);
        if (ObjectUtil.isEmpty(result.getData().getRecords())) {
            return Collections.emptyList();
        }

        //组装响应结果
        return result.getData().getRecords().stream().map(userDTO -> BeanUtil.toBean(userDTO, CourierVO.class)).collect(Collectors.toList());
    }


}
