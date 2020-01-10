package dev.cheerfun.pixivic.biz.web.rank.mapper;

import dev.cheerfun.pixivic.biz.web.rank.po.Rank;
import dev.cheerfun.pixivic.common.util.json.JsonTypeHandler;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author OysterQAQ
 * @version 1.0
 * @date 2019/09/12 14:27
 * @description RankMapper
 */
@Mapper
public interface RankMapper {

    @Select("select * from ranks where date = #{date} and mode= #{mode} limit 1")
    @Results({
            @Result(property = "date", column = "date"),
            @Result(property = "mode", column = "mode"),
            @Result(property = "data", column = "data", javaType = List.class, typeHandler = JsonTypeHandler.class)
    })
    Rank queryByDateAndMode(String date, String mode);
}
