package com.sl.ms.carriage.handler;

import com.sl.ms.carriage.domain.dto.WaybillDTO;
import com.sl.ms.carriage.entity.CarriageEntity;
import org.checkerframework.checker.units.qual.C;

/*
 * Date: 2025/1/24 23:31
 * Author: Adrian
 * Version: 1.0
 * Description: 运费模板处理链的抽象定义
 * */
public abstract class AbstractCarriageChainHandler {

    private AbstractCarriageChainHandler nextHandler;

    /**
     * 执行过滤方法，通过输入参数查找运费模板
     *
     * @param waybillDTO 输入参数
     * @return 运费模板
     */
    public abstract CarriageEntity doHandler(WaybillDTO waybillDTO);

    /**
     * 设置下游Handler
     *
     * @param nextHandler 下游Handler
     */
    public void setNextHandler(AbstractCarriageChainHandler nextHandler) {
        this.nextHandler = nextHandler;
    }

    /**
     * 执行下一个处理器
     *
     * @param waybillDTO     输入参数
     * @param carriageEntity 上个handler处理得到的对象
     * @return
     */
    protected CarriageEntity doNextHandler(WaybillDTO waybillDTO, CarriageEntity carriageEntity) {
        if (nextHandler == null || carriageEntity != null) {
            //如果下游Handler为空 或 上个Handler已经找到运费模板就返回
            return carriageEntity;
        }
        return nextHandler.doHandler(waybillDTO);
    }


}
