package com.hotel.solr.model.req;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class BatchDeleteReq {

    @NotEmpty(message = "ids 不能为空")
    @Size(max = 1000, message = "单次批量删除不能超过 1000 条")
    private List<@NotBlank(message = "id 不能为空") String> ids;
}
