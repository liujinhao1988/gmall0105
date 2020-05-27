package com.atguigu.gmall.passport.controller;


import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.UmsMember;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.UserService;
import com.atguigu.gmall.util.HttpclientUtil;
import com.atguigu.gmall.util.JwtUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

@Controller
public class PassportController {



    @Reference
    UserService userService;

    @RequestMapping("vlogin")
    //@ResponseBody
    public String vlogin(String code,HttpServletRequest request){

        //授权码换取access_token
        //交换accesstoken
        //授权吗,一天过期
        //32893f4c0c9d190699b72eb3805af5ef

        //App Secret：b4ea55a16c03a7a2d3d17ee3a8a4a039 //client_secret=

        String s3="https://api.weibo.com/oauth2/access_token";//?client_id=824451284&client_secret=b4ea55a16c03a7a2d3d17ee3a8a4a039&grant_type=authorization_code&redirect_uri=http://passport.gmall.com:8085/vlogin&code=CODE";

        Map<String,String> paramMap=new HashMap<>();
        paramMap.put("client_id","824451284");
        paramMap.put("client_secret","b4ea55a16c03a7a2d3d17ee3a8a4a039");
        paramMap.put("grant_type","authorization_code");
        paramMap.put("redirect_uri","http://passport.gmall.com:8085/vlogin");
        paramMap.put("code",code);//授权码要最新的

        String access_token_json = HttpclientUtil.doPost(s3,paramMap);

        Map<String,Object> access_map = JSON.parseObject(access_token_json, Map.class);

        //access_token换取用户信息
        String uid = (String) access_map.get("uid");
        String access_token = (String) access_map.get("access_token");
        String show_user_url="https://api.weibo.com/2/users/show.json?access_token="+access_token+"&uid="+uid;

        String user_json = HttpclientUtil.doGet(show_user_url);

        Map<String,Object> user_map = JSON.parseObject(user_json, Map.class);

        //将用户信息保存到数据库，用户类型设置为微博用户

        UmsMember umsMember = new UmsMember();
        umsMember.setSourceType("2");
        umsMember.setAccessCode(code);
        umsMember.setAccessToken(access_token);
        umsMember.setSourceUid((String) user_map.get("idstr"));
        umsMember.setCity((String)user_map.get("location"));
        umsMember.setNickname((String)user_map.get("screen_name"));


        String g="0";
        String gender=(String)user_map.get("gender");
        if(gender.equals("m")){
            g="1";
        }
        umsMember.setGender(g);

        UmsMember umsCheck = new UmsMember();
        umsCheck.setSourceUid(umsMember.getSourceUid());
        UmsMember umsMemberCheck = userService.checkOauthUser(umsCheck);
        if(umsMemberCheck==null){
            umsMember=userService.addOauthUser(umsMember);
        }else{
            umsMember=umsMemberCheck;
        }



        //生成jwt的token，并且重定向到首页，携带该token

        String token =null;
        String memberId =umsMember.getId();//rpc主键返回策略失效，因为前后端在不同电脑时，传不过去
        String nickname = umsMember.getNickname();


        Map<String,Object> userMap=new HashMap<>();
        userMap.put("memberId",memberId);
        userMap.put("nickname",nickname);

        String ip=request.getHeader("x-forwarded-for");//通过nginx转发的客户端ip
        if(StringUtils.isBlank(ip)){//从request中获取ip
            ip=request.getRemoteAddr();
            if(StringUtils.isBlank(ip)){
                ip="127.0.0.1";
            }
        }



        //按照设计的算法对参数进行加密后生成token
        token = JwtUtil.encode("2019gmall0105", userMap, ip);

        //将token存入redis
        userService.addUserToken(token,memberId);


        return "redirect:http://search.gmall.com:8083/index?token="+token;
    }



    @RequestMapping("verify")
    @ResponseBody
    public String verify(String token,String currentIp,HttpServletRequest request){
        //通过jwt校验token的真假

        Map<String,String> map=new HashMap<>();



        Map<String, Object> decode = JwtUtil.decode(token, "2019gmall0105", currentIp);
        if(decode!=null){
            map.put("status","success");
            map.put("memberId",(String)decode.get("memberId"));
            map.put("nickname",(String)decode.get("nickname"));
        }else {
            map.put("status","fail");
        }

        return JSON.toJSONString(map);
    }



    @RequestMapping("login")
    @ResponseBody
    public  String login(UmsMember umsMember, HttpServletRequest request){
        String token="";
        //调用用户服务验证用户名和密码
        UmsMember umsMemberLogin=userService.login(umsMember);

        if(umsMemberLogin!=null){
            //登录成功

            //用jwt制作token
            String memberId=umsMemberLogin.getId();
            String nickname=umsMemberLogin.getNickname();
            Map<String,Object> userMap=new HashMap<>();
            userMap.put("memberId",memberId);
            userMap.put("nickname",nickname);

            String ip=request.getHeader("x-forwarded-for");//通过nginx转发的客户端ip
            if(StringUtils.isBlank(ip)){//从request中获取ip
                ip=request.getRemoteAddr();
                if(StringUtils.isBlank(ip)){
                    ip="127.0.0.1";
                }
            }



            //按照设计的算法对参数进行加密后生成token
            token = JwtUtil.encode("2019gmall0105", userMap, ip);

            //将token存入redis
            userService.addUserToken(token,memberId);


        }else{
            //登录失败
            token="fail";
        }
        return token;
    }

    @RequestMapping("index")
    public  String index(String ReturnUrl, ModelMap map){
        map.put("ReturnUrl",ReturnUrl);
        return "index";
    }
}
