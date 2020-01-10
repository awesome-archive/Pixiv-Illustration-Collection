package dev.cheerfun.pixivic.biz.web.user.po;

import lombok.Data;

/**
 * @author OysterQAQ
 * @version 1.0
 * @date 2019/09/19 9:33
 * @description FollowedRelation
 */
@Data
public class FollowedRelation {
    private int userId;
    private int artistId;
}
