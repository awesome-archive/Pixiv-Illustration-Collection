package utils;

import domain.Comment;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.net.URLEncoder;
import java.util.Date;
import java.util.Properties;

public class EmailUtil {

    private final static String EMAILACCOUNT;
    private final static String AUTHORIZATIONCODE;
    private final static String SMTPHOST;
    private final static String SMTPPORT;

    static {
        EMAILACCOUNT = ConfigUtil.getConfig("email-config > emailAccount");
        AUTHORIZATIONCODE = ConfigUtil.getConfig("email-config > authorizationCode");
        SMTPHOST = ConfigUtil.getConfig("email-config > SMTPHost");
        SMTPPORT = ConfigUtil.getConfig("email-config > SMTPPort");
    }

    public static void senEmailto(Comment from, Comment to) throws Exception {
        String receiveAddr = to.getEmail();
        Properties props = new Properties();
        props.setProperty("mail.transport.protocol", "smtp");
        props.setProperty("mail.smtp.host", SMTPHOST);
        props.setProperty("mail.smtp.auth", "true");
        props.setProperty("mail.smtp.port", SMTPPORT);
        props.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.setProperty("mail.smtp.socketFactory.fallback", "false");
        props.setProperty("mail.smtp.socketFactory.port", SMTPPORT);
        Session session = Session.getInstance(props);
        // session.setDebug(true);
        Transport transport = null;
        try {
            MimeMessage message = createMimeMessage(session, receiveAddr, to, from);
            transport = session.getTransport();
            transport.connect(EMAILACCOUNT, AUTHORIZATIONCODE);
            transport.sendMessage(message, message.getAllRecipients());
        } finally {
            transport.close();
        }
    }

