package dev.cheerfun.pixivic.biz.crawler.pixiv.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import dev.cheerfun.pixivic.common.po.Spotlight;
import lombok.Data;

import java.util.List;

/**
 * @author OysterQAQ
 * @version 1.0
 * @date 2019/09/27 18:22
 * @description SpotlightDTO
 */
@Data
public class SpotlightDTO {
    @JsonSetter(value = "spotlight_articles")
    private List<Spotlight> spotlightAticles;
}
