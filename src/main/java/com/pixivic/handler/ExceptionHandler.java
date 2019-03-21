package com.pixivic.handler;

import com.pixivic.exception.DownloadException;
import com.pixivic.exception.GraphicsMagickException;
import com.pixivic.exception.OutOfMaxSizeException;
import com.pixivic.exception.UploadException;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class ExceptionHandler {
    @AfterThrowing(pointcut = "execution(* com.pixivic.*.*(..))", throwing = "ex")
    public void handleException(Exception ex) {
        System.out.println(ex.toString()+"sdsdsdsd");
        //发邮件
    }

    @AfterThrowing(pointcut = "execution(* com.pixivic.*.*(..))", throwing = "oose")
    public void handleOutOfMaxSizeException(OutOfMaxSizeException oose) {
        //发邮件

    }

    @AfterThrowing(pointcut = "execution(* com.pixivic.*.*(..))", throwing = "gme")
    public void handleOutOfMaxSizeException(GraphicsMagickException gme) {
        //发邮件

    }

    @AfterThrowing(pointcut = "execution(* com.pixivic.*.*(..))", throwing = "de")
    public void handleException(DownloadException de) {
        //发邮件
        System.out.println("asasas");
    }

    @AfterThrowing(pointcut = "execution(* com.pixivic.*.*(..))", throwing = "ue")
    public void handleException(UploadException ue) {
        //发邮件
    }
}
