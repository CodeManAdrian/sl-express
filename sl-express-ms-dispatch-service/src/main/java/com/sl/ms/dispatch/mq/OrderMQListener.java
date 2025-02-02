package com.sl.ms.dispatch.mq;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.json.JSONUtil;
import com.sl.ms.api.CourierFeign;
import com.sl.ms.base.api.common.MQFeign;
import com.sl.ms.transport.api.DispatchConfigurationFeign;
import com.sl.ms.work.api.PickupDispatchTaskFeign;
import com.sl.ms.work.domain.dto.CourierTaskCountDTO;
import com.sl.ms.work.domain.enums.pickupDispatchtask.PickupDispatchTaskType;
import com.sl.transport.common.constant.Constants;
import com.sl.transport.common.util.BeanUtil;
import com.sl.transport.common.util.ObjectUtil;
import com.sl.transport.common.vo.CourierTaskMsg;
import com.sl.transport.common.vo.OrderMsg;
import com.sl.transport.domain.DispatchConfigurationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单业务消息，接收到新订单后，根据快递员的负载情况，分配快递员
 */
@Slf4j
@Component
public class OrderMQListener {

    @Resource
    private CourierFeign courierFeign;
    @Resource
    private DispatchConfigurationFeign dispatchConfigurationFeign;
    @Resource
    private MQFeign mqFeign;
    @Resource
    private PickupDispatchTaskFeign pickupDispatchTaskFeign;


    /**
     * 如果有多个快递员，需要查询快递员今日的取派件数，根据此数量进行计算
     * 计算的逻辑：优先分配取件任务少的，取件数相同的取第一个分配
     * <p>
     * 发送生成取件任务时需要计算时间差，如果小于2小时，实时发送；大于2小时，延时发送
     * 举例：
     * 1、现在10:30分，用户期望：11:00 ~ 12:00上门，实时发送
     * 2、现在10:30分，用户期望：13:00 ~ 14:00上门，延时发送，12点发送消息，延时1.5小时发送
     *
     * @param msg 消息内容
     */
    @RabbitListener(bindings = @QueueBinding(value = @Queue(name = Constants.MQ.Queues.DISPATCH_ORDER_TO_PICKUP_DISPATCH_TASK), exchange = @Exchange(name = Constants.MQ.Exchanges.ORDER_DELAYED, type = ExchangeTypes.TOPIC, delayed = Constants.MQ.DELAYED), key = Constants.MQ.RoutingKeys.ORDER_CREATE))
    public void listenOrderMsg(String msg) {
        //{"orderId":123, "agencyId": 8001, "taskType":1, "mark":"带包装", "longitude":116.111, "latitude":39.00, "created":1654224658728, "estimatedStartTime": 1654224658728}
        log.info("接收到订单的消息 >>> msg = {}", msg);
        //1. 解析消息
        OrderMsg orderMsg = JSONUtil.toBean(msg, OrderMsg.class);
        Long agencyId = orderMsg.getAgencyId();
        Double longitude = orderMsg.getLongitude();
        Double latitude = orderMsg.getLatitude();
        long epochMilli = LocalDateTimeUtil.toEpochMilli(orderMsg.getEstimatedEndTime());
        // 用户期望上门时间
        LocalDateTime estimatedEndTime = orderMsg.getEstimatedEndTime();

        //2. 查询有排班、符合条件的快递员，并且选择快递员
        List<Long> courierIds = this.courierFeign.queryCourierIdListByCondition(agencyId, longitude, latitude, epochMilli);
        Long selectedCourierId = null;
        if (CollUtil.isNotEmpty(courierIds)) {
            // 选择快递员
            selectedCourierId = this.selectCourier(courierIds, orderMsg.getTaskType());
        }

        //3. 如果是取件任务，需要计算时间差，来决定是发送实时消息还是延时消息
        // 假设现在的时间是：10:30，用户期望上门时间是13:00 ~ 14:00
        long between = LocalDateTimeUtil.between(LocalDateTimeUtil.now(), orderMsg.getEstimatedEndTime(), ChronoUnit.MINUTES);//当前时间与用户期望上门时间做差值
        DispatchConfigurationDTO dispatchConfiguration = this.dispatchConfigurationFeign.findConfiguration();
        // 系统调度时间
        int dispatchTime = dispatchConfiguration.getDispatchTime() * 60;
        int delay = Constants.MQ.DEFAULT_DELAY;
        // 与系统调度时间做对比,如果调度时间大于差值,则延时消息,否则实时发送
        if (ObjectUtil.equals(orderMsg.getTaskType(), 1) && between > dispatchTime) {
            //延迟消息 14:00 向前推 2小时，得到12:00
            LocalDateTime date = LocalDateTimeUtil.offset(estimatedEndTime, dispatchTime * -1L, ChronoUnit.MINUTES);
            //延迟的时间，单位：毫秒 计算： 1.5小时 * 60分钟 * 60秒 *  1000
            delay = Convert.toInt(LocalDateTimeUtil.between(LocalDateTime.now(), date, ChronoUnit.MILLIS));
        }

        //4. 发送消息，通知work微服务，用于创建快递员取派件任务
        //4.1 构建消息
        CourierTaskMsg courierTaskMsg = BeanUtil.toBeanIgnoreError(orderMsg, CourierTaskMsg.class);
        courierTaskMsg.setCourierId(selectedCourierId);
        courierTaskMsg.setCreated(System.currentTimeMillis());

        //4.2 发送消息
        this.mqFeign.sendMsg(Constants.MQ.Exchanges.PICKUP_DISPATCH_TASK_DELAYED,
                Constants.MQ.RoutingKeys.PICKUP_DISPATCH_TASK_CREATE, courierTaskMsg.toJson(), delay);
    }


