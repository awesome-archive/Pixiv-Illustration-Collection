package dev.cheerfun.pixivic.biz.web.spotlight.service;

import dev.cheerfun.pixivic.biz.web.spotlight.mapper.SpotlightBizMapper;
import dev.cheerfun.pixivic.common.po.Illustration;
import dev.cheerfun.pixivic.common.po.Spotlight;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author OysterQAQ
 * @version 1.0
 * @date 2019/09/12 17:01
 * @description RankService
 */
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Cacheable(value = "spotlight")
public class SpotlightBizService {
    private final SpotlightBizMapper spotlightBizMapper;

    public List<Spotlight> query(int page, int pageSize) {
        return spotlightBizMapper.queryList(pageSize, (page - 1) * pageSize);
    }

    public Spotlight queryDetail(int spotlightId) {
        return spotlightBizMapper.queryDetail(spotlightId);
    }

    public List<Illustration> queryIllustrations(int spotlightId) {
        return spotlightBizMapper.queryIllustrations(spotlightId);
    }
}
