package com.sl.ms.carriage.controller;

import com.sl.ms.carriage.domain.dto.CarriageDTO;
import com.sl.ms.carriage.service.CarriageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/*
 * Date: 2025/1/23 12:03
 * Author: Adrian
 * Version: 1.0
 * Description:
 * */
@Slf4j
@Validated
@RestController
@Api(tags = "运费管理")
@RequestMapping("/carriages")
public class CarriageController {
    @Resource
    private CarriageService carriageService;

    @GetMapping
    @ApiOperation(value = "运费模版列表")
    public List<CarriageDTO> findAll() {
        return this.carriageService.findAll();
    }

    @PostMapping
    @ApiOperation(value = "新增/修改运费模板")
    public CarriageDTO saveOrUpdate(@RequestBody CarriageDTO carriageDto) {
        return this.carriageService.saveOrUpdate(carriageDto);
    }

}
