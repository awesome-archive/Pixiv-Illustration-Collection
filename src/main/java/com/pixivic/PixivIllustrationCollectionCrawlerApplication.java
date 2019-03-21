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

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

@SpringBootApplication
public class PixivIllustrationCollectionCrawlerApplication implements CommandLineRunner {
    private final IllustrationsUtil illustrationsUtil;
    private final ImageUtil imageUtil;
    private final EmailUtil emailUtil;

    @Autowired
    public PixivIllustrationCollectionCrawlerApplication(IllustrationsUtil illustrationsUtil, HttpClient httpClient, ImageUtil imageUtil, EmailUtil emailUtil) {
        this.illustrationsUtil = illustrationsUtil;
        this.imageUtil = imageUtil;
        this.emailUtil = emailUtil;
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "referer");
        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");//取消主机名验证
        SpringApplication app = new SpringApplication(PixivIllustrationCollectionCrawlerApplication.class);
        app.run(args);
    }

    @Override
    public void run(String[] args) throws Exception {
        String mode = args[0];
        String date = args[1];
        Illustration[] illustrations = illustrationsUtil.getIllustrations(mode, date);//参数一参数二
        List<CompletableFuture<Void>> queue = new ArrayList<>();
        List<CompletableFuture<Void>> scanQueue = new ArrayList<>();
        Arrays.stream(illustrations).parallel().forEach(illustration -> {
            if (illustration.getPage_count() > 1) {
                IntStream.range(0, illustration.getMeta_pages().size()).parallel().forEach(i -> {
                    ImageUrls image_urls = illustration.getMeta_pages().get(i).getImage_urls();
                    queue.add(
                            imageUtil.deal(image_urls.getOriginal(), illustration.getRank() + "-" + i, illustration.getSanity_level(), illustration.getType())
                                    .thenAccept(image_urls::setOriginal));
                });
            } else {
                MetaSinglePage meta_single_page = illustration.getMeta_single_page();
                queue.add(
                        imageUtil.deal(meta_single_page.getOriginal_image_url(), illustration.getRank().toString(), illustration.getSanity_level(), illustration.getType())
                                .thenAccept(meta_single_page::setOriginal_image_url));
            }
        });
        System.out.println(new Date() + "----第一次上传下载任务队列加入完毕,主线程开始阻塞等待所有任务完成");
        CompletableFuture.allOf(queue.toArray(CompletableFuture<?>[]::new)).join();
        System.out.println(new Date() + "----所有爬虫任务完成");
        System.out.println(new Date() + "----延迟十分钟后开始扫描新浪图床的外链是否被和谐");
        Thread.sleep(1000 * 60 * 10);
        Arrays.stream(illustrations).parallel().filter(illustration -> illustration.getSanity_level() < 5)
                .forEach(illustration -> {
                    if (illustration.getPage_count() > 1) {
                        IntStream.range(0, illustration.getMeta_pages().size()).parallel().forEach(i -> {
                            ImageUrls image_urls = illustration.getMeta_pages().get(i).getImage_urls();
                            scanQueue.add(imageUtil.scanUrl(image_urls.getOriginal()).thenAccept(isBan -> {
                                if (isBan) {
                                    String banImg = illustration.getRank() + "-" + i + ".jpg";
                                    System.out.println("检测到" + banImg + "被和谐,将重新上传到UploadCC");
                                    imageUtil.reUpload(banImg).thenAccept(image_urls::setOriginal).join();
                                }
                            }));
                        });
                    } else {
                        MetaSinglePage meta_single_page = illustration.getMeta_single_page();
                        scanQueue.add(imageUtil.scanUrl(meta_single_page.getOriginal_image_url()).thenAccept(isBan -> {
                            if (isBan)
                                imageUtil.reUpload(illustration.getRank() + ".jpg").thenAccept(meta_single_page::setOriginal_image_url).join();
                        }));
                    }
                });
        System.out.println(new Date() + "----第二次扫描任务队列加入完毕,主线程开始阻塞等待所有任务完成");
        CompletableFuture.allOf(scanQueue.toArray(CompletableFuture<?>[]::new)).join();
        System.out.println(new Date() + "----所有扫描与重上传任务完成");
        System.out.println(new Date() + "----开始post数据到web服务端");
        illustrationsUtil.postToWebClient(illustrations, mode, date);
        System.out.println(new Date() + "----数据已成功post到服务端,等待线程池关闭");
        emailUtil.send("392822872@qq.com");
        Thread.sleep(1000 * 60 * 10);
        System.exit(0);

    }

}
