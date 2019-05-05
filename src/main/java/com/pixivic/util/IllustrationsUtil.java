package com.pixivic.util;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
final public class IllustrationsUtil {
    private final HttpClient httpClient;
    private final HeaderUtil headerUtil;
    private final OauthUtil oauthUtil;
    @Value("${webClient.domain}")
    private String domain;
    @Value("${webClient.checkWord}")
    private String checkWord;
    @Value("${backup.path}")
    private String backupPath;

    public ArrayList<Illustration> getIllustrations(String mode, String date) throws InterruptedException {
        ArrayList<Illustration> illustrations = new ArrayList<>(150);
        final CountDownLatch cd = new CountDownLatch(5);
        IntStream.range(0, 5).parallel().forEach(i -> getIllustrationsJson(mode, date, i).thenAccept(illustration -> {
            illustrations.addAll(illustration);
            cd.countDown();
        }));
        cd.await();
        illustrations.trimToSize();
        IntStream.range(0, illustrations.size()).forEach(index -> illustrations.get(index).setRank(index));
        return illustrations;
    }

    private CompletableFuture<ArrayList<Illustration>> getIllustrationsJson(String mode, String date, Integer index) {
        HttpRequest.Builder uri = HttpRequest.newBuilder()
                .uri(URI.create("https://search.api.pixivic.com/v1/illust/ranking?mode=" + mode + "&offset=" + index * 30 + "&date=" + date));
        headerUtil.decorateHeader(uri);
        HttpRequest getRank = uri
                .header("Authorization", "Bearer " + oauthUtil.getAccess_token())
                .GET()
                .build();
        return httpClient.sendAsync(getRank, HttpResponse.BodyHandlers.ofString()).thenApply(response -> jsonToObject(response.body()));
    }

    //转换日排行json数据
    private ArrayList<Illustration> jsonToObject(String data) {
        data = data.replace("{\"illusts\":", "").substring(0, data.indexOf(",\"next_"));
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            return objectMapper.readValue(data, new TypeReference<ArrayList<Illustration>>() {
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String postToWebClient(ArrayList<Illustration> illustrations, String mode, String date) throws IOException, InterruptedException {
        ObjectMapper objectMapper = new ObjectMapper();
        String requestBody = objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(new Rank(illustrations, mode, date));
        Files.write(Paths.get(backupPath,date+"-"+mode+".json"), requestBody.getBytes(), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        HttpRequest.Builder uri = HttpRequest.newBuilder()
                .uri(URI.create("https://" + domain + "/ranks"));
        HttpRequest getRank = uri
                .header("checkWord", checkWord)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        return httpClient.send(getRank, HttpResponse.BodyHandlers.ofString()).body();
    }

}
