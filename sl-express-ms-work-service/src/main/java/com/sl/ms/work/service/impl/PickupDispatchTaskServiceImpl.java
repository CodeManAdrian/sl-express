package com.sl.ms.work.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sl.ms.work.domain.dto.CourierTaskCountDTO;
import com.sl.ms.work.domain.dto.PickupDispatchTaskDTO;
import com.sl.ms.work.domain.dto.request.PickupDispatchTaskPageQueryDTO;
import com.sl.ms.work.domain.dto.response.PickupDispatchTaskStatisticsDTO;
import com.sl.ms.work.domain.enums.WorkExceptionEnum;
import com.sl.ms.work.domain.enums.pickupDispatchtask.PickupDispatchTaskIsDeleted;
import com.sl.ms.work.domain.enums.pickupDispatchtask.PickupDispatchTaskStatus;
import com.sl.ms.work.domain.enums.pickupDispatchtask.PickupDispatchTaskType;
import com.sl.ms.work.entity.PickupDispatchTaskEntity;
import com.sl.ms.work.mapper.TaskPickupDispatchMapper;
import com.sl.ms.work.service.PickupDispatchTaskService;
import com.sl.transport.common.exception.SLException;
import com.sl.transport.common.util.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/*
 * Date: 2025/2/2 18:17
 * Author: Adrian
 * Version: 1.0
 * Description:
 * */
@Service
public class PickupDispatchTaskServiceImpl extends ServiceImpl<TaskPickupDispatchMapper, PickupDispatchTaskEntity> implements PickupDispatchTaskService {
    @Override
    public Boolean updateStatus(PickupDispatchTaskDTO pickupDispatchTaskDTO) {
        return null;
    }

    @Override
    public Boolean updateCourierId(List<Long> ids, Long originalCourierId, Long targetCourierId) {
        return null;
    }

    /**
     * 新增取派件任务
     *
     * @param taskPickupDispatch 取派件任务信息
     * @return 取派件任务信息
     */
    @Override
    @Transactional
    public PickupDispatchTaskEntity saveTaskPickupDispatch(PickupDispatchTaskEntity taskPickupDispatch) {
        // 设置任务状态为新任务
        taskPickupDispatch.setStatus(PickupDispatchTaskStatus.NEW);
        boolean result = super.save(taskPickupDispatch);

        if (result) {
            //TODO 同步快递员任务到es

            //TODO 生成运单跟踪消息和快递员端取件/派件消息通知
            return taskPickupDispatch;
        }
        throw new SLException(WorkExceptionEnum.PICKUP_DISPATCH_TASK_SAVE_ERROR);
    }

    /**
     * 分页查询取派件任务
     *
     * @param dto 查询条件
     * @return 分页结果
     */
    @Override
    public PageResponse<PickupDispatchTaskDTO> findByPage(PickupDispatchTaskPageQueryDTO dto) {
        //1.构造条件
        Page<PickupDispatchTaskEntity> iPage = new Page<>(dto.getPage(), dto.getPageSize());
        LambdaQueryWrapper<PickupDispatchTaskEntity> queryWrapper = Wrappers.<PickupDispatchTaskEntity>lambdaQuery()
                .like(ObjectUtil.isNotEmpty(dto.getId()), PickupDispatchTaskEntity::getId, dto.getId())
                .like(ObjectUtil.isNotEmpty(dto.getOrderId()), PickupDispatchTaskEntity::getOrderId, dto.getOrderId())
                .eq(ObjectUtil.isNotEmpty(dto.getAgencyId()), PickupDispatchTaskEntity::getAgencyId, dto.getAgencyId())
                .eq(ObjectUtil.isNotEmpty(dto.getCourierId()), PickupDispatchTaskEntity::getCourierId, dto.getCourierId())
                .eq(ObjectUtil.isNotEmpty(dto.getTaskType()), PickupDispatchTaskEntity::getTaskType, dto.getTaskType())
                .eq(ObjectUtil.isNotEmpty(dto.getStatus()), PickupDispatchTaskEntity::getStatus, dto.getStatus())
                .eq(ObjectUtil.isNotEmpty(dto.getAssignedStatus()), PickupDispatchTaskEntity::getAssignedStatus, dto.getAssignedStatus())
                .eq(ObjectUtil.isNotEmpty(dto.getSignStatus()), PickupDispatchTaskEntity::getSignStatus, dto.getSignStatus())
                .eq(ObjectUtil.isNotEmpty(dto.getIsDeleted()), PickupDispatchTaskEntity::getIsDeleted, dto.getIsDeleted())
                .between(ObjectUtil.isNotEmpty(dto.getMinEstimatedEndTime()), PickupDispatchTaskEntity::getEstimatedEndTime, dto.getMinEstimatedEndTime(), dto.getMaxEstimatedEndTime())
                .between(ObjectUtil.isNotEmpty(dto.getMinActualEndTime()), PickupDispatchTaskEntity::getActualEndTime, dto.getMinActualEndTime(), dto.getMaxActualEndTime())
                .orderByDesc(PickupDispatchTaskEntity::getUpdated);
        //2.分页查询
        Page<PickupDispatchTaskEntity> result = super.page(iPage, queryWrapper);

        //3.实体类转为dto
        return PageResponse.of(result, PickupDispatchTaskDTO.class);
    }

    @Override
    public List<CourierTaskCountDTO> findCountByCourierIds(List<Long> courierIds, PickupDispatchTaskType pickupDispatchTaskType, String date) {
        return List.of();
    }

    /**
     * 根据订单id查询取派件任务
     *
     * @param orderId  订单id
     * @param taskType 任务类型
     * @return 任务
     */
    @Override
    public List<PickupDispatchTaskEntity> findByOrderId(Long orderId, PickupDispatchTaskType taskType) {
        LambdaQueryWrapper<PickupDispatchTaskEntity> queryWrapper = Wrappers.<PickupDispatchTaskEntity>lambdaQuery()
                .eq(PickupDispatchTaskEntity::getOrderId, orderId)
                .eq(PickupDispatchTaskEntity::getTaskType, taskType)
                .orderByAsc(PickupDispatchTaskEntity::getCreated);
        return super.list(queryWrapper);
    }

    @Override
    public boolean deleteByIds(List<Long> ids) {
        return false;
    }

    @Override
    public Integer todayTasksCount(Long courierId, PickupDispatchTaskType taskType, PickupDispatchTaskStatus status, PickupDispatchTaskIsDeleted isDeleted) {
        return 0;
    }

    @Override
    public List<PickupDispatchTaskDTO> findAll(Long courierId, PickupDispatchTaskType taskType, PickupDispatchTaskStatus taskStatus, PickupDispatchTaskIsDeleted isDeleted) {
        return List.of();
    }

    @Override
    public PickupDispatchTaskStatisticsDTO todayTaskStatistics(Long courierId) {
        return null;
    }
}
