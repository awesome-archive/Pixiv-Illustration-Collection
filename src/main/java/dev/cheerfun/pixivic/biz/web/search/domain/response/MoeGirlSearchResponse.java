package dev.cheerfun.pixivic.biz.web.search.domain.response;

import lombok.Data;

import java.util.List;

/**
 * @author OysterQAQ
 * @version 1.0
 * @date 2019/08/14 21:51
 * @description MoeGirlSearchResponse
 */
@Data
public class MoeGirlSearchResponse {
    private List<String> result;
}
