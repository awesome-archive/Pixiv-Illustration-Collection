# Pixiv Illustration Collection
-------------
一个提供有限的pixiv日排行与高级会员搜索的站点

![](https://img.shields.io/github/stars/OysterQAQ/Pixiv-Illustration-Collection.svg) ![](https://img.shields.io/github/forks/OysterQAQ/Pixiv-Illustration-Collection.svg) ![](https://img.shields.io/badge/license-AGPLv3-blue.svg)


### Link:https://pixivic.com

### Bright Point:
- 前后端完全分离
- 三图床混合优化图片加载体验
- Spring Web Reactive异步响应式Web技术栈
- JDK11 Httpclient异步HTTP包
- 匿名回复邮件提醒(昵称与邮件由cookie与url携带进行自动填充)
- Nginx反向代理(作为跳板优化延迟与tomcat集群动静分离)
- PC端移动端单独适配(Nginx UA判断跳转)
- 爬虫业务与web业务服务端分别分布

#### v2.0的变化

> 功能的增加

- 增加周排行，月排行(暂且仅对接了PC页面)
- 漫画类型的查看(多图查看)
- gif图片的显示与下载(仅提供600*600尺寸，且小于12m的才会展示)
- 查看更详细的画作信息(暂且仅对接了PC页面)
- 查看画师信息(暂且仅对接了PC页面)
- 热门排序搜索的时间区间选定(页面未对接)
- 搜索联想(页面未对接)
- 可定制性随机图片api

> 技术栈迭代

- jdk: 11(编程风格的变化与一些流式操作带来的并行化好处)
- web: spring web reactive
- db: mongodb
- httpclient: jdk11 httpclient (本地爬虫)、webclient(服务端实时请求)
- image processor: graphics magick(call by gm4java)(主要是gif的合成与过大图片的压缩)

> 体验优化

- 本地爬虫使用https请求替代ssl远程连接服务端数据库
- 将根据p站原生的内容分级调整图床(非动态图片级数大于5上传uploadcc，小于5上传新浪图床，动态图片上传postImage)，将减少新浪图床的和谐造成的不便
- 更快的web体验
- 将部署多台图片反代服务器(目前准备两台)


### 可定制性随机图片api
###### 接口功能
> 提供一些定制性的随机图片API


###### URL
> [https://api.pixivic.com/illust/{startDate}/{endDate}]()（日期格式为yyyy-mm-dd，且为可选）

###### 请求方式
> GET

###### 请求参数
> | 参数       | 类型    | 说明                    |
> | :--------- | :------ | ----------------------- |
> | isOriginal | Boolean | 是否原图                |
> | isR18      | Boolean | 是否R18                 |
> | w_h_ratio  | String  | 宽-长比，输入格式为16-9 |
> | minWidth   | Integer | 最小宽度                |
> | range      | Float   | 长宽比误差范围          |
> | rank       | Integer | 排名范围                |
> | getDetail  | Boolean | 是否返回画作详细信息    |


###### 返回字段
> 301禁止缓存跳转

###### 接口示例
> 地址：[https://api.pixivic.com/illust/2019-01-01/2019-05-01?height=9&width=16&range=0.0001&isR18=false&isOrignal=true]()

### 架构图(已过时):
![Image text](https://ws4.sinaimg.cn/large/006346uDgy1fwkh7hxmtjj31pr15t7dn.jpg)


## License
AGPL v3