package com.pixivic;

import com.pixivic.model.Illustration;
import com.pixivic.model.illust.ImageUrls;
import com.pixivic.model.illust.MetaSinglePage;
import com.pixivic.util.EmailUtil;
import com.pixivic.util.IllustrationsUtil;
import com.pixivic.util.ImageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@SpringBootApplication
public class PixivIllustrationCollectionCrawlerApplication implements CommandLineRunner {
    private final IllustrationsUtil illustrationsUtil;
    private final ImageUtil imageUtil;
    private final EmailUtil emailUtil;
    private volatile AtomicInteger flag;
    private Integer taskSum;

    @Autowired
    public PixivIllustrationCollectionCrawlerApplication(IllustrationsUtil illustrationsUtil, HttpClient httpClient, ImageUtil imageUtil, EmailUtil emailUtil) {
        this.illustrationsUtil = illustrationsUtil;
        this.imageUtil = imageUtil;
        this.emailUtil = emailUtil;
        this.flag = new AtomicInteger(0);
    }

    public static void main(String[] args) {
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "referer");
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "connection");
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");//取消主机名验证
        SpringApplication app = new SpringApplication(PixivIllustrationCollectionCrawlerApplication.class);
        app.run(args);
    }

    @Override
    public void run(String[] args) throws Exception {
        String mode = args[0];
        String date = args[1];
        Path path = Paths.get(imageUtil.getPath(), mode, date);
        imageUtil.setPath(path.toString());
        Files.createDirectories(path);
        Illustration[] illustrations = illustrationsUtil.getIllustrations(mode, date);//参数一参数二
        List<CompletableFuture<Void>> queue = new ArrayList<>();
        List<CompletableFuture<Void>> scanQueue = new ArrayList<>();
        List<CompletableFuture<Void>> reUploadQueue = new ArrayList<>();
        //统计总图片数
        taskSum = Arrays.stream(illustrations).parallel().mapToInt(Illustration::getPage_count).sum();
        System.out.println(taskSum);
        Arrays.stream(illustrations).parallel().forEach(illustration -> {
            if (illustration.getPage_count() > 1) {
                IntStream.range(0, illustration.getMeta_pages().size()).parallel().forEach(i -> {
                    ImageUrls image_urls = illustration.getMeta_pages().get(i).getImage_urls();
                    try {
                        queue.add(
                                imageUtil.deal(image_urls.getOriginal(), illustration.getRank() + "-" + i, illustration.getSanity_level(), illustration.getType())
                                        .thenAccept(s -> {
                                            if (s.startsWith("https://"))
                                                image_urls.setOriginal(s);
                                            System.out.print("----任务已完成" + (flag.incrementAndGet() * 100 / taskSum) + "%----\r");
                                        }));
                    } catch (InterruptedException | IOException e) {
                        e.printStackTrace();
                    }
                });
            } else {
                MetaSinglePage meta_single_page = illustration.getMeta_single_page();
                try {
                    queue.add(
                            imageUtil.deal(meta_single_page.getOriginal_image_url(), illustration.getRank().toString(), illustration.getSanity_level(), illustration.getType())
                                    .thenAccept(s -> {
                                                if (s.startsWith("https://"))
                                                    meta_single_page.setOriginal_image_url(s);
                                                System.out.print("----任务已完成" + (flag.incrementAndGet() * 100 / taskSum) + "%----\r");
                                            }
                                    ));
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println(new Date() + "----第一次上传下载任务队列加入完毕,主线程开始阻塞等待所有任务完成");
        while (flag.get() < taskSum) {
            Thread.sleep(2000);
        }
        CompletableFuture.allOf(queue.subList(0, 4).toArray(CompletableFuture<?>[]::new)).join();
        System.out.println(new Date() + "----所有爬虫任务完成");
        System.out.println(new Date() + "----延迟十分钟后开始扫描新浪图床的外链是否被和谐");
        Thread.sleep(1000 * 60 * 5);
        Arrays.stream(illustrations).parallel().filter(illustration -> illustration.getSanity_level() < 5)
                .forEach(illustration -> {
                    if (illustration.getPage_count() > 1) {
                        IntStream.range(0, illustration.getMeta_pages().size()).parallel().forEach(i -> {
                            ImageUrls image_urls = illustration.getMeta_pages().get(i).getImage_urls();
                            scanQueue.add(imageUtil.scanUrl(image_urls.getOriginal()).thenCompose(isBan -> {
                                if (isBan) {
                                    String banImg = illustration.getRank() + "-" + i + ".jpg";
                                    System.out.println("检测到 " + banImg + "的外链:" + image_urls.getOriginal() + "被和谐,将重新上传到UploadCC");
                                    if (!Files.exists(path.resolve(banImg))) {
                                        return imageUtil.download(image_urls.getOriginal(),illustration.getRank() + "-" + i  , illustration.getSanity_level()).thenCompose(pathHttpResponse -> imageUtil.reUpload(banImg).completeOnTimeout("uploadCC上传失败", 10, TimeUnit.MINUTES));
                                    }
                                    return imageUtil.reUpload(banImg).completeOnTimeout("uploadCC上传失败", 10, TimeUnit.MINUTES);
                                }
                                return CompletableFuture.completedFuture(image_urls.getOriginal());
                            }).thenAccept(image_urls::setOriginal));
                        });
                    } else {
                        MetaSinglePage meta_single_page = illustration.getMeta_single_page();
                        scanQueue.add(imageUtil.scanUrl(meta_single_page.getOriginal_image_url()).thenCompose(isBan -> {
                            if (isBan){
                                String banImg = illustration.getRank() + ".jpg";
                                System.out.println("检测到 " + banImg + "的外链:" +meta_single_page.getOriginal_image_url()+ "被和谐,将重新上传到UploadCC");
                                if (!Files.exists(path.resolve(banImg))) {
                                    return imageUtil.download(meta_single_page.getOriginal_image_url(), String.valueOf(illustration.getRank()), illustration.getSanity_level()).thenCompose(pathHttpResponse -> imageUtil.reUpload(banImg).completeOnTimeout("uploadCC上传失败", 10, TimeUnit.MINUTES));
                                }
                                return imageUtil.reUpload(banImg);
                            }
                            return CompletableFuture.completedFuture(meta_single_page.getOriginal_image_url());
                        }).thenAccept(meta_single_page::setOriginal_image_url));
                    }
                });
        System.out.println(new Date() + "----第二次扫描任务队列加入完毕,主线程开始阻塞等待所有任务完成");
        //阻塞改为自旋
        CompletableFuture.allOf(scanQueue.toArray(CompletableFuture<?>[]::new)).join();
        System.out.println(new Date() + "----第二次扫描与重上传任务完成");
        Arrays.stream(illustrations).parallel().forEach(illustration -> {
            if (illustration.getPage_count() > 1) {
                IntStream.range(0, illustration.getMeta_pages().size()).parallel().forEach(i -> {
                    ImageUrls image_urls = illustration.getMeta_pages().get(i).getImage_urls();
                    if (image_urls.getOriginal().startsWith("uploadCC上传失败"))
                        reUploadQueue.add(
                                imageUtil.uploadToImgBB(Paths.get(image_urls.getOriginal().substring(12)))
                                        .thenAccept(image_urls::setOriginal));
                });
            } else {
                MetaSinglePage meta_single_page = illustration.getMeta_single_page();
                if (meta_single_page.getOriginal_image_url().startsWith("uploadCC上传失败"))
                    reUploadQueue.add(
                            imageUtil.uploadToImgBB(Paths.get(meta_single_page.getOriginal_image_url().substring(12)))
                                    .thenAccept(meta_single_page::setOriginal_image_url));
            }
        });
        CompletableFuture.allOf(reUploadQueue.toArray(CompletableFuture<?>[]::new)).join();
        System.out.println(new Date() + "----第三次重上传务队列加入完毕,主线程开始阻塞等待所有任务完成");
        System.out.println(new Date() + "----第三次扫描与重上传任务完成");
        System.out.println(new Date() + "----开始post数据到web服务端");
        illustrationsUtil.postToWebClient(illustrations, mode, date);
        System.out.println(new Date() + "----数据已成功post到服务端,等待线程池关闭");
        //       emailUtil.send("392822872@qq.com");
        Thread.sleep(1000 * 60);
        System.exit(0);

    }

}
