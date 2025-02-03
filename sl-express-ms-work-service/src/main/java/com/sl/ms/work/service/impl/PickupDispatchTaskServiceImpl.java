package com.sl.ms.work.service.impl;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sl.ms.oms.api.OrderFeign;
import com.sl.ms.oms.enums.OrderStatus;
import com.sl.ms.work.domain.dto.CourierTaskCountDTO;
import com.sl.ms.work.domain.dto.PickupDispatchTaskDTO;
import com.sl.ms.work.domain.dto.request.PickupDispatchTaskPageQueryDTO;
import com.sl.ms.work.domain.dto.response.PickupDispatchTaskStatisticsDTO;
import com.sl.ms.work.domain.enums.WorkExceptionEnum;
import com.sl.ms.work.domain.enums.pickupDispatchtask.*;
import com.sl.ms.work.entity.PickupDispatchTaskEntity;
import com.sl.ms.work.mapper.TaskPickupDispatchMapper;
import com.sl.ms.work.service.PickupDispatchTaskService;
import com.sl.transport.common.exception.SLException;
import com.sl.transport.common.util.PageResponse;
import com.sl.transport.common.vo.OrderMsg;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/*
 * Date: 2025/2/2 18:17
 * Author: Adrian
 * Version: 1.0
 * Description:
 * */
@Service
public class PickupDispatchTaskServiceImpl extends ServiceImpl<TaskPickupDispatchMapper, PickupDispatchTaskEntity> implements PickupDispatchTaskService {

    @Resource
    private OrderFeign orderFeign;

    /**
     * 更新取派件状态，不允许 NEW 状态
     *
     * @param pickupDispatchTaskDTO 修改的数据
     * @return 是否成功
     */
    @Override
    @Transactional
    public Boolean updateStatus(PickupDispatchTaskDTO pickupDispatchTaskDTO) {
        WorkExceptionEnum paramError = WorkExceptionEnum.PICKUP_DISPATCH_TASK_PARAM_ERROR;//取派件任务的参数不正确
        if (ObjectUtil.hasEmpty(pickupDispatchTaskDTO.getId(), pickupDispatchTaskDTO.getStatus())) {
            throw new SLException("更新取派件任务状态，id或status不能为空", paramError.getCode());
        }

        PickupDispatchTaskEntity pickupDispatchTask = super.getById(pickupDispatchTaskDTO.getId());

        if (ObjectUtil.isEmpty(pickupDispatchTask)) {
            throw new SLException(WorkExceptionEnum.PICKUP_DISPATCH_TASK_NOT_FOUND);
        }

        // 任务状态
        switch (pickupDispatchTaskDTO.getStatus()) {
            case NEW: {
                // 任务状态为新
                throw new SLException(WorkExceptionEnum.PICKUP_DISPATCH_TASK_STATUS_NOT_NEW);
            }
            case COMPLETED: {
                //任务完成
                pickupDispatchTask.setStatus(PickupDispatchTaskStatus.COMPLETED);
                //设置完成时间
                pickupDispatchTask.setActualEndTime(LocalDateTime.now());

                //如果是派件任务的完成，已签收需要设置签收状态和签收人，拒收只需要设置签收状态
                if (PickupDispatchTaskType.DISPATCH == pickupDispatchTask.getTaskType()) {
                    if (ObjectUtil.isEmpty(pickupDispatchTaskDTO.getSignStatus())) {
                        throw new SLException("完成派件任务，签收状态不能为空", paramError.getCode());
                    }
                    pickupDispatchTask.setSignStatus(pickupDispatchTaskDTO.getSignStatus());

                    if (PickupDispatchTaskSignStatus.RECEIVED == pickupDispatchTaskDTO.getSignStatus()) {
                        if (ObjectUtil.isEmpty(pickupDispatchTaskDTO.getSignRecipient())) {
                            throw new SLException("完成签收，签收人不能为空", paramError.getCode());
                        }
                        pickupDispatchTask.setSignRecipient(pickupDispatchTaskDTO.getSignRecipient());
                    }
                }
                break;
            }
            case CANCELLED: {
                //任务取消
                if (ObjectUtil.isEmpty(pickupDispatchTaskDTO.getCancelReason())) {
                    throw new SLException("取消任务，原因不能为空", paramError.getCode());
                }
                pickupDispatchTask.setStatus(PickupDispatchTaskStatus.CANCELLED);
                pickupDispatchTask.setCancelReason(pickupDispatchTaskDTO.getCancelReason());
                pickupDispatchTask.setCancelReasonDescription(pickupDispatchTaskDTO.getCancelReasonDescription());
                pickupDispatchTask.setCancelTime(LocalDateTime.now());

                if (pickupDispatchTaskDTO.getCancelReason() == PickupDispatchTaskCancelReason.RETURN_TO_AGENCY) {
                    //发送分配快递员派件任务的消息
                    OrderMsg orderMsg = OrderMsg.builder()
                            .agencyId(pickupDispatchTask.getAgencyId())
                            .orderId(pickupDispatchTask.getOrderId())
                            .created(DateUtil.current())
                            .taskType(PickupDispatchTaskType.PICKUP.getCode()) //取件任务
                            .mark(pickupDispatchTask.getMark())
                            .estimatedEndTime(pickupDispatchTask.getEstimatedEndTime())
                            .build();

                    //发送消息（取消任务发生在取件之前，没有运单，参数直接填入null）
                    //TODO 目前还没有实现，暂时先注释掉
                    // this.transportOrderService.sendPickupDispatchTaskMsgToDispatch(null, orderMsg);
                } else if (pickupDispatchTaskDTO.getCancelReason() == PickupDispatchTaskCancelReason.CANCEL_BY_USER) {
                    //原因是用户取消，则订单状态改为取消
                    orderFeign.updateStatus(ListUtil.of(pickupDispatchTask.getOrderId()), OrderStatus.CANCELLED.getCode());
                } else {
                    //其他原因则关闭订单
                    orderFeign.updateStatus(ListUtil.of(pickupDispatchTask.getOrderId()), OrderStatus.CLOSE.getCode());
                }
                break;
            }
            default: {
                throw new SLException("其他未知状态，不能完成更新操作", paramError.getCode());
            }
        }

        //TODO 发送消息，同步更新快递员任务
        return super.updateById(pickupDispatchTask);
    }

