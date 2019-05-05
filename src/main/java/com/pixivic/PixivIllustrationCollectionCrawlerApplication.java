package com.pixivic;

import com.pixivic.model.Illustration;
import com.pixivic.model.illust.ImageUrls;
import com.pixivic.model.illust.MetaPage;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Referer");
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Content-Length");
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");//取消主机名验证
        SpringApplication app = new SpringApplication(com.pixivic.PixivIllustrationCollectionCrawlerApplication.class);
        app.run(args);
    }

    @Override
    public void run(String[] args) throws IOException, InterruptedException, NoSuchAlgorithmException, MessagingException {
/*        try {
            System.out.println(imageUtil.uploadToPostimage(Paths.get("E:\\39-0.jpg")).get());
        } catch (ExecutionException e) {
            e.printStackTrace();
        }*/
        final int FIRST_MAX_TIME = 15;//首次任务队列超时时间限制
        final int SECOND_MAX_TIME = 10;//二次任务队列超时时间限制
        final int THIRD_MAX_TIME = 12;//三次任务队列超时时间限制.
        String mode = args[0];
        String date = args[1];
        switch (mode) {//周排行默认本周一的排行，月排行默认每月一号(确保都有)
            case "week":
                date = LocalDate.parse(date).with(DayOfWeek.MONDAY).toString();
                break;
            case "month":
                date = LocalDate.parse(date).with(TemporalAdjusters.firstDayOfMonth()).toString();
                break;
        }
        //新建路径
        Path path = Paths.get(imageUtil.getPath(), mode, date);
        imageUtil.setPath(path.toString());
        Files.createDirectories(path);
        //获取插画信息对象
        System.out.println(new Date() + "----开始获取所有插画对象");
        ArrayList<Illustration> illustrations = illustrationsUtil.getIllustrations(mode, date);
        System.out.println(new Date() + "----获取所有插画对象完成");
        System.gc();
        //统计总图片数
        taskSum = illustrations.stream().parallel().mapToInt(Illustration::getPage_count).sum();
        final CountDownLatch cd = new CountDownLatch(taskSum);
        System.out.println(new Date() + "批处理图片总数为 " + taskSum + " 张");
        //开始往fork join线程池添加任务
        stage1(illustrations, cd);
        System.out.println(new Date() + "----第一次下载与上传任务队列加入完毕,主线程开始阻塞等待所有任务完成");
        cd.await(FIRST_MAX_TIME, TimeUnit.MINUTES);//超时
        System.out.println(new Date() + "----第一次下载与上传任务队列终了");
        System.gc();
        //扫描任务总数
        taskSum = illustrations.stream().parallel().filter(illustration -> illustration.getSanity_level() < 5 && !illustration.getType().equals("ugoira")).mapToInt(Illustration::getPage_count).sum();
        final CountDownLatch cd2 = new CountDownLatch(taskSum);
        System.out.println(new Date() + "----将在十五分钟后启动扫描新浪图床的外链,待扫描图片数为" + taskSum);
        Thread.sleep(1000 * 60 * 15);
        stage2(path, illustrations, cd2);
        System.out.println(new Date() + "----第二次扫描与再上传任务队列加入完毕,主线程开始自旋等待所有任务完成");
        cd2.await(SECOND_MAX_TIME, TimeUnit.MINUTES);//超时
        System.out.println(new Date() + "----第二次扫描与重上传任务队列终了");
        System.gc();
        flag.set(0);
        AtomicInteger atomicTaskSum = new AtomicInteger(0);
        stage3(illustrations, flag, atomicTaskSum);
        Date start = new Date();
        System.out.println(start + "----第三次异常上传的重上传任务队列加入完毕,主线程开始自旋等待所有任务完成");
        while (flag.get() < atomicTaskSum.get() && (System.currentTimeMillis() - start.getTime()) < THIRD_MAX_TIME) {
            Thread.sleep(1000 * 30);
        }
        System.out.println(new Date() + "----第三次异常上传的重上传任务终了");
        System.gc();
        System.out.println(new Date() + "----开始post数据到web服务端");
        illustrationsUtil.postToWebClient(illustrations, mode, date);
        System.out.println(new Date() + "----数据已成功post到服务端,邮件通知发送后程序关闭");
        emailUtil.send(ownerEmail);
        System.exit(0);
    }

    private void stage1(ArrayList<Illustration> illustrations, CountDownLatch cd) {
        illustrations.stream().parallel().forEach(illustration -> {
            if (illustration.getPage_count() > 1) {
                IntStream.range(0, illustration.getMeta_pages().size()).parallel().forEach(i -> {
                    MetaPage metaPage = illustration.getMeta_pages().get(i);
                    imageUtil.deal(metaPage.getImage_urls().getOriginal(), illustration.getRank() + "-" + i, illustration.getSanity_level(), illustration.getType())
                            .thenAccept(s -> {
                                metaPage.setImage_urls(s);
                                System.out.print("----上传下载任务队列已完成 " + ((taskSum - cd.getCount()) * 100 / taskSum) + "%----\r");
                                cd.countDown();
                            });
                });
            } else {
                MetaSinglePage meta_single_page = illustration.getMeta_single_page();
                imageUtil.deal(meta_single_page.getOriginal_image_url(), illustration.getRank().toString(), illustration.getSanity_level(), illustration.getType())
                        .thenAccept(url -> {
                                    meta_single_page.setUrl(url.getOriginal(), url.getLarge());
                                    System.out.print("----上传下载任务队列已完成 " + ((taskSum - cd.getCount()) * 100 / taskSum) + "%----\r");
                                    cd.countDown();
                                }
                        );
            }
        });
    }

    private void stage2(Path path, ArrayList<Illustration> illustrations, CountDownLatch cd2) {
        illustrations.stream().parallel().filter(illustration -> illustration.getSanity_level() < 4 && !illustration.getType().equals("ugoira"))
                .forEach(illustration -> {
                    if (illustration.getPage_count() > 1) {
                        IntStream.range(0, illustration.getMeta_pages().size()).parallel().forEach(i -> {
                            MetaPage metaPage = illustration.getMeta_pages().get(i);
                            ImageUrls image_urls = metaPage.getImage_urls();
                            imageUtil.scanUrl(image_urls.getOriginal()).thenCompose(isBan -> {
                                if (isBan) {
                                    String banImg = illustration.getRank() + "-" + i + ".jpg";
                                    System.out.println("检测到 " + banImg + "的外链:" + image_urls.getOriginal() + "被和谐,将重新上传到UploadCC/ImgBB");
                                    return Files.exists(path.resolve(banImg))//上一次处理未成功的资源
                                            ? imageUtil.balanceUpload(banImg).completeOnTimeout(new ImageUrls("上传失败" + banImg, ""), 5, TimeUnit.MINUTES) :
                                            imageUtil.deal(image_urls.getOriginal(), illustration.getRank() + "-" + i, illustration.getSanity_level(), illustration.getType());
                                }
                                return CompletableFuture.completedFuture(image_urls);
                            }).thenAccept(url -> {
                                metaPage.setImage_urls(url);
                                System.out.print("----扫描与再上传任务队列已完成 " + ((taskSum - cd2.getCount()) * 100 / taskSum) + "%----\r");
                                cd2.countDown();
                            });
                        });
                    } else {
                        MetaSinglePage meta_single_page = illustration.getMeta_single_page();
                        imageUtil.scanUrl(meta_single_page.getOriginal_image_url()).thenCompose(isBan -> {
                            if (isBan) {
                                String banImg = illustration.getRank() + ".jpg";
                                System.out.println("检测到 " + banImg + "的外链:" + meta_single_page.getOriginal_image_url() + "被和谐,将重新上传到UploadCC/ImgBB");
                                return Files.exists(path.resolve(banImg))
                                        ? imageUtil.balanceUpload(banImg).completeOnTimeout(new ImageUrls("上传失败" + banImg, ""), 5, TimeUnit.MINUTES)
                                        : imageUtil.deal(meta_single_page.getOriginal_image_url(), illustration.getRank().toString(), illustration.getSanity_level(), illustration.getType());
                            }
                            return CompletableFuture.completedFuture(new ImageUrls(meta_single_page.getOriginal_image_url(), meta_single_page.getLarge_image_url()));
                        }).thenAccept(url -> {
                            meta_single_page.setUrl(url.getOriginal(), url.getLarge());
                            System.out.print("----扫描与再上传任务队列已完成 " + ((taskSum - cd2.getCount()) * 100 / taskSum) + "%----\r");
                            cd2.countDown();
                        });
                    }
                });
    }

    private void stage3(ArrayList<Illustration> illustrations, AtomicInteger flag, AtomicInteger taskSum) {
        illustrations.stream().parallel().forEach(illustration -> {
            if (illustration.getPage_count() > 1) {
                IntStream.range(0, illustration.getMeta_pages().size()).parallel().forEach(i -> {
                    MetaPage metaPage = illustration.getMeta_pages().get(i);
                    ImageUrls image_urls = metaPage.getImage_urls();
                    boolean isNotDownload = image_urls.getOriginal().startsWith("https://i.pximg.net");
                    if (image_urls.getOriginal().startsWith("上传失败") || isNotDownload) {
                        taskSum.incrementAndGet();
                        CompletableFuture<ImageUrls> responseUrl = isNotDownload
                                ? imageUtil.download(image_urls.getOriginal(), illustration.getRank() + "-" + i, illustration.getSanity_level(), illustration.getType())
                                .thenCompose(pathHttpResponse -> imageUtil.uploadToImgBB(pathHttpResponse.body()))
                                : imageUtil.uploadToImgBB(Paths.get(image_urls.getOriginal().substring(4)));
                        responseUrl.thenAccept(url -> {
                            metaPage.setImage_urls(url);
                            System.out.print("----异常上传的重上传任务队列已完成 " + (flag.incrementAndGet() * 100 / taskSum.get()) + "%----\r");
                        });
                    }
                });
            } else {
                MetaSinglePage meta_single_page = illustration.getMeta_single_page();
                boolean isNotDownload = meta_single_page.getOriginal_image_url().startsWith("https://i.pximg.net");
                if (meta_single_page.getOriginal_image_url().startsWith("上传失败") || isNotDownload) {
                    taskSum.incrementAndGet();
                    CompletableFuture<ImageUrls> responseUrl = isNotDownload
                            ? imageUtil.download(meta_single_page.getOriginal_image_url(), illustration.getRank().toString(), illustration.getSanity_level(), illustration.getType())
                            .thenCompose(pathHttpResponse -> imageUtil.uploadToImgBB(pathHttpResponse.body()))
                            : imageUtil.uploadToImgBB(Paths.get(meta_single_page.getOriginal_image_url().substring(4)));
                    responseUrl.thenAccept(url -> {
                        meta_single_page.setUrl(url.getOriginal(), url.getLarge());
                        System.out.print("----异常上传的重上传任务队列已完成 " + (flag.incrementAndGet() * 100 / taskSum.get()) + "%----\r");
                    });
                }
            }
        });
    }
}
