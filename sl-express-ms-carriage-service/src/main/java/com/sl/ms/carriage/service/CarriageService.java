package com.sl.ms.carriage.service;

import com.sl.ms.carriage.domain.dto.CarriageDTO;

import java.util.List;

/*
 * Date: 2025/1/22 23:42
 * Author: Adrian
 * Version: 1.0
 * Description: 运费管理表 服务类
 * */
public interface CarriageService {

    /**
     * 获取全部运费模板
     *
     * @return 运费模板对象列表
     */
    List<CarriageDTO> findAll();

    /**
     * 新增/修改运费模板
     *
     * @param carriageDto 新增/修改运费对象
     *                    必填字段：templateType、transportType
     *                    更新时传入id字段
     */
    CarriageDTO saveOrUpdate(CarriageDTO carriageDto);
}
