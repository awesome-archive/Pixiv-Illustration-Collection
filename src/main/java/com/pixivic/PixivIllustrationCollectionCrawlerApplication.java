package com.pixivic;

import com.pixivic.model.Illustration;
import com.pixivic.model.illust.ImageUrls;
import com.pixivic.model.illust.MetaSinglePage;
import com.pixivic.util.EmailUtil;
import com.pixivic.util.IllustrationsUtil;
import com.pixivic.util.ImageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.mail.MessagingException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
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
    private String ownerEmail;

    @Autowired
    public PixivIllustrationCollectionCrawlerApplication(IllustrationsUtil illustrationsUtil, HttpClient httpClient, ImageUtil imageUtil, EmailUtil emailUtil, @Value("${owner.email}") String ownerEmail) {
        this.illustrationsUtil = illustrationsUtil;
        this.imageUtil = imageUtil;
        this.emailUtil = emailUtil;
        this.ownerEmail = ownerEmail;
        this.flag = new AtomicInteger(0);
    }

    public static void main(String[] args) {
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "referer");
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "connection");
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Content-Length");
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");//取消主机名验证
        SpringApplication app = new SpringApplication(PixivIllustrationCollectionCrawlerApplication.class);
        app.run(args);
    }

    @Override
    public void run(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException, MessagingException {
        //用jdk8新的date
        final Long FIRST_MAX_TIME = 1000 * 60 * 6L;
        final Long SECOND_MAX_TIME = 1000 * 60 * 5L;
        final Long THIRD_MAX_TIME = 1000 * 60 * 4L;
        String mode = args[0];
        String date = args[1];
        Path path = Paths.get(imageUtil.getPath(), mode, date);
        imageUtil.setPath(path.toString());
        Files.createDirectories(path);
        Illustration[] illustrations = illustrationsUtil.getIllustrations(mode, date);//参数一参数二
        List<CompletableFuture<Void>> queue = new ArrayList<>();
        //统计总图片数
        taskSum = Arrays.stream(illustrations).parallel().mapToInt(Illustration::getPage_count).sum();
        System.out.println("批处理图片总数为 " + taskSum + " 张");
        Arrays.stream(illustrations).parallel().forEach(illustration -> {
            if (illustration.getPage_count() > 1) {
                IntStream.range(0, illustration.getMeta_pages().size()).parallel().forEach(i -> {
                    ImageUrls image_urls = illustration.getMeta_pages().get(i).getImage_urls();
                    try {
                        queue.add(
                                imageUtil.deal(image_urls.getOriginal(), illustration.getRank() + "-" + i, illustration.getSanity_level(), illustration.getType())
                                        .thenAccept(s -> {
                                            if (s.startsWith("https://") || s.startsWith("上传失败"))
                                                image_urls.setOriginal(s);
                                            System.out.print("----上传下载任务队列已完成 " + (flag.incrementAndGet() * 100 / taskSum) + "%----\r");
                                        }));
                    } catch (InterruptedException | IOException e) {
                        flag.incrementAndGet();
                    }
                });
            } else {
                MetaSinglePage meta_single_page = illustration.getMeta_single_page();
                try {
                    queue.add(
                            imageUtil.deal(meta_single_page.getOriginal_image_url(), illustration.getRank().toString(), illustration.getSanity_level(), illustration.getType())
                                    .thenAccept(s -> {
                                                if (s.startsWith("https://") || s.startsWith("上传失败"))
                                                    meta_single_page.setOriginal_image_url(s);
                                                System.out.print("----上传下载任务队列已完成 " + (flag.incrementAndGet() * 100 / taskSum) + "%----\r");
                                            }
                                    ));
                } catch (InterruptedException | IOException e) {
                    flag.incrementAndGet();
                }
            }
        });
        Date start = new Date();
        System.out.println(start + "----第一次下载与上传任务队列加入完毕,主线程开始自旋等待所有任务完成");
        while (flag.get() < taskSum && (System.currentTimeMillis() - start.getTime()) < FIRST_MAX_TIME) {
            Thread.sleep(1000 * 30);
        }
        System.out.println(new Date() + "----第一次下载与上传任务队列终了");
        flag.set(0);
        taskSum = Arrays.stream(illustrations).parallel().filter(illustration -> illustration.getSanity_level() < 5 && !illustration.getType().equals("ugoira")).mapToInt(Illustration::getPage_count).sum();
        System.out.println(new Date() + "----将在三分钟后启动扫描新浪图床的外链,待扫描图片数为" + taskSum);
        Thread.sleep(1000 * 60 * 3);
        Arrays.stream(illustrations).parallel().filter(illustration -> illustration.getSanity_level() < 5 && !illustration.getType().equals("ugoira"))
                .forEach(illustration -> {
                    if (illustration.getPage_count() > 1) {
                        IntStream.range(0, illustration.getMeta_pages().size()).parallel().forEach(i -> {
                            ImageUrls image_urls = illustration.getMeta_pages().get(i).getImage_urls();
                            imageUtil.scanUrl(image_urls.getOriginal()).thenCompose(isBan -> {
                                if (isBan) {
                                    String banImg = illustration.getRank() + "-" + i + ".jpg";
                                    System.out.println("检测到 " + banImg + "的外链:" + image_urls.getOriginal() + "被和谐,将重新上传到UploadCC/ImgBB");
                                    if (!Files.exists(path.resolve(banImg))) {
                                        return imageUtil.download(image_urls.getOriginal(), illustration.getRank() + "-" + i, illustration.getSanity_level(), illustration.getType()).thenCompose(pathHttpResponse -> imageUtil.reUpload(banImg).completeOnTimeout("uploadCC上传失败", 5, TimeUnit.MINUTES));
                                    }
                                    return imageUtil.reUpload(banImg).completeOnTimeout("上传失败", 5, TimeUnit.MINUTES);
                                }
                                return CompletableFuture.completedFuture(image_urls.getOriginal());
                            }).thenAccept(url -> {
                                image_urls.setOriginal(url);
                                System.out.print("----扫描与再上传任务队列已完成 " + (flag.incrementAndGet() * 100 / taskSum) + "%----\r");
                            });
                        });
                    } else {
                        MetaSinglePage meta_single_page = illustration.getMeta_single_page();
                        imageUtil.scanUrl(meta_single_page.getOriginal_image_url()).thenCompose(isBan -> {
                            if (isBan) {
                                String banImg = illustration.getRank() + ".jpg";
                                System.out.println("检测到 " + banImg + "的外链:" + meta_single_page.getOriginal_image_url() + "被和谐,将重新上传到UploadCC/ImgBB");
                                if (!Files.exists(path.resolve(banImg))) {
                                    return imageUtil.download(meta_single_page.getOriginal_image_url(), String.valueOf(illustration.getRank()), illustration.getSanity_level(), illustration.getType()).thenCompose(pathHttpResponse -> imageUtil.reUpload(banImg).completeOnTimeout("uploadCC上传失败", 5, TimeUnit.MINUTES));
                                }
                                return imageUtil.reUpload(banImg).completeOnTimeout("上传失败", 5, TimeUnit.MINUTES);
                            }
                            return CompletableFuture.completedFuture(meta_single_page.getOriginal_image_url());
                        }).thenAccept(url -> {
                            meta_single_page.setOriginal_image_url(url);
                            System.out.print("----扫描与再上传任务队列已完成 " + (flag.incrementAndGet() * 100 / taskSum) + "%----\r");
                        });
                    }
                });
        start = new Date();
        System.out.println(start + "----第二次扫描与再上传任务队列加入完毕,主线程开始自旋等待所有任务完成");
        while (flag.get() < taskSum && (System.currentTimeMillis() - start.getTime()) < SECOND_MAX_TIME) {
            Thread.sleep(1000 * 30);
        }
        System.out.println(new Date() + "----第二次扫描与重上传任务队列终了");
        flag.set(0);
        taskSum = 0;
        Arrays.stream(illustrations).parallel().forEach(illustration -> {
            if (illustration.getPage_count() > 1) {
                IntStream.range(0, illustration.getMeta_pages().size()).parallel().forEach(i -> {
                    ImageUrls image_urls = illustration.getMeta_pages().get(i).getImage_urls();
                    if (image_urls.getOriginal().startsWith("上传失败")) {
                        taskSum++;
                        imageUtil.uploadToImgBB(Paths.get(image_urls.getOriginal().substring(4)))
                                .thenAccept(url -> {
                                    image_urls.setOriginal(url);
                                    System.out.print("----异常上传的重上传任务队列已完成 " + (flag.incrementAndGet() * 100 / taskSum) + "%----\r");
                                });
                    }
                });
            } else {
                MetaSinglePage meta_single_page = illustration.getMeta_single_page();
                if (meta_single_page.getOriginal_image_url().startsWith("上传失败")) {
                    taskSum++;
                    imageUtil.uploadToImgBB(Paths.get(meta_single_page.getOriginal_image_url().substring(4)))
                            .thenAccept(url -> {
                                meta_single_page.setOriginal_image_url(url);
                                System.out.print("----异常上传的重上传任务队列已完成 " + (flag.incrementAndGet() * 100 / taskSum) + "%----\r");
                            });
                }
            }
        });
        start = new Date();
        System.out.println(start + "----第三次异常上传的重上传任务队列加入完毕,主线程开始自旋等待所有任务完成");
        while (flag.get() < taskSum && (System.currentTimeMillis() - start.getTime()) < THIRD_MAX_TIME) {
            Thread.sleep(1000 * 30);
        }
        System.out.println(new Date() + "----第三次异常上传的重上传任务终了");
        System.out.println(new Date() + "----开始post数据到web服务端");
        illustrationsUtil.postToWebClient(illustrations, mode, date);
        System.out.println(new Date() + "----数据已成功post到服务端,邮件通知发送后程序关闭");
        emailUtil.send(ownerEmail);
        System.exit(0);

    }

}
