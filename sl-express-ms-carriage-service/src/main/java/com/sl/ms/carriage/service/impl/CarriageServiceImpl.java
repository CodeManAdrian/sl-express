package com.sl.ms.carriage.service.impl;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sl.ms.carriage.domain.constant.CarriageConstant;
import com.sl.ms.carriage.domain.dto.CarriageDTO;
import com.sl.ms.carriage.entity.CarriageEntity;
import com.sl.ms.carriage.enums.CarriageExceptionEnum;
import com.sl.ms.carriage.mapper.CarriageMapper;
import com.sl.ms.carriage.service.CarriageService;
import com.sl.ms.carriage.utils.CarriageUtils;
import com.sl.transport.common.exception.SLException;
import com.sl.transport.common.util.ObjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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
        List<CarriageEntity> CarriageEntityList = super.list(queryWrapper);

        // 转化对象,返回集合数据
        return CollStreamUtil.toList(CarriageEntityList, CarriageUtils::toDTO);
    }


    @Override
    public CarriageDTO saveOrUpdate(CarriageDTO carriageDto) {
        // 根据传入的CarriageDTO对象参数进行查询模板，并且判断是否为经济区
        // 如果是非经济区互寄，需要进一步判断模板是否存在，如果存在需要判断是否为新增操作，如果是新增操作就抛出异常，其他情况都可以进行落库
        // 如果是经济区互寄，需要判断关联城市是否重复，如果重复抛出异常，否则进行落库操作

        //1. 设置查询条件，查询运费模板列表
        LambdaQueryWrapper<CarriageEntity> queryWrapper = Wrappers.<CarriageEntity>lambdaQuery()
                .eq(CarriageEntity::getTemplateType, carriageDto.getTemplateType())
                .eq(CarriageEntity::getTransportType, CarriageConstant.REGULAR_FAST);
        List<CarriageEntity> list = super.list(queryWrapper);

        //2. 判断是否为经济区互寄，如果不是，需要进一步的判断是否重复，如果是，需要判断关联城市是否重复
        if (ObjectUtil.notEqual(carriageDto.getTemplateType(), CarriageConstant.ECONOMIC_ZONE)) {
            if (CollUtil.isNotEmpty(list) && ObjectUtil.isEmpty(carriageDto.getId())) {
                //模板已经存在，数据重复
                throw new SLException(CarriageExceptionEnum.NOT_ECONOMIC_ZONE_REPEAT);
            }

            //更新时判断是否已经存在该类型的模板
            long count = StreamUtil.of(list)
                    .filter(carriageEntity -> ObjectUtil.notEqual(carriageEntity.getId(), carriageDto.getId()))
                    .count();
            if (count > 0) {
                throw new SLException(CarriageExceptionEnum.NOT_ECONOMIC_ZONE_REPEAT);
            }
            //新增或更新
            return this.saveOrUpdateCarriage(carriageDto);
        }

        //3. 经济区互寄，校验关联城市是否有重复
        if (CollUtil.isEmpty(list)) {
            //直接新增或更新
            return this.saveOrUpdateCarriage(carriageDto);
        }

        //判断重复的思路：先将查询出的运费模板中的关联城市收集起来，传入的关联城市是否在此集合中
        //查询其他模板中所有的经济区列表
        List<String> associatedCityList = StreamUtil.of(list)
                //排除掉自己，检查与其他模板是否存在冲突
                .filter(carriageEntity -> ObjectUtil.notEqual(carriageEntity.getId(), carriageDto.getId()))
                //获取关联城市
                .map(CarriageEntity::getAssociatedCity)
                //将关联城市按照逗号分割
                .map(associatedCity -> StrUtil.split(associatedCity, ','))
                //将上面得到的集合展开，得到字符串
                .flatMap(StreamUtil::of)
                //收集到集合中
                .collect(Collectors.toList());

        //取交集，如果存在交集说明重复
        Collection<String> intersection = CollUtil.intersection(associatedCityList, carriageDto.getAssociatedCityList());
        if (CollUtil.isNotEmpty(intersection)) {
            throw new SLException(CarriageExceptionEnum.ECONOMIC_ZONE_CITY_REPEAT);
        }
        //不重复
        return this.saveOrUpdateCarriage(carriageDto);
    }

    private CarriageDTO saveOrUpdateCarriage(CarriageDTO carriageDto) {
        CarriageEntity carriageEntity = CarriageUtils.toEntity(carriageDto);
        boolean result = super.saveOrUpdate(carriageEntity);
        if (result) {
            return CarriageUtils.toDTO(carriageEntity);
        }
        throw new SLException(CarriageExceptionEnum.SAVE_OR_UPDATE_ERROR);
    }
}