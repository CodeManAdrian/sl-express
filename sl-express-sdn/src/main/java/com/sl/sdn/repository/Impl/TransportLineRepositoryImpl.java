package com.sl.sdn.repository.Impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.StrUtil;
import com.sl.sdn.dto.OrganDTO;
import com.sl.sdn.dto.TransportLineNodeDTO;
import com.sl.sdn.entity.node.AgencyEntity;
import com.sl.sdn.enums.OrganTypeEnum;
import com.sl.sdn.repository.TransportLineRepository;
import com.sl.transport.common.util.BeanUtil;
import org.neo4j.driver.internal.value.PathValue;
import org.neo4j.driver.types.Path;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/*
 * Date: 2025/1/26 18:04
 * Author: Adrian
 * Version: 1.0
 * Description:
 * */
@Component
public class TransportLineRepositoryImpl implements TransportLineRepository {

    @Resource
    private Neo4jClient neo4jClient;

    @Override
    public TransportLineNodeDTO findShortestPath(AgencyEntity start, AgencyEntity end) {
        // 获取网点数据在Neo4j中的类型
        String label = AgencyEntity.class.getAnnotation(Node.class).value()[0];
        // 构造查询语句
        String cypher = StrUtil.format("MATCH path = shortestPath((n:{}) -[*..10]->(m:{}))\n" +
                "WHERE n.bid = $startBid AND m.bid = $endBid\n" +
                "RETURN path", label, label);

        // 执行查询
        Optional<TransportLineNodeDTO> optional = this.neo4jClient.query(cypher)
                .bind(start.getBid()).to("startBid")//绑定参数
                .bind(end.getBid()).to("endBid")//绑定参数
                .fetchAs(TransportLineNodeDTO.class)//返回值映射的对象类型
                .mappedBy(((typeSystem, record) -> {//手动结果映射
                    PathValue pathValue = (PathValue) record.get(0);
                    Path path = pathValue.asPath();
                    TransportLineNodeDTO transportLineNodeDTO = new TransportLineNodeDTO();
                    // 获取路线中的所有节点
                    List<OrganDTO> nodeList = StreamUtil.of(path.nodes())
                            .map(node -> {
                                Map<String, Object> map = node.asMap();
                                OrganDTO organDTO = BeanUtil.toBeanIgnoreError(map, OrganDTO.class);
                                Object location = map.get("location");
                                // 设置经度
                                organDTO.setLongitude(BeanUtil.getProperty(location, "x"));
                                // 设置纬度
                                organDTO.setLatitude(BeanUtil.getProperty(location, "y"));
                                //取第一个标签作为类型
                                OrganTypeEnum organTypeEnum = OrganTypeEnum.valueOf(CollUtil.getFirst(node.labels()));
                                organDTO.setType(organTypeEnum.getCode());
                                return organDTO;
                            })
                            .collect(Collectors.toList());
                    transportLineNodeDTO.setNodeList(nodeList);

                    //计算路线的总成本
                    double cost = StreamUtil.of(path.relationships())
                            .mapToDouble(relationships -> Convert.toDouble(relationships.asMap().get("cost"))).sum();
                    transportLineNodeDTO.setCost(cost);
                    return transportLineNodeDTO;
                }))
                .one();
        // 返回数据
        return optional.orElse(null);
    }
}
