package com.pixivic.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pixivic.model.Illustration;
import com.pixivic.model.Rank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.NoSuchAlgorithmException;

@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class IllustrationsUtil {
    private final HttpClient httpClient;
    private final HeaderUtil headerUtil;
    private final OauthUtil oauthUtil;
    @Value("${webClient.domain}")
    private String domain;
    @Value("${webClient.checkWord}")
    private String checkWord;

    public Illustration[] getIllustrations(String mode, String date) throws NoSuchAlgorithmException, IOException, InterruptedException {
        String data;
        Illustration[][] illustrations = new Illustration[7][];
        Illustration[] illustrationsLIst;
        for (int i = 0; i < 7; i++) {
            illustrations[i] = jsonToObject(getIllustrationsJson(mode, date, i));
        }
        illustrationsLIst = arrangeArray(illustrations);
        return illustrationsLIst;
    }

    private String getIllustrationsJson(String mode, String date, Integer index) throws NoSuchAlgorithmException, IOException, InterruptedException {
        HttpRequest.Builder uri = HttpRequest.newBuilder()
                .uri(URI.create("https://search.api.pixivic.com/v1/illust/ranking?mode=" + mode + "&offset=" + index * 30 + "&date=" + date));
        headerUtil.decorateHeader(uri);
        HttpRequest getRank = uri
                .header("Authorization", "Bearer " + oauthUtil.getAccess_token())
                .GET()
                .build();
        return httpClient.send(getRank, HttpResponse.BodyHandlers.ofString()).body();
    }

    //转换日排行json数据
    private Illustration[] jsonToObject(String data) throws IOException {
        data = data.replace("{\"illusts\":", "");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper.readValue(data, Illustration[].class);
    }

    //整理日排行插画数组
    private Illustration[] arrangeArray(Illustration[][] illustrations) {
        int count = 0;
        int index = 0;
        for (Illustration[] illustration : illustrations) {
            count += illustration.length;
        }
        Illustration[] data = new Illustration[count];
        for (Illustration[] illustration : illustrations) {
            System.arraycopy(illustration, 0, data, index, illustration.length);
            index += illustration.length;
        }
        for (int i = 0; i < data.length; i++) {
            data[i].setRank(i);
        }
        return data;
    }

    public String postToWebClient(Illustration[] illustrations, String mode, String date) throws IOException, InterruptedException {
        ObjectMapper objectMapper = new ObjectMapper();
        String requestBody = objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(new Rank(illustrations, mode, date));
        HttpRequest.Builder uri = HttpRequest.newBuilder()
                .uri(URI.create("http://" + domain + "/ranks"));
        HttpRequest getRank = uri
                .header("checkWord", "1111")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        return httpClient.send(getRank, HttpResponse.BodyHandlers.ofString()).body();
    }

}