    /**
     * 根据当日的任务数选取快递员
     *
     * @param courierIds 快递员列个表
     * @param taskType   任务类型
     * @return 选中的快递员id
     */
    private Long selectCourier(List<Long> courierIds, Integer taskType) {
        // 返回的快递员只有一个
        if (courierIds.size() == 1) {
            return courierIds.get(0);
        }
        String date = DateUtil.date().toDateStr();
        // List<CourierTaskCountDTO> courierTaskCountDTOS = this.pickupDispatchTaskFeign.findCountByCourierIds(courierIds,PickupDispatchTaskType.codeOf(taskType), date);
        // TODO 暂时先模拟实现，后面再做具体实现
        List<CourierTaskCountDTO> courierTaskCountDTOS = this.findCountByCourierIds(courierIds, PickupDispatchTaskType.codeOf(taskType), date);

        if (CollUtil.isEmpty(courierTaskCountDTOS)) {
            //没有查到任务数量，默认给第一个快递员分配任务
            return courierIds.get(0);
        }

        //查看任务数是否与快递员数相同，如果不相同需要补齐，设置任务数为0，这样就可以确保每个快递员都能分配到任务
        if (ObjectUtil.notEqual(courierIds.size(), courierTaskCountDTOS.size())) {
            List<CourierTaskCountDTO> dtoList = StreamUtil.of(courierIds)
                    .filter(courierId -> {
                        int index = CollUtil.indexOf(courierTaskCountDTOS, dto -> ObjectUtil.equals(dto.getCourierId(), courierId));
                        return index == -1;
                    })
                    .map(courierId -> CourierTaskCountDTO.builder()
                            .courierId(courierId)
                            .count(0L).build())
                    .collect(Collectors.toList());
            //补齐到集合中
            courierTaskCountDTOS.addAll(dtoList);
        }

        //按照任务数量从小到大排序
        CollUtil.sortByProperty(courierTaskCountDTOS, "count");
        //选中任务数最小的快递员进行分配
        return courierTaskCountDTOS.get(0).getCourierId();
    }

    /**
     * 根据快递员id列表查询快递员任务数
     *
     * @param courierIds
     * @param pickupDispatchTaskType
     * @param date
     * @return
     */
    private List<CourierTaskCountDTO> findCountByCourierIds(List<Long> courierIds, PickupDispatchTaskType pickupDispatchTaskType, String date) {
        //TODO 模拟实现
        List<CourierTaskCountDTO> list = new ArrayList<>();

        CourierTaskCountDTO courierTaskCountDTO = CourierTaskCountDTO.builder()
                .courierId(courierIds.get(0))
                .count(10L)
                .build();
        list.add(courierTaskCountDTO);

        return list;
    }
}