    /**
     * 改派快递员
     *
     * @param ids               任务id列表
     * @param originalCourierId 原快递员id
     * @param targetCourierId   目标快递员id
     * @return 是否成功
     */
    @Override
    public Boolean updateCourierId(List<Long> ids, Long originalCourierId, Long targetCourierId) {
        // 任务id列表、原快递员id、目标快递员id不能为空
        if (ObjectUtil.hasEmpty(ids, targetCourierId, originalCourierId)) {
            throw new SLException(WorkExceptionEnum.UPDATE_COURIER_PARAM_ERROR);
        }
        // 目标快递员id不能与原快递员id一致
        if (ObjectUtil.equal(originalCourierId, targetCourierId)) {
            throw new SLException(WorkExceptionEnum.UPDATE_COURIER_EQUAL_PARAM_ERROR);
        }

        // 任务列表不能为空
        List<PickupDispatchTaskEntity> entities = super.listByIds(ids);
        if (CollUtil.isEmpty(entities)) {
            throw new SLException(WorkExceptionEnum.PICKUP_DISPATCH_TASK_NOT_FOUND);
        }

        //校验原快递id是否正确（本来无快递员id的情况除外）
        List<PickupDispatchTaskEntity> taskEntityList = StreamUtil.of(entities).filter(entity -> ObjectUtil.isNotEmpty(entity.getCourierId())).filter(entity -> ObjectUtil.notEqual(entity.getCourierId(), originalCourierId)).collect(Collectors.toList());
        if (CollUtil.isNotEmpty(taskEntityList)) {
            throw new SLException(WorkExceptionEnum.UPDATE_COURIER_ID_PARAM_ERROR);
        }

        //批量更新
        List<Long> taskIds = CollStreamUtil.toList(entities, PickupDispatchTaskEntity::getId);
        LambdaUpdateWrapper<PickupDispatchTaskEntity> updateWrapper = Wrappers.<PickupDispatchTaskEntity>lambdaUpdate().in(PickupDispatchTaskEntity::getId, taskIds).set(PickupDispatchTaskEntity::getCourierId, targetCourierId).set(PickupDispatchTaskEntity::getAssignedStatus, PickupDispatchTaskAssignedStatus.DISTRIBUTED);
        boolean result = super.update(updateWrapper);

        if (result) {
            //TODO 发送消息，同步更新快递员任务（ES）
        }
        return result;
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
        LambdaQueryWrapper<PickupDispatchTaskEntity> queryWrapper = Wrappers.<PickupDispatchTaskEntity>lambdaQuery().like(ObjectUtil.isNotEmpty(dto.getId()), PickupDispatchTaskEntity::getId, dto.getId()).like(ObjectUtil.isNotEmpty(dto.getOrderId()), PickupDispatchTaskEntity::getOrderId, dto.getOrderId()).eq(ObjectUtil.isNotEmpty(dto.getAgencyId()), PickupDispatchTaskEntity::getAgencyId, dto.getAgencyId()).eq(ObjectUtil.isNotEmpty(dto.getCourierId()), PickupDispatchTaskEntity::getCourierId, dto.getCourierId()).eq(ObjectUtil.isNotEmpty(dto.getTaskType()), PickupDispatchTaskEntity::getTaskType, dto.getTaskType()).eq(ObjectUtil.isNotEmpty(dto.getStatus()), PickupDispatchTaskEntity::getStatus, dto.getStatus()).eq(ObjectUtil.isNotEmpty(dto.getAssignedStatus()), PickupDispatchTaskEntity::getAssignedStatus, dto.getAssignedStatus()).eq(ObjectUtil.isNotEmpty(dto.getSignStatus()), PickupDispatchTaskEntity::getSignStatus, dto.getSignStatus()).eq(ObjectUtil.isNotEmpty(dto.getIsDeleted()), PickupDispatchTaskEntity::getIsDeleted, dto.getIsDeleted()).between(ObjectUtil.isNotEmpty(dto.getMinEstimatedEndTime()), PickupDispatchTaskEntity::getEstimatedEndTime, dto.getMinEstimatedEndTime(), dto.getMaxEstimatedEndTime()).between(ObjectUtil.isNotEmpty(dto.getMinActualEndTime()), PickupDispatchTaskEntity::getActualEndTime, dto.getMinActualEndTime(), dto.getMaxActualEndTime()).orderByDesc(PickupDispatchTaskEntity::getUpdated);
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
        LambdaQueryWrapper<PickupDispatchTaskEntity> queryWrapper = Wrappers.<PickupDispatchTaskEntity>lambdaQuery().eq(PickupDispatchTaskEntity::getOrderId, orderId).eq(PickupDispatchTaskEntity::getTaskType, taskType).orderByAsc(PickupDispatchTaskEntity::getCreated);
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
