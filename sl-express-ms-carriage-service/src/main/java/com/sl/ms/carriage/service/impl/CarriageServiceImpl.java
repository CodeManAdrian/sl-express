package com.sl.ms.carriage.service.impl;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sl.ms.base.api.common.AreaFeign;
import com.sl.ms.carriage.domain.constant.CarriageConstant;
import com.sl.ms.carriage.domain.dto.CarriageDTO;
import com.sl.ms.carriage.domain.dto.WaybillDTO;
import com.sl.ms.carriage.domain.enums.EconomicRegionEnum;
import com.sl.ms.carriage.entity.CarriageEntity;
import com.sl.ms.carriage.enums.CarriageExceptionEnum;
import com.sl.ms.carriage.handler.CarriageChainHandler;
import com.sl.ms.carriage.mapper.CarriageMapper;
import com.sl.ms.carriage.service.CarriageService;
import com.sl.ms.carriage.utils.CarriageUtils;
import com.sl.transport.common.exception.SLException;
import com.sl.transport.common.util.ObjectUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashMap;
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

    @Resource
    private AreaFeign areaFeign;

    @Resource
    private CarriageChainHandler carriageChainHandler;


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


    @Override
    public CarriageDTO compute(WaybillDTO waybillDTO) {
        // 1.查找运费模版
//        CarriageEntity carriageEntity = this.findCarriage(waybillDTO);
        CarriageEntity carriageEntity = this.carriageChainHandler.findCarriage(waybillDTO);
        // 2.计算实际的计费重量,结果保留1为小数
        double computeWeight = this.getComputeWeight(waybillDTO, carriageEntity);
        // 3. 计算运费，首重 + 续重，保留一位小数
        double expense = carriageEntity.getFirstWeight() + carriageEntity.getContinuousWeight() * (computeWeight - 1);
        expense = NumberUtil.round(expense, 1).doubleValue();
        // 4.封装数据返回
        CarriageDTO carriageDTO = CarriageUtils.toDTO(carriageEntity);
        carriageDTO.setExpense(expense);
        carriageDTO.setComputeWeight(computeWeight);
        return carriageDTO;
    }

    /**
     * 根据体积参数与实际重量计算计费重量
     *
     * @param waybillDTO     运费计算对象
     * @param carriageEntity 运费模板
     * @return 计费重量
     */
    private double getComputeWeight(WaybillDTO waybillDTO, CarriageEntity carriageEntity) {
        //计算体积，如果传入体积不需要计算
        Integer volume = waybillDTO.getVolume();
        if (ObjectUtil.isEmpty(volume)) {
            try {
                //长*宽*高计算体积
                volume = waybillDTO.getMeasureLong() * waybillDTO.getMeasureWidth() * waybillDTO.getMeasureHigh();
            } catch (Exception e) {
                //计算出错设置体积为0
                volume = 0;
            }
        }

        // 计算体积重量，体积 / 轻抛系数
        BigDecimal volumeWeight = NumberUtil.div(volume, carriageEntity.getLightThrowingCoefficient(), 1);

        //取大值
        double computeWeight = NumberUtil.max(volumeWeight.doubleValue(), NumberUtil.round(waybillDTO.getWeight(), 1).doubleValue());

        //计算续重，规则：不满1kg，按1kg计费；10kg以下续重以0.1kg计量保留1位小数；10-100kg续重以0.5kg计量保留1位小数；100kg以上四舍五入取整
        if (computeWeight <= 1) {
            return 1;
        }

        if (computeWeight <= 10) {
            return computeWeight;
        }

        // 举例：
        // 108.4kg按照108kg收费
        // 108.5kg按照109kg收费
        // 108.6kg按照109kg收费
        if (computeWeight >= 100) {
            return NumberUtil.round(computeWeight, 0).doubleValue();
        }

        //0.5为一个计算单位，举例：
        // 18.8kg按照19收费，
        // 18.4kg按照18.5kg收费
        // 18.1kg按照18.5kg收费
        // 18.6kg按照19收费
        int integer = NumberUtil.round(computeWeight, 0, RoundingMode.DOWN).intValue();
        if (NumberUtil.sub(computeWeight, integer) == 0) {
            return integer;
        }

        if (NumberUtil.sub(computeWeight, integer) <= 0.5) {
            return NumberUtil.add(integer, 0.5);
        }
        return NumberUtil.add(integer, 1);

    }


    @Override
    public CarriageEntity findByTemplateType(Integer templateType) {
        // 根据模版类型查找
        LambdaQueryWrapper<CarriageEntity> queryWrapper = Wrappers.<CarriageEntity>lambdaQuery()
                .eq(CarriageEntity::getTemplateType, templateType)
                .eq(CarriageEntity::getTransportType, CarriageConstant.REGULAR_FAST);
        return super.getOne(queryWrapper);
    }


    private CarriageDTO saveOrUpdateCarriage(CarriageDTO carriageDto) {
        CarriageEntity carriageEntity = CarriageUtils.toEntity(carriageDto);
        boolean result = super.saveOrUpdate(carriageEntity);
        if (result) {
            return CarriageUtils.toDTO(carriageEntity);
        }
        throw new SLException(CarriageExceptionEnum.SAVE_OR_UPDATE_ERROR);
    }


    private CarriageEntity findCarriage(WaybillDTO waybillDTO) {
        //1. 校验是否为同城
        if (ObjectUtil.equals(waybillDTO.getReceiverCityId(), waybillDTO.getSenderCityId())) {
            //同城
            CarriageEntity carriageEntity = this.findByTemplateType(CarriageConstant.SAME_CITY);
            if (ObjectUtil.isNotEmpty(carriageEntity)) {
                return carriageEntity;
            }
        }

        //2. 校验是否为省内
        //2.1 获取收寄件地址省份id
        Long receiverProvinceId = this.areaFeign.get(waybillDTO.getReceiverCityId()).getParentId();
        Long senderProvinceId = this.areaFeign.get(waybillDTO.getSenderCityId()).getParentId();
        if (ObjectUtil.equal(receiverProvinceId, senderProvinceId)) {
            //2.2 查询同省运费模板
            CarriageEntity carriageEntity = this.findByTemplateType(CarriageConstant.SAME_PROVINCE);
            if (ObjectUtil.isNotEmpty(carriageEntity)) {
                return carriageEntity;
            }
        }

        //3. 校验是否为经济区互寄
        //3.1 获取经济区城市配置枚举
        LinkedHashMap<String, EconomicRegionEnum> EconomicRegionMap = EnumUtil.getEnumMap(EconomicRegionEnum.class);
        EconomicRegionEnum economicRegionEnum = null;
        for (EconomicRegionEnum regionEnum : EconomicRegionMap.values()) {
            //该经济区是否全部包含收发件省id
            boolean result = ArrayUtil.containsAll(regionEnum.getValue(), receiverProvinceId, senderProvinceId);
            if (result) {
                economicRegionEnum = regionEnum;
                break;
            }
        }

        if (ObjectUtil.isNotEmpty(economicRegionEnum)) {
            //3.2 根据类型编码查询
            LambdaQueryWrapper<CarriageEntity> queryWrapper = Wrappers
                    .lambdaQuery(CarriageEntity.class)
                    .eq(CarriageEntity::getTemplateType, CarriageConstant.ECONOMIC_ZONE)
                    .eq(CarriageEntity::getTransportType, CarriageConstant.REGULAR_FAST)
                    .like(CarriageEntity::getAssociatedCity, economicRegionEnum.getCode());

            CarriageEntity carriageEntity = super.getOne(queryWrapper);
            if (ObjectUtil.isNotEmpty(carriageEntity)) {
                return carriageEntity;
            }
        }

        //4. 最后兜底，跨省模板
        return this.findByTemplateType(CarriageConstant.TRANS_PROVINCE);
    }
}