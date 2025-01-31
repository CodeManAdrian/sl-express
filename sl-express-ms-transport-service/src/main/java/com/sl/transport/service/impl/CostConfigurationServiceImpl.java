package com.sl.transport.service.impl;

import com.sl.transport.domain.CostConfigurationDTO;
import com.sl.transport.service.CostConfigurationService;
import org.springframework.stereotype.Service;

import java.util.List;

/*
 * Date: 2025/1/31 16:27
 * Author: Adrian
 * Version: 1.0
 * Description:
 * */
@Service
public class CostConfigurationServiceImpl implements CostConfigurationService {
    @Override
    public List<CostConfigurationDTO> findConfiguration() {
        return List.of();
    }

    @Override
    public void saveConfiguration(List<CostConfigurationDTO> dto) {

    }

    @Override
    public Double findCostByType(Integer type) {
        return 0.0;
    }
}
