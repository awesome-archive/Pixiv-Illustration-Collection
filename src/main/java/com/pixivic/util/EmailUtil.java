package com.pixivic.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

@Component
public class EmailUtil {
    private final JavaMailSender mailSender;

    @Autowired
    public EmailUtil(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(String emailAddr) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom("Pixivic.com<403827354@qq.com>");
        helper.setTo(emailAddr);
        helper.setSubject("来自Pixivic.com的成功提醒");
        helper.setText("今日爬虫任务完成");
        mailSender.send(message);
    }
}
