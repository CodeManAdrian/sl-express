package com.sl.ms.work.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sl.ms.work.domain.dto.TransportOrderDTO;
import com.sl.ms.work.domain.dto.request.TransportOrderQueryDTO;
import com.sl.ms.work.domain.dto.response.TransportOrderStatusCountDTO;
import com.sl.ms.work.domain.enums.transportorder.TransportOrderStatus;
import com.sl.ms.work.entity.PickupDispatchTaskEntity;
import com.sl.ms.work.entity.TransportOrderEntity;
import com.sl.ms.work.mapper.TaskPickupDispatchMapper;
import com.sl.ms.work.mapper.TransportOrderMapper;
import com.sl.ms.work.service.TransportOrderService;
import com.sl.transport.common.util.PageResponse;
import com.sl.transport.common.vo.OrderMsg;
import org.springframework.stereotype.Service;

import java.util.List;

/*
 * Date: 2025/2/2 23:52
 * Author: Adrian
 * Version: 1.0
 * Description:
 * */
@Service
public class TransportOrderServiceImpl extends ServiceImpl<TransportOrderMapper, TransportOrderEntity> implements TransportOrderService {
    @Override
    public TransportOrderEntity orderToTransportOrder(Long orderId) {
        return null;
    }

    @Override
    public Page<TransportOrderEntity> findByPage(TransportOrderQueryDTO transportOrderQueryDTO) {
        return null;
    }

    @Override
    public TransportOrderEntity findByOrderId(Long orderId) {
        return null;
    }

    @Override
    public List<TransportOrderEntity> findByOrderIds(Long[] orderIds) {
        return List.of();
    }

    @Override
    public List<TransportOrderEntity> findByIds(String[] ids) {
        return List.of();
    }

    @Override
    public List<TransportOrderEntity> searchById(String id) {
        return List.of();
    }

    @Override
    public boolean updateStatus(List<String> ids, TransportOrderStatus transportOrderStatus) {
        return false;
    }

    @Override
    public boolean updateByTaskId(Long taskId) {
        return false;
    }

    @Override
    public List<TransportOrderStatusCountDTO> findStatusCount() {
        return List.of();
    }

    @Override
    public void sendPickupDispatchTaskMsgToDispatch(TransportOrderEntity transportOrder, OrderMsg orderMsg) {

    }

    @Override
    public PageResponse<TransportOrderDTO> pageQueryByTaskId(Integer page, Integer pageSize, String taskId, String transportOrderId) {
        return null;
    }
}
