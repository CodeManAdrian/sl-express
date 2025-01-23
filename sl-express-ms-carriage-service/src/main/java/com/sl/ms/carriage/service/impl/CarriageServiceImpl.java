package com.sl.ms.carriage.service.impl;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sl.ms.carriage.domain.dto.CarriageDTO;
import com.sl.ms.carriage.entity.CarriageEntity;
import com.sl.ms.carriage.mapper.CarriageMapper;
import com.sl.ms.carriage.service.CarriageService;
import com.sl.transport.common.util.BeanUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/*
 * Date: 2025/1/22 23:44
 * Author: Adrian
 * Version: 1.0
 * Description:
 * */
@Slf4j
@Service
public class CarriageServiceImpl extends ServiceImpl<CarriageMapper, CarriageEntity> implements CarriageService {
    @Override
    public List<CarriageDTO> findAll() {
        // 构造查询条件,按照时间倒序排序
        LambdaQueryWrapper<CarriageEntity> queryWrapper = Wrappers.<CarriageEntity>lambdaQuery()
                .orderByDesc(CarriageEntity::getCreated);

        // 查询数据库
        List<CarriageEntity> list = super.list(queryWrapper);

        // 转化对象,返回集合数据
        return CollStreamUtil.toList(list, carriageEntity -> {
            // 关联城市数据按照逗号分割成集合
            CarriageDTO carriageDTO = BeanUtil.toBean(carriageEntity, CarriageDTO.class);
            // 关联城市数据按照逗号分割成集合
            carriageDTO.setAssociatedCityList(StrUtil.split(carriageEntity.getAssociatedCity(), ","));
            return carriageDTO;
        });
    }
}
