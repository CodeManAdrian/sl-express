package com.sl.ms.courier.service.impl;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.StrUtil;
import com.sl.ms.base.api.common.WorkSchedulingFeign;
import com.sl.ms.base.domain.base.WorkSchedulingDTO;
import com.sl.ms.base.domain.enums.WorkUserTypeEnum;
import com.sl.ms.courier.service.CourierUserService;
import com.sl.ms.scope.api.ServiceScopeFeign;
import com.sl.ms.scope.dto.ServiceScopeDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/*
 * Date: 2025/2/1 21:39
 * Author: Adrian
 * Version: 1.0
 * Description:
 * */
@Service
@Slf4j
public class CourierUserServiceImpl implements CourierUserService {

    @Resource
    private ServiceScopeFeign serviceScopeFeign;
    @Resource
    private WorkSchedulingFeign workSchedulingFeign;

    /**
     * 条件查询快递员列表（结束取件时间当天快递员有排班）
     * 如果服务范围内无快递员，或满足服务范围的快递员无排班，则返回该网点所有满足排班的快递员
     *
     * @param agencyId         网点id
     * @param longitude        用户地址的经度
     * @param latitude         用户地址的纬度
     * @param estimatedEndTime 结束取件时间
     * @return 快递员id列表
     */
    @Override
    public List<Long> queryCourierIdListByCondition(Long agencyId, Double longitude, Double latitude, Long estimatedEndTime) {
        log.info("当前机构id为：{}", agencyId);

        //1.根据经纬度查询服务范围内的快递员
        List<ServiceScopeDTO> serviceScopeDTOS = serviceScopeFeign.queryListByLocation(2, longitude, latitude);

        //1.1 如果服务范围内有快递员，则在其中筛选结束取件时间当天有排班的快递员
        if (CollUtil.isNotEmpty(serviceScopeDTOS)) {
            List<Long> bids = CollStreamUtil.toList(serviceScopeDTOS, ServiceScopeDTO::getBid);
            log.info("根据经纬度查询到的快递员id有：{}", bids);
            String bidStr = StrUtil.join(",", bids);

            //1.2 查询排班数据，对满足服务范围、网点的快递员筛选排班
            List<WorkSchedulingDTO> workSchedulingDTOS = workSchedulingFeign.monthSchedule(bidStr, agencyId, WorkUserTypeEnum.COURIER.getCode(), estimatedEndTime);
            log.info("满足服务范围、网点的快递员排班：{}", workSchedulingDTOS);
            if (CollUtil.isNotEmpty(workSchedulingDTOS)) {
                List<Long> courierIds = StreamUtil.of(workSchedulingDTOS)
                        // 过滤出今日有排班的快递员
                        .filter(workSchedulingDTO -> workSchedulingDTO.getWorkSchedules().get(0))
                        .map(WorkSchedulingDTO::getUserId)
                        .collect(Collectors.toList());
                log.info("服务范围、网点、排班均满足的快递员id有：{}", courierIds);

                //1.3 存在同时满足服务范围、网点、排班的快递员，直接返回
                if (CollUtil.isNotEmpty(courierIds)) {
                    return courierIds;
                }
            }
        }

        //2. 如果服务范围内没有快递员，或服务范围内的快递员没有排班，则查询该网点的任一有排班快递员
        List<WorkSchedulingDTO> workSchedulingDTOS = workSchedulingFeign.monthSchedule(null, agencyId, WorkUserTypeEnum.COURIER.getCode(), estimatedEndTime);
        log.info("查询该网点所有快递员排班：{}", workSchedulingDTOS);
        if (CollUtil.isEmpty(workSchedulingDTOS)) {
            //该网点没有有排班的快递员
            return null;
        }

        //2.1 对满足网点的快递员筛选排班
        List<Long> courierIds = StreamUtil.of(workSchedulingDTOS)
                // 过滤出今日有排班的快递员
                .filter(workSchedulingDTO -> workSchedulingDTO.getWorkSchedules().get(0))
                .map(WorkSchedulingDTO::getUserId)
                .collect(Collectors.toList());
        log.info("只满足网点、排班的快递员id有：{}", courierIds);
        return courierIds;
    }
}
