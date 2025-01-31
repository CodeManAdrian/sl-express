package com.sl.transport.repository.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.PageUtil;
import cn.hutool.core.util.StrUtil;
import com.sl.transport.common.util.PageResponse;
import com.sl.transport.domain.TransportLineNodeDTO;
import com.sl.transport.domain.TransportLineSearchDTO;
import com.sl.transport.entity.line.TransportLine;
import com.sl.transport.entity.node.AgencyEntity;
import com.sl.transport.entity.node.BaseEntity;
import com.sl.transport.repository.TransportLineRepository;
import com.sl.transport.utils.TransportLineUtils;
import org.neo4j.driver.Record;
import org.neo4j.driver.internal.value.PathValue;
import org.neo4j.driver.types.Relationship;
import org.neo4j.driver.types.TypeSystem;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /**
     * 转运节点优先规划
     *
     * @param start 开始网点
     * @param end   结束网点
     * @return
     */
    @Override
    public TransportLineNodeDTO findShortestPath(AgencyEntity start, AgencyEntity end) {
        return this.findShortestPath(start, end, 10);
    }

    @Override
    public TransportLineNodeDTO findShortestPath(AgencyEntity start, AgencyEntity end, int depth) {
        // 获取网点数据在Neo4j中的类型
        String type = AgencyEntity.class.getAnnotation(Node.class).value()[0];
        // 构造查询语句
        String cypherQuery = StrUtil.format(
                "MATCH path = shortestPath((start:{}) -[*..{}]-> (end:{}))\n" +
                        "WHERE start.bid = $startId AND end.bid = $endId AND start.status = true AND end.status = true\n" +
                        "RETURN path", type, depth, type
        );
        Collection<TransportLineNodeDTO> transportLineNodeDTOS = this.executeQueryPath(cypherQuery, start, end);
        if (CollUtil.isEmpty(transportLineNodeDTOS)) {
            return null;
        }
        for (TransportLineNodeDTO transportLineNodeDTO : transportLineNodeDTOS) {
            return transportLineNodeDTO;
        }
        return null;
    }

    private List<TransportLineNodeDTO> executeQueryPath(String cypherQuery, AgencyEntity start, AgencyEntity end) {
        return ListUtil.toList(this.neo4jClient.query(cypherQuery)
                .bind(start.getBid()).to("startId")
                .bind(end.getBid()).to("endId")
                .fetchAs(TransportLineNodeDTO.class)
                .mappedBy((typeSystem, record) -> {
                    PathValue pathValue = (PathValue) record.get(0);
                    return TransportLineUtils.convert(pathValue);
                }).all());
    }

    /**
     * 成本优先规划
     *
     * @param start 开始网点
     * @param end   结束网点
     * @param depth 查询深度
     * @param limit 返回路线的数量
     * @return
     */
    @Override
    public List<TransportLineNodeDTO> findPathList(AgencyEntity start, AgencyEntity end, int depth, int limit) {
        //获取网点数据在Neo4j中的类型
        String type = AgencyEntity.class.getAnnotation(Node.class).value()[0];
        // 构造查询语句
        String cypherQuery = StrUtil.format(
                "MATCH path = ((start:{}) -[*..{}]-> (end:{}))\n" +
                        "WHERE start.bid = $startId AND end.bid = $endId AND start.status = true AND end.status = true\n" +
                        "UNWIND relationships(path)\n" +
                        "WITH sum(r.cost) AS r\n" +
                        "RETURN path ORDER BY cost ASC, LENGTH(path) ASC LIMIT {}", type, depth, type, limit
        );
        return this.executeQueryPath(cypherQuery, start, end);
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

    /**
     * 删除路线
     *
     * @param lineId 关系id
     * @return
     */
    @Override
    public Long remove(Long lineId) {
        String cypherQuery = "MATCH () -[r]-> ()\n" +
                "WHERE id(r) = $lineId\n" +
                "DETACH DELETE r\n" +
                "RETURN count(r) AS c";
        Optional<Long> optional = this.neo4jClient.query(cypherQuery)
                .bind(lineId).to("lineId")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> Convert.toLong(record.get("c")))
                .one();
        return optional.orElse(0L);
    }

    /**
     * 查询路线列表
     *
     * @param transportLineSearchDTO 搜索参数
     * @return
     */
    @Override
    public PageResponse<TransportLine> queryPageList(TransportLineSearchDTO transportLineSearchDTO) {
        int page = Math.max(transportLineSearchDTO.getPage(), 1);
        int pageSize = transportLineSearchDTO.getPageSize();
        int skip = (page - 1) * pageSize;
        Map<String, Object> searchParam = BeanUtil.beanToMap(transportLineSearchDTO, false, true);
        MapUtil.removeAny(searchParam, "page", "pageSize");
        //构建查询语句，第一个是查询数据，第二个是查询数量
        String[] cyphers = this.buildPageQueryCypher(searchParam);
        String cypherQuery = cyphers[0];

        //数据
        List<TransportLine> list = ListUtil.toList(this.neo4jClient.query(cypherQuery)
                .bind(skip).to("skip")
                .bind(pageSize).to("limit")
                .bindAll(searchParam)
                .fetchAs(TransportLine.class)
                .mappedBy((typeSystem, record) -> {
                    //封装数据
                    return this.toTransportLine(record);
                }).all());

        // 数据总数
        String countCypher = cyphers[1];
        Long total = this.neo4jClient
                .query(countCypher)
                .bindAll(searchParam)
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> Convert.toLong(record.get("c")))
                .one().orElse(0L);

        PageResponse<TransportLine> pageResponse = new PageResponse<>();
        pageResponse.setPage(page);
        pageResponse.setPageSize(pageSize);
        pageResponse.setItems(list);
        pageResponse.setCounts(total);
        Long pages = Convert.toLong(PageUtil.totalPage(Convert.toInt(total), pageSize));
        pageResponse.setPages(pages);

        return pageResponse;
    }

    private String[] buildPageQueryCypher(Map<String, Object> searchParam) {
        String queryCypher;
        String countCypher;
        if (CollUtil.isEmpty(searchParam)) {
            //无参数
            queryCypher = "MATCH (m) -[r]-> (n) RETURN m,r,n ORDER BY id(r) DESC SKIP $skip LIMIT $limit";
            countCypher = "MATCH () -[r]-> () RETURN count(r) AS c";
        } else {
            //有参数
            String cypherPrefix = "MATCH (m) -[r]-> (n)";
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(cypherPrefix).append(" WHERE 1=1 ");
            for (String key : searchParam.keySet()) {
                Object value = searchParam.get(key);
                if (value instanceof String) {
                    if (StrUtil.isNotBlank(Convert.toStr(value))) {
                        stringBuilder.append(StrUtil.format("AND r.{} CONTAINS ${} \n", key, key));
                    }
                } else {
                    stringBuilder.append(StrUtil.format("AND r.{} = ${} \n", key, key));
                }
            }
            String cypher = stringBuilder.toString();
            queryCypher = cypher + "RETURN m,r,n ORDER BY id(r) DESC SKIP $skip LIMIT $limit";
            countCypher = cypher + "RETURN count(r) AS c";
        }
        return new String[]{queryCypher, countCypher};
    }

    private TransportLine toTransportLine(Record record) {
        org.neo4j.driver.types.Node startNode = record.get("m").asNode();
        org.neo4j.driver.types.Node endNode = record.get("n").asNode();
        Relationship relationship = record.get("r").asRelationship();
        Map<String, Object> map = relationship.asMap();

        TransportLine transportLine = BeanUtil.toBeanIgnoreError(map, TransportLine.class);
        transportLine.setStartOrganName(startNode.get("name").asString());
        transportLine.setStartOrganId(startNode.get("bid").asLong());
        transportLine.setEndOrganName(endNode.get("name").asString());
        transportLine.setEndOrganId(endNode.get("bid").asLong());
        transportLine.setId(relationship.id());
        return transportLine;
    }

    @Override
    public List<TransportLine> queryByIds(Long... ids) {
        String cypherQuery = "MATCH (m) -[r]-> (n)\n" +
                "WHERE id(r) in $ids\n" +
                "RETURN m,r,n";
        return ListUtil.toList(this.neo4jClient.query(cypherQuery)
                .bind(ids).to("ids")
                .fetchAs(TransportLine.class)
                .mappedBy(((typeSystem, record) -> {
                    return this.toTransportLine(record);
                })).all());
    }

    @Override
    public TransportLine queryById(Long id) {
        List<TransportLine> transportLines = this.queryByIds(id);
        if (BeanUtil.isNotEmpty(transportLines)) {
            return transportLines.get(0);
        }
        return null;
    }
}
