package com.atguigu.gmall.passport.controller;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.util.HttpclientUtil;

import java.util.HashMap;
import java.util.Map;

public class TestOauth2 {

    public static String getCode(){
        //App Key：824451284
        //授权回调页：http://passport.gmall.com:8085/vlogin

        String s1 = HttpclientUtil.doGet("https://api.weibo.com/oauth2/authorize?client_id=824451284&response_type=code&redirect_uri=http://passport.gmall.com:8085/vlogin");

        System.out.println(s1);

        //授权吗
        //1b11598548e9815495c18279fa51a1f3

        String s2="http://passport.gmall.com:8085/vlogin?code=e2469e9574b987dc6125a4e7c2051be4";
        return null;
    }

    public static String getAccess_token(){

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
        paramMap.put("code","1df9bfba467a736cdfc4a7ec019dde77");//授权码要最新的

        String access_token_json = HttpclientUtil.doPost(s3,paramMap);

        Map<String,String> access_map = JSON.parseObject(access_token_json, Map.class);

        System.out.println(access_map.get("access_token"));
        System.out.println(access_map.get("uid"));
        return null;
    }

    public static Map<String,String> getUser_info(){
        //access_token
        //2.00yJDPoB038_nt9e0eb8f4ce0tKCFn

        //4用access_token查询用户信息
        String s4="https://api.weibo.com/2/users/show.json?access_token=2.00yJDPoB038_nt9e0eb8f4ce0tKCFn&uid=1658536702";

        String user_json = HttpclientUtil.doGet(s4);
        Map<String,String> user_map = JSON.parseObject(user_json, Map.class);
        System.out.println(user_map.get("1"));

        return user_map;
    }




    public static void main(String[] args){

        getUser_info();





    }
}
