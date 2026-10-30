package com.hotel.solr.service;

import com.hotel.solr.config.SolrProperties;
import com.hotel.solr.framework.exception.BusinessException;
import com.hotel.solr.framework.lang.PageResult;
import com.hotel.solr.model.dto.HotelRpDto;
import com.hotel.solr.model.po.HotelRpDo;
import com.hotel.solr.model.req.HotelPatchReq;
import com.hotel.solr.model.req.HotelRpQueryReq;
import com.hotel.solr.service.builder.HotelQueryBuilder;
import com.hotel.solr.service.converter.HotelRpSolrConverter;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Solr 单 core 数据服务的公共实现：封装文档增删改查、批量操作、
 * 写入提交策略与 Solr 异常转换，子类只需指定对应的 SolrClient。
 *
 * @author gaofeng
 * @date 2026/10/30-17:19
 */
public abstract class AbstractSolrService {

    private final SolrClient solrClient;
    private final SolrProperties properties;
    private final HotelQueryBuilder queryBuilder;

    protected AbstractSolrService(SolrClient solrClient,
                                  SolrProperties properties,
                                  HotelQueryBuilder queryBuilder) {
        this.solrClient = solrClient;
        this.properties = properties;
        this.queryBuilder = queryBuilder;
    }

    public abstract void initCoreData();

    public HotelRpDo create(HotelRpDo hotel) {
        validateHotel(hotel, true);
        Instant now = Instant.now();
        if (hotel.getCreateTime() == null) {
            hotel.setCreateTime(now);
        }
        hotel.setUpdateTime(now);

        SolrInputDocument document = HotelRpSolrConverter.toSolrDocument(hotel);
        executeAction(() -> {
            solrClient.add(document);
            commitIfNeeded();
        });
        return hotel;
    }

    public HotelRpDo replace(String id, HotelRpDo h) {
        validateId(id);
        validateHotel(h, false);
        h.setId(id);

        HotelRpDto existing = findById(id);
        Instant now = Instant.now();
        h.setCreateTime(existing != null && existing.getCreateTime() != null
                ? existing.getCreateTime()
                : now);
        h.setUpdateTime(now);

        SolrInputDocument document = HotelRpSolrConverter.toSolrDocument(h);
        executeAction(() -> {
            solrClient.add(document);
            commitIfNeeded();
        });
        return h;
    }

    public HotelRpDto patch(String id, HotelPatchReq patch) {
        validateId(id);
        if (patch == null) {
            throw new BusinessException(400, "更新内容不能为空");
        }
        if (findById(id) == null) {
            throw new BusinessException(404, "酒店不存在: " + id);
        }
        validateCoordinates(patch.getLongitude(), patch.getLatitude());

        SolrInputDocument document = HotelRpSolrConverter.toAtomicDocument(id, patch);
        document.setField(HotelRpSolrConverter.FIELD_UPDATE_TIME,
                Collections.singletonMap("set", Date.from(Instant.now())));
        executeAction(() -> {
            solrClient.add(document);
            commitIfNeeded();
        });
        return get(id);
    }

    public HotelRpDto get(String id) {
        validateId(id);
        HotelRpDto hotelRpDto = findById(id);
        if (hotelRpDto == null) {
            throw new BusinessException(404, "酒店不存在: " + id);
        }
        return hotelRpDto;
    }

    public void delete(String id) {
        validateId(id);
        if (findById(id) == null) {
            throw new BusinessException(404, "酒店不存在: " + id);
        }
        executeAction(() -> {
            solrClient.deleteById(id);
            commitIfNeeded();
        });
    }

    public PageResult<HotelRpDto> query(HotelRpQueryReq query) {
        HotelRpQueryReq queryReq = queryBuilder.initQueryReq(query);

        SolrQuery solrQuery = buildSolrQuery(queryReq);
        QueryResponse response = execute(() -> solrClient.query(solrQuery));

        int size = solrQuery.getRows();
        int page = solrQuery.getStart() / size + 1;
        List<HotelRpDto> items = extractDocuments(response).stream()
                .map(document -> HotelRpSolrConverter.toHotelForQuery(
                        document, queryReq.getCheckinDate(), queryReq.getDays()))
                .collect(Collectors.toList());
        return PageResult.of(extractTotal(response), page, size, items);
    }