    private static MimeMessage createMimeMessage(Session session, String receiveMail, Comment to, Comment from) throws Exception {
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(EmailUtil.EMAILACCOUNT, "生蚝QAQ", "UTF-8"));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(receiveMail, "亲爱的达瓦里希", "UTF-8"));
        message.setSubject("您在Pixivic的评论获得了回复", "UTF-8");
        message.setContent("<!DOCTYPE html>\n" +
                "<html lang=\"ch\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>Title</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "<table align=\"center\" style=\"background:#ffffff; font-family:microsoft yahei; margin:5px auto 10px; width:400px\">\n" +
                "    <tbody>\n" +
                "    <tr class=\"firstRow\">\n" +
                "        <td>\n" +
                "            <table align=\"center\"\n" +
                "                   style=\"background:#90eec7; color:#fff; font-family:microsoft yahei; font-size:20px; font-weight:bold; width:400px\">\n" +
                "                <tbody>\n" +
                "                <tr class=\"firstRow\">\n" +
                "                    <td style=\"height: 70px; text-align: center; word-break: break-all;\"><span\n" +
                "                            style=\"font-family:Microsoft YaHei\"><span style=\"font-size:20px\" class=\"ng-binding\">于Pixivic的留言有了回复!</span></span>\n" +
                "                    </td>\n" +
                "                </tr>\n" +
                "                </tbody>\n" +
                "            </table>\n" +
                "            <table align=\"center\" style=\"background:#ffffff; font-family:microsoft yahei; margin:0px auto; width:350px\">\n" +
                "                <tbody>\n" +
                "                <tr class=\"firstRow\">\n" +
                "                    <td style=\"word-break: break-all;\">\n" +
                "                        <div class=\"letter-txttop\"><h3>尊敬的<span\n" +
                "                                style=\"color:#29bdb9; font-family:microsoft yahei\">" + convert(to.getUser()) + "</span>： </h3>\n" +
                "                            <p style=\"line-height: 26px;\" class=\"ng-binding\">您好！ &nbsp;<br>您的评论获得了回复，详细信息如下：\n" +
                "                            </p></div>\n" +
                "                        <div class=\"letter-title\"\n" +
                "                             style=\"HEIGHT: 50px; FONT-FAMILY: Microsoft YaHei; TEXT-ALIGN: center; MARGIN-TOP: 30px; border-top: 1px solid #efefef;\">\n" +
                "                            <div style=\"HEIGHT: 40px; FONT-FAMILY: Microsoft YaHei; WIDTH: 123px; BACKGROUND: #eef1f6; FONT-WEIGHT: 600; TEXT-ALIGN: center; MARGIN: 0px auto 20px; LINE-height: 40px; border-radius: 5px ; position:relative; top:-20px;\">\n" +
                "                                评论详情\n" +
                "                            </div>\n" +
                "                        </div>\n" +
                "                        <div class=\"letter-text\"\n" +
                "                             style=\"FONT-FAMILY: Microsoft YaHei; PADDING-BOTTOM: 0px; PADDING-TOP: 0px; PADDING-LEFT: 8px; PADDING-RIGHT: 8px\">\n" +
                "                            <p class=\"ng-binding\">内容：<span style=\"font-size:16px\">\n" +
                "                                <p\n" +
                "                                        style=\"TEXT-DECORATION: none;font-size:16px;\" target=\"_blank\"\n" +
                "                                        rel=\"noopener\"><span\n" +
                "                                        style=\"color:#ff8c00\">" + convert(from.getContent()) + "</span>\n" +
                "                                </p>\n" +
                "                                <p>时间：<span style=\"font-size:16px\">\n" +
                "                                <p\n" +
                "                                        style=\"TEXT-DECORATION: none;font-size:16px;\" target=\"_blank\"\n" +
                "                                        rel=\"noopener\"><span\n" +
                "                                        style=\"color:#ff8c00\">" + convert(from.getTime()) + "</span></p>\n" +
                "                                <a href=\"" + "https://pixivic.com/comments?id=" + to.getId() + "&user=" + URLEncoder.encode(convert(to.getUser()), "UTF-8") + "&email=" + URLEncoder.encode(convert(to.getEmail()), "UTF-8") + "\"\n" +
                "                                   style=\"text-decoration:none; \" target=\"_blank\"\n" +
                "                                   rel=\"noopener\"><span\n" +
                "                                        style=\"color:#90eec7; font-family:microsoft yahei\"><strong\n" +
                "                                        style=\"font-size: 20px;\" class=\"ng-binding\">点击查看</strong></span></a></div>\n" +
                "                        &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;\n" +
                "                        &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;\n" +
                "                        &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp;\n" +
                "                    </td>\n" +
                "                </tr>\n" +
                "                </tbody>\n" +
                "            </table>\n" +
                "            <table align=\"center\" style=\"font-family:microsoft yahei; width:400px\">\n" +
                "                <tbody>\n" +
                "                <tr class=\"firstRow\">\n" +
                "                    <td>\n" +
                "                        <div class=\"letter-explain\"\n" +
                "                             style=\"FONT-FAMILY: Microsoft YaHei; BACKGROUND: #f8f9fb; PADDING-BOTTOM: 15px; PADDING-TOP: 15px; PADDING-LEFT: 20px; padding-right: 20px\">\n" +
                "                            <p>以上来自：</p>\n" +
                "                            <a href=\"https://pixivic.com\"\n" +
                "                               style=\"text-decoration:none;\" target=\"_blank\"\n" +
                "                               rel=\"noopener\"><span\n" +
                "                                    style=\"color:#90eec7; font-family:microsoft yahei\"><strong style=\"font-size: 16px;\"\n" +
                "                                                                                               class=\"ng-binding\"> Pixivic.com </strong></span></a>\n" +
                "                            <p class=\"ng-binding\">一个 Pixiv 日排行与免费高级会员搜索的网站\n" +
                "                            </p></div>\n" +
                "                    </td>\n" +
                "                </tr>\n" +
                "                </tbody>\n" +
                "            </table>\n" +
                "        </td>\n" +
                "    </tr>\n" +
                "    </tbody>\n" +
                "</table>\n" +
                "</body>\n" +
                "</html>", "text/html;charset=UTF-8");
        message.setSentDate(new Date());
        message.saveChanges();
        return message;
    }

    public static String convert(String input) {
        String output = "";
        if (input == null) return "";
        output = input.replace("&", "&amp;");
        output = output.replace("<", "&lt;");
        output = output.replace(">", "&gt;");
        output = output.replace(" ", "&nbsp;");
        output = output.replace("'", "&#39;");
        output = output.replace("\"", "&quot;");
        return output;

    }
}
