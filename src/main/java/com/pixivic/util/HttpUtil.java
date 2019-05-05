package com.pixivic.util;

import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;

@Component
final public class HttpUtil {
    public String getPostEntity(Map<String, String> param) {
        StringBuilder stringBuilder = new StringBuilder();
        Iterator<Map.Entry<String, String>> iterator = param.entrySet().iterator();
        Map.Entry<String, String> entry;
        while (iterator.hasNext()) {
            entry = iterator.next();
            stringBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        return stringBuilder.toString();
    }
}
