
---
# Pixiv Illustration Collection
-------------
一个提供有限的pixiv日排行与高级会员搜索的站点
 
![](https://img.shields.io/github/stars/OysterQAQ/Pixiv-Illustration-Collection.svg) ![](https://img.shields.io/github/forks/OysterQAQ/Pixiv-Illustration-Collection.svg) ![](https://img.shields.io/badge/license-AGPLv3-blue.svg)
 
 
### Link:https://pixivic.com

### Bright Point:
- 前后端完全分离
- 三图床混合优化图片加载体验
- 匿名回复邮件提醒(昵称与邮件由cookie与url携带进行自动填充)
- Nginx反向代理(作为跳板优化延迟与tomcat集群动静分离)
- 原生js
- PC端移动端单独适配(Nginx UA判断跳转)
- 爬虫业务与web业务服务端分别分布
- SSH隧道连接数据库(安全性)
- Httpclient连接池,线程池队列,Druid数据库连接池


### 伺服器:
- 本地arm服务器:作为爬虫服务器,每日五点爬取pixiv三天前的日排行数据
- Aws lightsail:反向代理图片请求过程中添加referer请求头,绕过防盗链
- Uovz香港:反向代理pixiv搜索api,加速访问,延迟提升500ms
- 息壤 北京:作为主web服务器之一,nginx tomcat动静分离
- 阿里云 深圳:作为主web服务器之一,nginx tomcat动静分离

### 架构图:
![Image text](https://ws4.sinaimg.cn/large/006346uDgy1fwkh7hxmtjj31pr15t7dn.jpg)

### 使用方法
更改conf.xml文件与druid配置文件即可

### TODO
等待使用spring reacive web重构

## License
AGPL v3
