package com.pixivic.util;

import com.pixivic.model.DefaultDownloadHttpResponse;
import com.pixivic.model.DefaultUploadHttpResponse;
import com.pixivic.model.illust.ImageUrls;
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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    @Setter
    private String cookie;
    @Setter
    @Getter
    @Value("${imageSave.path}")
    private String path;
    @Value("${sina.username}")
    private String username;
    @Value("${sina.password}")
    private String password;
    @Value("${imgur.client_id}")
    private String imgur_client_id;
    @Value("${postimage.apikey}")
    private String postimage_apikey;
    private AtomicInteger SMMSCounter;
    //常量区
    private final static String SINA301URL = "https://wx4.sinaimg.cn/large/007iuyE8gy1g18b8poxhlj30rs12n7wh.jpg";
    private final static Long MAXSIZE_LEVEL1 = 10485760L;//大于10m压缩
    private final static Long MAXSIZE_LEVEL2 = 5242880L;//大于10m压缩
    private final static Long MAXSIZE_LEVEL3 = 2097152L;//大于10m压缩
    private final static String SINAURL = "http://picupload.weibo.com/interface/pic_upload.php?s=xml&ori=1&data=1&rotate=0&wm=&app=miniblog&mime=image%2Fjpeg";
    private final static String UPLOADCCURL = "https://upload.cc/image_upload";
    private final static String IMGBBURL = "https://zh-cn.imgbb.com/json";
    private final static ImageUrls IMAGEURLS301 = new ImageUrls(SINA301URL, SINA301URL);
    private final static String UPLOADCCPRE = "https://upload.cc/";
    private final static String IMGBBPRE = "https://i0.wp.com/";

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
        SMMSCounter = new AtomicInteger(0);

    }

    public CompletableFuture<ImageUrls> deal(String url, String fileName, Integer sanity_level, String type) {
        if (type.equals("ugoira")) {
            url = "https://i.pximg.net/img-zip-ugoira/img/" + url.substring(url.indexOf("/img/") + 5, url.length() - 5) + "600x600.zip";
        }
        return download(url, fileName, sanity_level, type).whenComplete((resp, throwable) -> {
            Integer respSize = Integer.valueOf(resp.headers().firstValue("Content-Length").get());
            if (respSize > MAXSIZE_LEVEL1) {//使用返回头看大小
                //压缩(png百分99转jpg,其他则百分80转jpg)
                System.out.println("\n" + fileName + " 尺寸过大准备进行压缩---------------");
                Integer quality;
                if (resp.body().endsWith(".png")) {
                    if (respSize > MAXSIZE_LEVEL1 * 2)
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
                            Path path = Paths.get(this.path, fileName + ".gif");
                            if (Files.size(path) < MAXSIZE_LEVEL1)//gif大小限制
                                return uploadToImgBB(Paths.get(this.path, fileName + ".gif"));
                            return uploadToSina(Paths.get(this.path, fileName, "000000.jpg"));//过大，仅上传第一帧
                        } else {
                            if (sanity_level < 4) {
                                return uploadToSina(body);
                            }
                            if (System.currentTimeMillis() % 2 == 0)//色图通道
                                return uploadToUploadCC(body);
                            else return uploadToImgBB(body);
                        }
                    } catch (IOException e) {
                        return CompletableFuture.completedFuture(IMAGEURLS301);//上传异常
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

    public CompletableFuture<ImageUrls> uploadToSina(Path path) throws IOException {
        HttpRequest upload = HttpRequest.newBuilder()
                .uri(URI.create(SINAURL))
                .header("Cookie", this.cookie)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36")
                .POST(HttpRequest.BodyPublishers.ofFile(path))
                .build();
        return httpClient.sendAsync(upload, HttpResponse.BodyHandlers.ofString()).completeOnTimeout(defaultUploadHttpResponse, 4, TimeUnit.MINUTES)
                .thenApply(response -> {
                    String body = response.body();
                    if (response.statusCode() == 200 && body.contains("<pid>")) {
                        String pid = body.substring(body.indexOf("<pid>") + 5, body.indexOf("</pid>"));
                        return new ImageUrls("https://ws4.sinaimg.cn/large/" + pid + ".jpg", "https://ws4.sinaimg.cn/mw690/" + pid + ".jpg");
                    }
                    return IMAGEURLS301;//301图片等待扫描后重上传uploadcc
                });
    }

    public CompletableFuture<ImageUrls> uploadToUploadCC(Path path) {
        HttpEntity httpEntity = MultipartEntityBuilder.create()
                .setBoundary("******")
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .addBinaryBody("uploaded_file[]", path.toFile(), ContentType.IMAGE_PNG, path.getFileName().toString())
                .build();
        HttpRequest upload = HttpRequest.newBuilder()
                .uri(URI.create(UPLOADCCURL))
                .header("Referer", "https://upload.cc/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36")
                .header("Content-Type", "multipart/form-data, boundary=******")
                .POST(HttpRequest.BodyPublishers.ofByteArray(entityToByteArray(httpEntity)))
                .build();
        return httpClient.sendAsync(upload, HttpResponse.BodyHandlers.ofString()).completeOnTimeout(defaultUploadHttpResponse, 4, TimeUnit.MINUTES)
                .thenApply(response -> {
                    String body = response.body();
                    if (response.statusCode() == 200 && body.contains("url")) {
                        String url = UPLOADCCPRE + body.substring(body.indexOf("\"url\":\"") + 7, body.indexOf("\",\"thumbnail\":\"")).replace("\\", "");
                        return new ImageUrls(url, url);
                    }
                    System.err.println(path + "上传到uploadCC失败");
                    return new ImageUrls("上传失败" + path, "");
                });
    }

    public CompletableFuture<ImageUrls> uploadToImgBB(Path path) {
        HttpEntity httpEntity = MultipartEntityBuilder.create()
                .addTextBody("type", "file")
                .addTextBody("action", "upload")
                .setBoundary("******")
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .addBinaryBody("source", path.toFile(), ContentType.IMAGE_GIF, path.getFileName().toString())
                .build();
        HttpRequest upload = HttpRequest.newBuilder()
                .uri(URI.create(IMGBBURL))
                .header("Referer", IMGBBURL)
                .header("Origin", IMGBBURL)
                .header("Content-Type", "multipart/form-data, boundary=******")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36")
                .POST(HttpRequest.BodyPublishers.ofByteArray(entityToByteArray(httpEntity)))
                .build();
        return httpClient.sendAsync(upload, HttpResponse.BodyHandlers.ofString()).completeOnTimeout(defaultUploadHttpResponse, 4, TimeUnit.MINUTES)
                .thenApply(response -> {
                    String body = response.body();
                    if (response.statusCode() == 200 && body.contains("image")) {
                        String originalUrl = IMGBBPRE + body.substring(body.indexOf("\"url\":\"https:\\/\\/") + 17, body.indexOf("\",\"size_formatted")).replace("\\", "");
                        String largeUrl = IMGBBPRE + body.substring(body.indexOf("\"url\":\"https:\\/\\/", body.indexOf("\"medium\"")) + 17, body.indexOf("\",\"size\"", body.indexOf("\"medium\""))).replace("\\", "");
                        if (originalUrl.endsWith(".gif"))
                            originalUrl = originalUrl.replace(IMGBBPRE, "https://");
                        return new ImageUrls(originalUrl, largeUrl);
                    }
                    System.err.println(path + "上传到ImgBB失败");
                    return IMAGEURLS301;
                });
    }

    public CompletableFuture<Boolean> scanUrl(String url) {
        HttpRequest upload = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36")
                .header("referer", "https://weibo.com")
                .GET()
                .build();
        return httpClient.sendAsync(upload, HttpResponse.BodyHandlers.discarding())
                .thenApply(HttpResponse::statusCode)
                .thenApply(status -> !status.equals(200)).completeOnTimeout(false, 2, TimeUnit.MINUTES);
    }

    public CompletableFuture<ImageUrls> reUpload(String filename) {
        if ((Thread.currentThread().getId() & 1) == 1)//分发
            return uploadToImgBB(Paths.get(path, filename));
        return uploadToUploadCC(Paths.get(path, filename));
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
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofFile(Paths.get(path, fullFileName))).completeOnTimeout(defaultDownloadHttpResponse, 8, TimeUnit.MINUTES);
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

    public CompletableFuture<String> uploadToSMMS(Path path) {
        MultipartEntityBuilder smfile = MultipartEntityBuilder.create()
                .addBinaryBody("smfile", path.toFile(), ContentType.IMAGE_PNG, path.getFileName().toString())
                .setBoundary("******")
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        HttpEntity httpEntity = smfile
                .build();
        HttpRequest upload = HttpRequest.newBuilder()
                .uri(URI.create("https://sm.ms/api/upload"))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36")
                .header("Content-Type", "multipart/form-data, boundary=******")
                .POST(HttpRequest.BodyPublishers.ofByteArray(entityToByteArray(httpEntity)))
                .build();
        return httpClient.sendAsync(upload, HttpResponse.BodyHandlers.ofString()).completeOnTimeout(defaultUploadHttpResponse, 4, TimeUnit.MINUTES)
                .thenApply(response -> {
                    String body = response.body();
                    if (response.statusCode() == 200 && body.contains("url")) {
                        return body.substring(body.indexOf("\"url\":\"") + 7, body.indexOf("\",\"delete\"")).replace("\\", "");
                    }
                    System.err.println(path + "上传到SMMS失败");
                    return "上传失败" + path;
                });
    }

    private CompletableFuture<String> uploadToPostimage(Path path) throws IOException {
        HttpRequest upload = HttpRequest.newBuilder()
                .uri(URI.create("http://api.postimage.org/1/upload"))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(postimage_apikey + URLEncoder.encode(Base64.getEncoder().encodeToString(Files.readAllBytes(path)), Charset.defaultCharset())))
                .build();
        return httpClient.sendAsync(upload, HttpResponse.BodyHandlers.ofString()).completeOnTimeout(defaultUploadHttpResponse, 4, TimeUnit.MINUTES)
                .thenApply(response -> {
                    String body = response.body();
                    if (response.statusCode() == 200 && body.contains("<page>")) {
                        return body.substring(body.indexOf("<page>") + 6, body.indexOf("</page>")).replace("http", "https");
                    }
                    System.err.println(path + "上传到Postimage失败");
                    return "上传失败" + path;
                }).thenCompose(this::getPostimageOriginalUrl);
    }

    private CompletableFuture<String> getPostimageOriginalUrl(String url) {
        HttpRequest upload = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36")
                .GET()
                .build();
        return httpClient.sendAsync(upload, HttpResponse.BodyHandlers.ofString()).completeOnTimeout(defaultUploadHttpResponse, 4, TimeUnit.MINUTES)
                .thenApply(response -> {
                    String body = response.body();
                    if (response.statusCode() == 200 && body.contains("download")) {
                        return body.substring(body.indexOf("<a href=\"https://i.postimg.cc/") + 9, body.indexOf("\" id=\"download\"") - 5);
                    }
                    System.err.println(path + "获取PostImage原图链接失败");
                    return "上传失败" + path;
                });
    }
}
