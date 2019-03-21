package com.pixivic.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Rank {
    private Illustration[] illustrations;
    private String mode;
    private String Date;
}
