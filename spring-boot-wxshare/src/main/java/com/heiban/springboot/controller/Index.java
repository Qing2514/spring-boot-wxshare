package com.heiban.springboot.controller;

import com.heiban.springboot.bean.Wx;
import net.sf.json.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Formatter;
import java.util.UUID;

@Controller
public class Index {

    private static final String AppID = "wx881d1d3b97ab83a3"; //开发者ID
    private static final String AppSecret = "4b77b9cb6e1c84eaa0f524a70903b791"; //开发者密码
    private static String access_token = ""; //调用接口的凭证 2个小时后就会过期需要重新获取 一天最多获取2千次
    private static String jsapi_ticket = ""; //jsapi_ticket的有效时间也是2个小时  获取该参数需要通过access_token
    private static Long expireTime = 0L; //记录access_token 与 jsapi_ticket 的过期时间


    @RequestMapping("/share")
    public String Share (ModelMap model){

        String url = "http://域名/具体的路径?附带的参数";//即调用 JS-SDK的网址
        Wx wxShare = getWxShare(url);//随机串、签名的时间戳、签名字符串
        model.addAttribute("wxShare",wxShare);

        return "share";
    }


    //获取微信分享的重要信息
    public Wx getWxShare(String url){//url 即为访问网站的url
        Wx wx = new Wx(); //用于保存微信分享所需要的参数

        //打开浏览器，创建HTTPClient对象
        CloseableHttpClient httpClient = HttpClients.createDefault();
        Long nowtime = System.currentTimeMillis();//获取当前的时间戳
        //第一步 判断access_token 与 jsapi_ticket是否过期 如果过期了就重新获取
        if (expireTime <= nowtime){//如果过期时间小于 当前的时间就证明过期了
            //通过appid和appsecret，就可以向微信平台来换取access_token
            String token_access_url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="+Index.AppID+"&secret="+Index.AppSecret;
            HttpGet httpGet = new HttpGet(token_access_url);
            CloseableHttpResponse response = null;//按回车，发起请求
            try {
                response = httpClient.execute(httpGet);
                //解析响应 判断状态码是否是200
                if (response.getStatusLine().getStatusCode()==200){
                    HttpEntity httpEntity = response.getEntity();//获取响应消息实体
                    String body = EntityUtils.toString(httpEntity);//获取返回的消息文本
                    JSONObject jsonObject = JSONObject.fromObject(body);//将消息文本解析成json
                    String access_token = (String) jsonObject.get("access_token");
                    Index.access_token = access_token;
                }
                //第二步 通过access_token，向微信平台索取一个jsapi_ticket
                String jsapi_ticket_url = "https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token="+Index.access_token+"&type=jsapi";
                httpGet = new HttpGet(jsapi_ticket_url);
                response = httpClient.execute(httpGet);
                if (response.getStatusLine().getStatusCode()==200){//解析响应 判断状态码是否是200
                    HttpEntity httpEntity = response.getEntity();//获取响应消息实体
                    String body = EntityUtils.toString(httpEntity);//获取返回的消息文本
                    JSONObject jsonObject = JSONObject.fromObject(body);//将消息文本解析成json
                    String errmsg = (String) jsonObject.get("errmsg");
                    if(errmsg.trim().equals("ok")){
                        String jsapi_ticket = (String) jsonObject.get("ticket");
                        Index.jsapi_ticket = jsapi_ticket;
                        Index.expireTime = nowtime+5400000; //这里采用的过期时间是90分钟即一个半小时
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                //关闭资源
                try {response.close();} catch (IOException e) {e.printStackTrace();}
                try {httpClient.close();} catch (IOException e) {e.printStackTrace();}
            }
        }


        //第三步 获得jsapi_ticket之后，就可以生成JS-SDK权限验证的签名了。
        Long timestamp = System.currentTimeMillis()/1000;//用于签名的时间戳 单位为秒
        String nonceStr = UUID.randomUUID().toString(); //获得随机串

        //注意这里参数名必须全部小写，且必须有序
        String string1 = "jsapi_ticket=" + Index.jsapi_ticket + "&noncestr=" + nonceStr + "&timestamp=" + timestamp + "&url=" + url;

        String signature = ""; //签名字符串
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            messageDigest.reset(); //reset()的必要性只表明getInstance()不进行初始化.
            messageDigest.update(string1.getBytes("UTF-8"));

            Formatter formatter = new Formatter();
            for (byte b : messageDigest.digest()) {
                formatter.format("%02x", b);//字节数组转换为十六进制字符串
            }

            signature = formatter.toString();//签名字符串
            formatter.close();
        }catch (Exception e){}


        wx.setNonceStr(nonceStr);//随机串
        wx.setTimestamp(timestamp.toString());//签名的时间戳
        wx.setSignature(signature);//签名字符串

        return wx;
    }

}
