package dev.cheerfun.pixivic.biz.web.common.controller;

import dev.cheerfun.pixivic.basic.auth.annotation.PermissionRequired;
import dev.cheerfun.pixivic.basic.auth.constant.PermissionLevel;
import dev.cheerfun.pixivic.basic.auth.util.JWTUtil;
import dev.cheerfun.pixivic.basic.ratelimit.annotation.RateLimit;
import dev.cheerfun.pixivic.basic.sensitive.annotation.SensitiveCheck;
import dev.cheerfun.pixivic.basic.verification.annotation.CheckVerification;
import dev.cheerfun.pixivic.biz.crawler.news.service.NewService;
import dev.cheerfun.pixivic.biz.crawler.pixiv.service.IllustRankService;
import dev.cheerfun.pixivic.biz.web.common.po.User;
import dev.cheerfun.pixivic.biz.web.user.dto.SignUpDTO;
import dev.cheerfun.pixivic.common.po.Result;
import dev.cheerfun.pixivic.common.util.pixiv.OauthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutionException;

/**
 * @author OysterQAQ
 * @version 1.0
 * @date 2019/07/16 9:21
 * @description TODO
 */
@RestController
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TestController {
    private final JWTUtil jwtUtil;
    private final OauthUtil oauthUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final IllustRankService rankDailyService;
    private final NewService newService;

    //@PermissionRequired(PermissionLevel.VIP)
    @PostMapping("/test")
    @RateLimit
    //@PermissionRequired
    public ResponseEntity<String> test(@RequestBody @SensitiveCheck SignUpDTO signUpDTO,/*@RequestHeader("Authorization")  String token,*/@RequestParam @SensitiveCheck String content, @RequestParam @SensitiveCheck String title) throws InterruptedException, ExecutionException, IOException {
        //rankDailyService.pullAllRank();
        // newService.dailyPullTask();
        System.out.println(content + title);
        System.out.println(signUpDTO);
        return ResponseEntity.ok().body(content);
    }

    /*@GetMapping("/32")
    public String login() throws InterruptedException {
        oauthUtil.getOauths().forEach(o -> System.out.println(o.getAccess_token()));
        illustrationPersistentService.dailyPersistentTask();
        User user = new User();
        user.setAvatar("233");
        user.setUsername("233");
        user.setLevel(1);
        AbstractVerificationCode code = VerificationCodeBuildUtil.create(VerificationType.IMG).build();
        AbstractVerificationCode code2 = VerificationCodeBuildUtil.create(VerificationType.EMAIL_CHECK).email("392822872").build();
           stringRedisTemplate.opsForValue().set(code.getVid(), code.getValue());

        stringRedisTemplate.opsForValue().set(code2.getVid(), code2.getValue());
        System.out.println(code2.getValue().equals(stringRedisTemplate.opsForValue().get(code2.getVid())));

        return null;
    }*/

    @PermissionRequired(PermissionLevel.VIP)
    @PostMapping("/auth")
    public String testAOP(@RequestBody() User user, @RequestHeader("Authorization") String token) {
        return "233";
    }

    @PostMapping("/testCode")
    @CheckVerification
    public ResponseEntity<Result> testCode(@RequestParam("vid") String vid, @RequestParam("value") String value, @RequestBody User user) {
        System.out.println(user);
        return ResponseEntity.ok(new Result<>("测试", null));
    }
}
