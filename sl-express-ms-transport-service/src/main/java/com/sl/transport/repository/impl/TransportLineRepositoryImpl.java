package com.sl.transport.repository.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import com.sl.transport.common.util.PageResponse;
import com.sl.transport.domain.TransportLineNodeDTO;
import com.sl.transport.domain.TransportLineSearchDTO;
import com.sl.transport.entity.line.TransportLine;
import com.sl.transport.entity.node.AgencyEntity;
import com.sl.transport.entity.node.BaseEntity;
import com.sl.transport.repository.TransportLineRepository;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;

/*
 * Date: 2025/1/31 15:58
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
        return null;
    }

    @Override
    public TransportLineNodeDTO findShortestPath(AgencyEntity start, AgencyEntity end, int depth) {
        return null;
    }

    @Override
    public List<TransportLineNodeDTO> findPathList(AgencyEntity start, AgencyEntity end, int depth, int limit) {
        return List.of();
    }

    /**
     * 查询节点之间的线路
     *
     * @param firstNode  第一个节点
     * @param secondNode 第二个节点
     * @return
     */
    @Override
    public Long queryCount(BaseEntity firstNode, BaseEntity secondNode) {
        String firstNodeType = firstNode.getClass().getAnnotation(Node.class).value()[0];
        String secondNodeType = secondNode.getClass().getAnnotation(Node.class).value()[0];
        String cypherQuery = StrUtil.format(
                "MATCH (m:{}) -[r]- (n:{})\n" +
                        "WHERE m.bid = $firstBid AND n.bid = $secondBid\n" +
                        "RETURN count(r) AS c", firstNodeType, secondNodeType);
        Optional<Long> optional = this.neo4jClient.query(cypherQuery)
                .bind(firstNode.getBid()).to("firstBid")
                .bind(secondNode.getBid()).to("secondBid")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> Convert.toLong(record.get("c")))
                .one();
        return optional.orElse(0L);
    }

    /**
     * 新增路线
     *
     * @param firstNode     第一个节点
     * @param secondNode    第二个节点
     * @param transportLine 路线数据
     * @return
     */
    @Override
    public Long create(BaseEntity firstNode, BaseEntity secondNode, TransportLine transportLine) {
        //获取起点、终点节点的类型
        String firstNodeType = firstNode.getClass().getAnnotation(Node.class).value()[0];
        String secondNodeType = secondNode.getClass().getAnnotation(Node.class).value()[0];
        //定义cypher语句，成对创建路线
        String cypherQuery = StrUtil.format("MATCH (m:{} {bid : $firstBid})\n" +
                "WITH m\n" + "MATCH (n:{} {bid : $secondBid})\n" +
                "WITH m,n\n" +
                "CREATE\n" +
                " (m) -[r:IN_LINE {cost:$cost, number:$number, type:$type, name:$name, distance:$distance, time:$time, extra:$extra, startOrganId:$startOrganId, endOrganId:$endOrganId,created:$created, updated:$updated}]-> (n),\n" +
                " (m) <-[:OUT_LINE {cost:$cost, number:$number, type:$type, name:$name, distance:$distance, time:$time, extra:$extra, startOrganId:$endOrganId, endOrganId:$startOrganId, created:$created, updated:$updated}]- (n)\n" +
                "RETURN count(r) AS c", firstNodeType, secondNodeType);
        //执行
        Optional<Long> optional = this.neo4jClient.query(cypherQuery) //设置执行语句
                .bindAll(BeanUtil.beanToMap(transportLine))//绑定全部参数
                .bind(firstNode.getBid()).to("firstBid") //自定义参数
                .bind(secondNode.getBid()).to("secondBid")//自定义参数
                .fetchAs(Long.class) //指定响应值的类型
                .mappedBy((typeSystem, record) -> Convert.toLong(record.get("c")))//对return值的处理
                .one();//获取一个值
        return optional.orElse(0L);
    }

    @Override
    public Long update(TransportLine transportLine) {
        return null;
    }

    @Override
    public Long remove(Long lineId) {
        return null;
    }

    @Override
    public PageResponse<TransportLine> queryPageList(TransportLineSearchDTO transportLineSearchDTO) {
        return null;
    }

    @Override
    public List<TransportLine> queryByIds(Long... ids) {
        return List.of();
    }

    @Override
    public TransportLine queryById(Long id) {
        return null;
    }
}
