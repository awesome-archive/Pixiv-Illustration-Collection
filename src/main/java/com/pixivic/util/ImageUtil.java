package com.pixivic.util;

import com.pixivic.model.DefaultDownloadHttpResponse;
import com.pixivic.model.DefaultUploadHttpResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.gm4java.engine.GMException;
import org.gm4java.engine.GMServiceException;
import org.gm4java.engine.support.PooledGMService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ImageUtil {
    private final HttpClient httpClient;
    private final HttpUtil httpUtil;
    private final ZipUtil zipUtil;
    private final PooledGMService pooledGMService;
    private final DefaultDownloadHttpResponse defaultDownloadHttpResponse;
    private final DefaultUploadHttpResponse defaultUploadHttpResponse;
    private HashMap<String, String> formData;
    private Random random;
    @Setter
    private String cookie;
    @Getter
    @Setter
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
        random = new Random(47);
    }

    public CompletableFuture<String> deal(String url, String fileName, Integer sanity_level, String type) throws IOException, InterruptedException {
        if (type.equals("ugoira")) {
            url = "https://i.pximg.net/img-zip-ugoira/img/" + url.substring(url.indexOf("/img/") + 5, url.length() - 5) + "600x600.zip";
        }
        return download(url, fileName, sanity_level, type).whenComplete((resp, throwable) -> {
            Integer respSize = Integer.valueOf(resp.headers().firstValue("Content-Length").get());
            if (respSize > maxSize) {//使用返回头看大小
                //压缩(png百分99转jpg,其他则百分80转jpg)
                System.out.println("\n" + fileName + " 尺寸过大准备进行压缩---------------");
                Integer quality;
                if (resp.body().endsWith(".png")) {
                    if (respSize > maxSize * 2)
                        quality = 70;
                    else
                        quality = 99;
                } else
                    quality = 80;
                try {
                    pooledGMService.execute("convert -quality " + quality + "% -limit threads 4 -limit memory 256MB " + resp.body().toString() + " " + Paths.get(path, fileName) + ".jpg");
                } catch (IOException | GMException | GMServiceException e) {
                    System.err.println("图片处理异常");
                }
                System.out.println(fileName + "压缩完毕--------------------------------------------");
            }
            if (type.equals("ugoira")) {  //动图通道
                System.out.println(fileName + " 检测为动态图片,准备下载解压ZIP并合并为GIF文件");
                //解压+合并gif
                try {
                    zipUtil.unzip(Paths.get(path, fileName), resp.body().toString());
                    String s = Paths.get(path, fileName).toString();
                    pooledGMService.execute("convert -loop 0 -delay 10 -limit threads 4 -limit memory 256MB " + s + "/*.jpg " + s + ".gif");
                    System.out.println("合成GIF成功,等待上传----------------------------");
                } catch (IOException | GMException | GMServiceException e) {
                    System.err.println("图片处理异常");
                }
            }
        }).thenApply(HttpResponse::body).thenCompose(body -> {
                    try {
                        if (type.equals("ugoira")) {  //动图通道
                            // return uploadToVim_cn(Paths.get(path, fileName + ".gif"));
                            //return uploadToImgur(Paths.get(path, fileName + ".gif"));
                            Path path = Paths.get(this.path, fileName + ".gif");
                            if (Files.size(path) < maxSize)
                                return uploadToImgBB(path);
                            return CompletableFuture.completedFuture("https://ws2.sinaimg.cn/large/007iuyE8gy1g1u0evxm1bg30a00dcmx4.gif");
                        } else {
                            if (sanity_level > 5)
                                if (System.currentTimeMillis() % 2 == 0)//色图通道
                                    return uploadToUploadCC(body);
                                else return uploadToImgBB(body);
                            // return uploadToVim_cn(resp.body());
                            return uploadToSina(body);
                        }
                    } catch (IOException e) {
                        return CompletableFuture.completedFuture("上传异常");
                    }
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
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36")
                .POST(HttpRequest.BodyPublishers.ofFile(path))
                .build();

        return httpClient.sendAsync(upload, HttpResponse.BodyHandlers.ofString()).completeOnTimeout(defaultUploadHttpResponse, 4, TimeUnit.MINUTES)
                .thenApply(response -> {
                    String body = response.body();
                    if (response.statusCode() == 200 && body.contains("<pid>")) {
                        return "https://ws4.sinaimg.cn/large/" + body.substring(body.indexOf("<pid>") + 5, body.indexOf("</pid>")) + ".jpg";
                    }
                    return "https://ws4.sinaimg.cn/large/007iuyE8gy1g18b8poxhlj30rs12n7wh.jpg";//301图片等待扫描后重上传uploadcc
                });
    }

    public CompletableFuture<String> uploadToUploadCC(Path path) {
        HttpEntity httpEntity = MultipartEntityBuilder.create()
                .addBinaryBody("uploaded_file[]", path.toFile(), ContentType.IMAGE_PNG, path.getFileName().toString())
                .setBoundary("******")
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .build();
        HttpRequest upload = HttpRequest.newBuilder()
                .uri(URI.create("https://upload.cc/image_upload"))
                .header("Referer", "https://upload.cc/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36")
                .header("Content-Type", "multipart/form-data, boundary=******")
                .POST(HttpRequest.BodyPublishers.ofByteArray(entityToByteArray(httpEntity)))
                .build();
        return httpClient.sendAsync(upload, HttpResponse.BodyHandlers.ofString()).completeOnTimeout(defaultUploadHttpResponse, 4, TimeUnit.MINUTES)
                .thenApply(response -> {
                    String body = response.body();
                    if (response.statusCode() == 200 && body.contains("url")) {
                        return "https://upload.cc/" + body.substring(body.indexOf("\"url\":\"") + 7, body.indexOf("\",\"thumbnail\"")).replace("\\", "");
                    }
                    System.err.println(path + "上传到uploadCC失败");
                    return "上传失败" + path;
                });

    }

    public CompletableFuture<String> uploadToImgBB(Path path) {
        HttpEntity httpEntity = MultipartEntityBuilder.create()
                .addBinaryBody("source", path.toFile(), ContentType.IMAGE_GIF, path.getFileName().toString())
                .addTextBody("type", "file")
                .addTextBody("action", "upload")
                .setBoundary("******")
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .build();
        HttpRequest upload = HttpRequest.newBuilder()
                .uri(URI.create("https://zh-cn.imgbb.com/json"))
                .header("Referer", "https://zh-cn.imgbb.com/")
                .header("Origin", "https://zh-cn.imgbb.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36")
                .header("Content-Type", "multipart/form-data, boundary=******")
                .POST(HttpRequest.BodyPublishers.ofByteArray(entityToByteArray(httpEntity)))
                .build();
        return httpClient.sendAsync(upload, HttpResponse.BodyHandlers.ofString()).completeOnTimeout(defaultUploadHttpResponse, 4, TimeUnit.MINUTES)
                .thenApply(response -> {
                    String body = response.body();
                    if (response.statusCode() == 200 && body.contains("image")) {
                        return body.substring(body.indexOf("\"url\":\"") + 7, body.indexOf("\",\"size_formatted")).replace("\\", "");
                    }
                    System.err.println(path + "上传到ImgBB失败");
                    return "上传失败" + path;
                });
    }

    private CompletableFuture<String> uploadToImgur(Path path) throws IOException {
        HttpRequest upload = HttpRequest.newBuilder()
                .uri(URI.create("https://api.imgur.com/3/image"))
                .header("Authorization", imgur_client_id)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36")
                .POST(HttpRequest.BodyPublishers.ofFile(path))
                .build();
        return httpClient.sendAsync(upload, HttpResponse.BodyHandlers.ofString()).completeOnTimeout(defaultUploadHttpResponse, 4, TimeUnit.MINUTES)
                .thenApply(response -> {
                    String s = response.body();
                    if (response.statusCode() == 200 && s.contains("link")) {
                        return s.substring(s.indexOf("\"link\":\"") + 8, s.indexOf("\",\"mp4\"")).replace("\\", "");
                    }
                    System.err.println(path + "上传到imgur失败");
                    return "上传失败" + path;
                });
    }

    public void uploadToVim_cn(Path path) throws IOException, InterruptedException {
        HttpEntity httpEntity = MultipartEntityBuilder.create()
                .addBinaryBody("image", path.toFile(), ContentType.IMAGE_PNG, path.getFileName().toString())
                .setBoundary("******")
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .build();
        HttpRequest upload = HttpRequest.newBuilder()
                .uri(URI.create("https://img.vim-cn.com/"))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36")
                .header("Content-Type", "multipart/form-data, boundary=******")
                .POST(HttpRequest.BodyPublishers.ofByteArray(entityToByteArray(httpEntity)))
                .build();
        System.out.println(httpClient.send(upload, HttpResponse.BodyHandlers.ofString()).body());
    }

    public CompletableFuture<Boolean> scanUrl(String url) {
        HttpRequest upload = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36")
                .GET()
                .build();
        return httpClient.sendAsync(upload, HttpResponse.BodyHandlers.discarding())
                .thenApply(HttpResponse::statusCode)
                .thenApply(status -> !status.equals(200)).completeOnTimeout(false, 2, TimeUnit.MINUTES);
    }

    public CompletableFuture<String> reUpload(String filename) {
        if (System.currentTimeMillis() % 2 == 0)
            return uploadToImgBB(Paths.get(path, filename)).completeOnTimeout("https://ws4.sinaimg.cn/large/007iuyE8gy1g18b8poxhlj30rs12n7wh.jpg", 5, TimeUnit.MINUTES);
        return uploadToUploadCC(Paths.get(path, filename)).completeOnTimeout("https://ws4.sinaimg.cn/large/007iuyE8gy1g18b8poxhlj30rs12n7wh.jpg", 5, TimeUnit.MINUTES);
    }

    public CompletableFuture<HttpResponse<Path>> download(String url, String fileName, Integer sanity_level, String type) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("referer", "https://app-api.pixiv.net/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36")
                .GET()
                .build();
        String fullFileName;
        if (sanity_level > 5)
            fullFileName = fileName + url.substring(url.length() - 4);
        else {
            if (type.equals("ugoira"))
                fullFileName = fileName + ".zip";
            else
                fullFileName = fileName + ".jpg";
        }

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofFile(Paths.get(path, fullFileName))).completeOnTimeout(defaultDownloadHttpResponse, 4, TimeUnit.MINUTES);
    }

    private byte[] entityToByteArray(HttpEntity httpEntity) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            httpEntity.writeTo(byteArrayOutputStream);
            byteArrayOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return byteArrayOutputStream.toByteArray();
    }

    private byte[] combineByteArray(byte[] pre, byte[] data, byte[] post) {
        byte[] body = new byte[pre.length + data.length + post.length];
        System.arraycopy(pre, 0, body, 0, pre.length);
        System.arraycopy(data, 0, body, pre.length, pre.length);
        System.arraycopy(post, 0, body, pre.length + post.length, pre.length);
        return body;
    }
}
