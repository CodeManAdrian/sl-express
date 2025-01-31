package com.sl.transport.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.sl.transport.common.util.BeanUtil;
import com.sl.transport.common.util.ObjectUtil;
import com.sl.transport.entity.node.BaseEntity;
import com.sl.transport.repository.BaseRepository;
import com.sl.transport.service.IService;
import org.springframework.beans.factory.annotation.Autowired;

/*
 * Date: 2025/1/31 15:38
 * Author: Adrian
 * Version: 1.0
 * Description: 基础服务的实现
 * */
public class ServiceImpl<M extends BaseRepository<T, Long>, T extends BaseEntity> implements IService<T> {

    @Autowired
    private M repository;

    @Override
    public T queryByBid(Long bid) {
        return this.repository.findByBid(bid).orElse(null);
    }

    @Override
    public T create(T t) {
        t.setId(null); //主键id由neo4j生成，不能设置值
        return this.repository.save(t);
    }

    @Override
    public T update(T t) {
        //先查询，后更新
        T entity = this.queryByBid(t.getBid());
        if (ObjectUtil.isEmpty(entity)) {
            return null;
        }
        //将更新的数据复制到entity中
        BeanUtil.copyProperties(t, entity, CopyOptions.create().ignoreNullValue().setIgnoreProperties("id", "bid"));

        //更新
        return this.repository.save(entity);
    }

    @Override
    public Boolean deleteByBid(Long bid) {
        return this.repository.deleteByBid(bid) > 0;
    }
}