package dev.cheerfun.pixivic.biz.web.search.domain.elasticsearch;

import com.fasterxml.jackson.annotation.JsonSetter;
import dev.cheerfun.pixivic.common.po.Illustration;
import lombok.Data;

/**
 * @author OysterQAQ
 * @version 1.0
 * @date 2019/09/09 23:05
 * @description Hit
 */
@Data
public class Hit {
    @JsonSetter("_source")
    private Illustration illustration;
}
