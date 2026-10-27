package com.hotel.solr.service;

import com.hotel.solr.config.SolrProperties;
import com.hotel.solr.dto.HotelDo;
import com.hotel.solr.dto.HotelPatchRequest;
import com.hotel.solr.dto.HotelQuery;
import com.hotel.solr.dto.PageResult;
import com.hotel.solr.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HotelService {

    private final SolrClient solrClient;
    private final SolrProperties properties;
    private final HotelSolrConverter converter;
    private final HotelQueryBuilder queryBuilder;

    public HotelDo create(HotelDo hotelDo) {
        validateHotel(hotelDo, true);
        Instant now = Instant.now();
        if (hotelDo.getCreateTime() == null) {
            hotelDo.setCreateTime(now);
        }
        hotelDo.setUpdateTime(now);

        SolrInputDocument document = converter.toDocument(hotelDo);
        executeAction(() -> {
            solrClient.add(document);
            commitIfNeeded();
        });
        return hotelDo;
    }

    public HotelDo replace(String id, HotelDo hotelDo) {
        validateId(id);
        validateHotel(hotelDo, false);
        hotelDo.setId(id);

        HotelDo existing = findById(id);
        Instant now = Instant.now();
        hotelDo.setCreateTime(existing != null && existing.getCreateTime() != null
                ? existing.getCreateTime()
                : now);
        hotelDo.setUpdateTime(now);

        SolrInputDocument document = converter.toDocument(hotelDo);
        executeAction(() -> {
            solrClient.add(document);
            commitIfNeeded();
        });
        return hotelDo;
    }

    public HotelDo patch(String id, HotelPatchRequest patch) {
        validateId(id);
        if (patch == null) {
            throw new BusinessException(400, "更新内容不能为空");
        }
        if (findById(id) == null) {
            throw new BusinessException(404, "酒店不存在: " + id);
        }
        validateCoordinates(patch.getLongitude(), patch.getLatitude());

        SolrInputDocument document = converter.toAtomicDocument(id, patch);
        document.setField(HotelSolrConverter.FIELD_UPDATE_TIME,
                Collections.singletonMap("set", Date.from(Instant.now())));
        executeAction(() -> {
            solrClient.add(document);
            commitIfNeeded();
        });
        return get(id);
    }

    public HotelDo get(String id) {
        validateId(id);
        HotelDo hotelDo = findById(id);
        if (hotelDo == null) {
            throw new BusinessException(404, "酒店不存在: " + id);
        }
        return hotelDo;
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

    public PageResult<HotelDo> query(HotelQuery query) {
        SolrQuery solrQuery = queryBuilder.build(query);
        QueryResponse response = execute(() -> solrClient.query(solrQuery));
        SolrDocumentList results = response.getResults();

        int size = solrQuery.getRows();
        int page = solrQuery.getStart() / size + 1;
        List<HotelDo> items = results == null
                ? Collections.emptyList()
                : results.stream().map(converter::toHotel).collect(Collectors.toList());
        long total = results == null ? 0 : results.getNumFound();
        return PageResult.of(total, page, size, items);
    }

    public int saveBatch(List<HotelDo> hotelDos) {
        if (hotelDos == null || hotelDos.isEmpty()) {
            throw new BusinessException(400, "hotels 不能为空");
        }
        Instant now = Instant.now();
        List<SolrInputDocument> documents = new ArrayList<>(hotelDos.size());
        for (HotelDo hotelDo : hotelDos) {
            validateHotel(hotelDo, true);
            if (hotelDo.getCreateTime() == null) {
                hotelDo.setCreateTime(now);
            }
            hotelDo.setUpdateTime(now);
            documents.add(converter.toDocument(hotelDo));
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

    private HotelDo findById(String id) {
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
            return converter.toHotel(document);
        });
    }

    private void commitIfNeeded() throws SolrServerException, IOException {
        if (properties.isCommitAfterWrite()) {
            solrClient.commit();
        }
    }

    private void validateHotel(HotelDo hotelDo, boolean requireId) {
        if (hotelDo == null) {
            throw new BusinessException(400, "酒店数据不能为空");
        }
        if (requireId) {
            validateId(hotelDo.getId());
        }
        if (!StringUtils.hasText(hotelDo.getName())) {
            throw new BusinessException(400, "name 不能为空");
        }
        validateCoordinates(hotelDo.getLongitude(), hotelDo.getLatitude());
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