    /**
     * 构建查询条件，子类可按需追加参数（如分组查询）。
     */
    protected SolrQuery buildSolrQuery(HotelRpQueryReq queryReq) {
        return queryBuilder.buildSolrQuery(queryReq);
    }

    /**
     * 提取当前页的文档。默认取普通查询结果，分组查询的子类可覆盖为每组的代表文档。
     */
    protected List<SolrDocument> extractDocuments(QueryResponse response) {
        SolrDocumentList results = response.getResults();
        return results == null ? Collections.emptyList() : new ArrayList<>(results);
    }

    /**
     * 提取总记录数。默认取普通查询的 numFound，分组查询的子类可覆盖为分组总数。
     */
    protected long extractTotal(QueryResponse response) {
        SolrDocumentList results = response.getResults();
        return results == null ? 0 : results.getNumFound();
    }

    public int saveBatch(List<HotelRpDo> docPoList) {
        if (docPoList == null || docPoList.isEmpty()) {
            throw new BusinessException(400, "hotels 不能为空");
        }
        Instant now = Instant.now();
        List<SolrInputDocument> documents = new ArrayList<>(docPoList.size());
        for (HotelRpDo docPo : docPoList) {
            validateHotel(docPo, true);
            if (docPo.getCreateTime() == null) {
                docPo.setCreateTime(now);
            }
            docPo.setUpdateTime(now);
            documents.add(HotelRpSolrConverter.toSolrDocument(docPo));
        }
        executeAction(() -> {
            solrClient.add(documents);
            commitIfNeeded();
        });
        return documents.size();
    }

    public int deleteBatch(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "ids 不能为空");
        }
        for (String id : ids) {
            validateId(id);
        }
        executeAction(() -> {
            solrClient.deleteById(ids);
            commitIfNeeded();
        });
        return ids.size();
    }

    private HotelRpDto findById(String id) {
        return execute(() -> {
            SolrQuery query = new SolrQuery();
            query.setQuery("id:" + ClientUtils.escapeQueryChars(id));
            query.setRows(1);
            QueryResponse response = solrClient.query(query);
            SolrDocumentList results = response.getResults();
            if (results == null || results.isEmpty()) {
                return null;
            }
            SolrDocument document = results.get(0);
            return HotelRpSolrConverter.toHotel(document);
        });
    }

    private void commitIfNeeded() throws SolrServerException, IOException {
        if (properties.isCommitAfterWrite()) {
            solrClient.commit();
        }
    }

    private void validateHotel(HotelRpDo hotel, boolean requireId) {
        if (hotel == null) {
            throw new BusinessException(400, "酒店数据不能为空");
        }
        if (requireId) {
            validateId(hotel.getId());
        }
        if (!StringUtils.hasText(hotel.getName())) {
            throw new BusinessException(400, "name 不能为空");
        }
        validateCoordinates(hotel.getLongitude(), hotel.getLatitude());
    }

    private void validateCoordinates(BigDecimal longitude, BigDecimal latitude) {
        if (longitude == null && latitude == null) {
            return;
        }
        if (longitude == null || latitude == null) {
            throw new BusinessException(400, "longitude 和 latitude 必须同时提供");
        }
        validateCoordinate("longitude", longitude,
                new BigDecimal("-180"), new BigDecimal("180"));
        validateCoordinate("latitude", latitude,
                new BigDecimal("-90"), new BigDecimal("90"));
    }

    private void validateCoordinate(String field, BigDecimal value,
                                    BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new BusinessException(400, field + " 必须在 "
                    + min.toPlainString() + " 到 " + max.toPlainString() + " 之间");
        }
        if (value.scale() < 6) {
            throw new BusinessException(400, field + " 小数点后至少 6 位");
        }
    }

    private void validateId(String id) {
        if (!StringUtils.hasText(id)) {
            throw new BusinessException(400, "id 不能为空");
        }
    }

    private <T> T execute(SolrCallable<T> callable) {
        try {
            return callable.call();
        } catch (SolrServerException | IOException e) {
            throw new BusinessException(502, "Solr 请求失败: " + e.getMessage(), e);
        }
    }

    private void executeAction(SolrAction action) {
        try {
            action.run();
        } catch (SolrServerException | IOException e) {
            throw new BusinessException(502, "Solr 请求失败: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface SolrCallable<T> {
        T call() throws SolrServerException, IOException;
    }

    @FunctionalInterface
    private interface SolrAction {
        void run() throws SolrServerException, IOException;
    }
}
