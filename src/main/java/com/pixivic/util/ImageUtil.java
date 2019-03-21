package com.pixivic.util;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.gm4java.engine.GMException;
import org.gm4java.engine.GMServiceException;
import org.gm4java.engine.support.PooledGMService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ImageUtil {
    private final HttpClient httpClient;
    private final HttpUtil httpUtil;
    private final ZipUtil zipUtil;
    private final ExecutorService executorService;
    private final PooledGMService pooledGMService;
    private HashMap<String, String> formData;
    @Setter
    private String cookie;
    @Value("${imageSave.path}")
    private String path;
    @Value("${sina.username}")
    private String username;
    @Value("${sina.password}")
    private String password;
    @Value("${imgur.client_id}")
    private String imgur_client_id;
    private Long maxSize = 10485760L;//大于10m压缩

    @PostConstruct
    public void init() throws IOException, InterruptedException {
        formData = new HashMap<>() {{
            put("entry", "sso");
            put("gateway", "1");
            put("from", "null");
            put("savestate", "30");
            put("useticket", "0");
            put("pagerefer", "");
            put("vsnf", "1");
            put("su", Base64.getEncoder().encodeToString(username.getBytes()));
            put("service", "sso");
            put("sp", password);
            put("sr", "1920*1080");
            put("encoding", "UTF-8");
            put("cdult", "3");
            put("domain", "sina.com.cn");
            put("prelt", "0");
            put("returntype", "TEXT");
        }};
        setSinaCookies();
    }

    public CompletableFuture<String> deal(String url, String fileName, Integer sanity_level, String type) {
        if (type.equals("ugoira")) {
            url = "https://i.pximg.net/img-zip-ugoira/img/" + url.substring(url.indexOf("/img/") + 5, url.indexOf("0.jpg")) + "600x600.zip";
        }
        return download(url, fileName, sanity_level).whenCompleteAsync((resp, throwable) -> {
            if (Integer.valueOf(resp.headers().firstValue("Content-Length").get()) > maxSize) {//使用返回头看大小
                //压缩(png百分99转jpg,其他则百分80转jpg)
                System.out.println(fileName + " 尺寸过大准备压缩");
                String commend;
                if (resp.body().endsWith(".png")) {
                    commend = "convert -quality 99% -limit threads 4 -limit memory 256MB " + resp.body().toString() + " " + path + fileName + ".jpg";
                } else
                    commend = "convert -quality 80% -limit threads 4 -limit memory 256MB " + resp.body().toString() + " " + path + fileName + ".jpg";
                try {
                    pooledGMService.execute(commend);
                } catch (IOException | GMException | GMServiceException e) {
                    System.err.println("图片处理异常");
                }
                System.out.println(fileName + "压缩完毕");
            }
            if (type.equals("ugoira")) {  //动图通道
                System.out.println(fileName + " 检测为动态图片,准备解压合并");
                //解压+合并gif
                try {
                    zipUtil.unzip(path + fileName, resp.body().toString());
                    pooledGMService.execute("convert -loop 0 -delay 10 -limit threads 4 -limit memory 256MB " + path + fileName + "/*.jpg " + path + fileName + ".gif");
                    System.out.println("gif合成成功");
                } catch (IOException | GMException | GMServiceException e) {
                    System.err.println("图片处理异常");
                }
            }
        }, executorService).thenApply(resp -> {
                    try {
                        if (type.equals("ugoira")) {  //动图通道
                            return uploadToImgur(Paths.get(path, fileName + ".gif")).join();
                        } else {
                            if (sanity_level > 5)    //色图通道
                                return uploadToUploadCC(resp.body()).join();
                            return uploadToSina(resp.body()).join();
                        }
                    } catch (IOException e) {
                        System.err.println("上传异常");
                    }
                    return "上传异常";
                }
        );
    }

    @Scheduled(cron = "0 0 */3 * * ?")
    public void setSinaCookies() throws IOException, InterruptedException {
        HttpRequest oauth = HttpRequest.newBuilder()
                .uri(URI.create("https://login.sina.com.cn/sso/login.php?client=ssologin.js(v1.4.15)&_=1403138799543"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(httpUtil.getPostEntity(formData)))
                .build();
        String responseCookie = httpClient.send(oauth, HttpResponse.BodyHandlers.ofString()).headers().map().get("set-cookie").get(1);
        setCookie(responseCookie);
    }

    private CompletableFuture<String> uploadToSina(Path path) throws IOException {
        HttpRequest upload = HttpRequest.newBuilder()
                .uri(URI.create("http://picupload.weibo.com/interface/pic_upload.php?s=xml&ori=1&data=1&rotate=0&wm=&app=miniblog&mime=image%2Fjpeg"))
                .header("Cookie", cookie)
                .POST(HttpRequest.BodyPublishers.ofFile(path))
                .build();

        return httpClient.sendAsync(upload, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    String s = response.body();
                    if (response.statusCode() == 200 && s.contains("<pid>")) {
                        return "https://ws4.sinaimg.cn/large/" + s.substring(s.indexOf("<pid>") + 5, s.indexOf("</pid>")) + ".jpg";
                    }
                    return "https://ws4.sinaimg.cn/large/007iuyE8gy1g18b8poxhlj30rs12n7wh.jpg";//301图片等待扫描后重上传uploadcc
                });

    }

    private CompletableFuture<String> uploadToUploadCC(Path path) {
        MultiPartBodyPublisher publisher = new MultiPartBodyPublisher()
                .addPart("uploaded_file[]", path);
        HttpRequest upload = HttpRequest.newBuilder()
                .uri(URI.create("https://upload.cc/image_upload"))
                .header("Referer", "https://upload.cc/")
                .header("Content-Type", "multipart/form-data, boundary=" + publisher.getBoundary())
                .POST(publisher.build())
                .build();
        return httpClient.sendAsync(upload, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    String s = response.body();
                    if (response.statusCode() == 200 &&s.contains("url")) {
                        return "https://upload.cc/" + s.substring(s.indexOf("\"url\":\"") + 7, s.indexOf("\",\"thumbnail\"")).replace("\\", "");
                    }
                    System.err.println(s);
                    return "上传失败";
                });

    }

    private CompletableFuture<String> uploadToImgur(Path path) throws IOException {
        HttpRequest upload = HttpRequest.newBuilder()
                .uri(URI.create("https://api.imgur.com/3/image"))
                .header("Authorization", imgur_client_id)
                .POST(HttpRequest.BodyPublishers.ofFile(path))
                .build();
        return httpClient.sendAsync(upload, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    String s = response.body();
                    if (response.statusCode() == 200 && s.contains("link")) {
                        return s.substring(s.indexOf("\"link\":\"") + 8, s.indexOf("\",\"mp4\"")).replace("\\", "");
                    }
                    System.err.println(s);
                    return "上传失败";
                });
    }

    public CompletableFuture<Boolean> scanUrl(String url) {
        HttpRequest upload = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        return httpClient.sendAsync(upload, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::statusCode)
                .thenApply(status -> status.equals(301));
    }

    public CompletableFuture<String> reUpload(String filename) {
        return uploadToUploadCC(Paths.get(path + filename));
    }

    private byte[] combineByteArray(byte[] pre, byte[] data, byte[] post) {
        byte[] body = new byte[pre.length + data.length + post.length];
        System.arraycopy(pre, 0, body, 0, pre.length);
        System.arraycopy(data, 0, body, pre.length, pre.length);
        System.arraycopy(post, 0, body, pre.length + post.length, pre.length);
        return body;
    }

    private CompletableFuture<HttpResponse<Path>> download(String url, String fileName, Integer sanity_level) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("referer", "https://app-api.pixiv.net/")
                .GET()
                .build();
        String fullFileName;
        if (sanity_level > 5)
            fullFileName = fileName + url.substring(url.length() - 4);
        else
            fullFileName = fileName + ".jpg";
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofFile(Paths.get(path, fullFileName)));

    }
}
