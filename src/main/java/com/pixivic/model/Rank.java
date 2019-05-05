package com.pixivic.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;

@Data
@AllArgsConstructor
public class Rank {
    private ArrayList<Illustration> illustrations;
    private String mode;
    private String Date;
}
