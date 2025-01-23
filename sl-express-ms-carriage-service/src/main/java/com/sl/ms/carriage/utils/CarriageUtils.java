package com.sl.ms.carriage.utils;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.sl.ms.carriage.domain.dto.CarriageDTO;
import com.sl.ms.carriage.entity.CarriageEntity;
import com.sl.transport.common.util.BeanUtil;

import java.util.List;

/*
 * Date: 2025/1/23 13:04
 * Author: Adrian
 * Version: 1.0
 * Description: 运费模板工具类
 * */
public class CarriageUtils {

    private CarriageUtils() {
        //不允许实例化此类
    }

    public static CarriageEntity toEntity(CarriageDTO carriageDTO) {
        CarriageEntity carriageEntity = BeanUtil.toBean(carriageDTO, CarriageEntity.class);
        // 关联城市以逗号分割存储到数据库
        String associatedCity = CollUtil.join(carriageDTO.getAssociatedCityList(), ",");
        carriageEntity.setAssociatedCity(associatedCity);
        return carriageEntity;
    }

    public static CarriageDTO toDTO(CarriageEntity carriageEntity) {
        CarriageDTO carriageDTO = BeanUtil.toBean(carriageEntity, CarriageDTO.class);
        List<String> associatedCityList = StrUtil.split(carriageEntity.getAssociatedCity(), ",");
        carriageDTO.setAssociatedCityList(associatedCityList);
        return carriageDTO;
    }
}
