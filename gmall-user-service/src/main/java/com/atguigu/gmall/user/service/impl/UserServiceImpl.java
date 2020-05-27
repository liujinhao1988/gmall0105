package com.atguigu.gmall.user.service.impl;



import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.UmsMember;
import com.atguigu.gmall.bean.UmsMemberReceiveAddress;
import com.atguigu.gmall.service.UserService;
import com.atguigu.gmall.user.mapper.UmsMemberReceiveAddressMapper;
import com.atguigu.gmall.user.mapper.UserMapper;
import com.atguigu.gmall.util.RedisUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;


//import org.springframework.stereotype.Service; //重新引入service

import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    UserMapper userMapper;
    @Autowired
    UmsMemberReceiveAddressMapper umsMemberReceiveAddressMapper;

    @Autowired
    RedisUtil redisUtil;
    @Override
    public List<UmsMember> getAllUser() {
        //userMapper.selectAllUser();
        List<UmsMember> umsMemberList = userMapper.selectAll();

        return umsMemberList;
    }

    @Override
    public List<UmsMemberReceiveAddress> getReceiveAddressByMemberId(String memberId) {
        UmsMemberReceiveAddress umsMemberReceiveAddress=new UmsMemberReceiveAddress();
        umsMemberReceiveAddress.setMemberId(memberId);

        List<UmsMemberReceiveAddress> umsMemberReceiveAddresses = umsMemberReceiveAddressMapper.select(umsMemberReceiveAddress);
        return umsMemberReceiveAddresses;
    }

    @Override
    public UmsMember login(UmsMember umsMember) {
        Jedis jedis =null;
        try{
            jedis = redisUtil.getJedis();
            if(jedis!=null){
                String umsMemberStr =jedis.get("user:"+umsMember.getPassword()+umsMember.getUsername()+":info");

                if(StringUtils.isNotBlank(umsMemberStr)){
                    //密码正确
                    UmsMember umsMemberFromCache= JSON.parseObject(umsMemberStr,UmsMember.class);
                    return umsMemberFromCache;
                }
                //密码错误
                //缓存中没有
                //开启数据库
            }
            //连接redis失败
            //开启数据库
            UmsMember umsMemberFromDb=loginFromDb(umsMember);
            if(umsMemberFromDb!=null){
                jedis.setex("user:"+umsMember.getPassword()+umsMember.getUsername()+":info",60*60*24,JSON.toJSONString(umsMemberFromDb));
            }
            return umsMemberFromDb;

        }finally {
            jedis.close();
        }

    }

    @Override
    public void addUserToken(String token, String memberId) {

        Jedis jedis = redisUtil.getJedis();
        jedis.setex("user:"+memberId+":token",60*60*2,token);
        jedis.close();
    }

    @Override
    public UmsMember addOauthUser(UmsMember umsMember) {
        userMapper.insertSelective(umsMember);
        return umsMember;//传回去
    }

    @Override
    public UmsMember checkOauthUser(UmsMember umsCheck) {
        UmsMember umsMember = userMapper.selectOne(umsCheck);
        return umsMember;
    }

    @Override
    public UmsMember getOauthUser(UmsMember umsMemberCheck) {

        UmsMember umsMember = userMapper.selectOne(umsMemberCheck);
        return umsMember;
    }

    @Override
    public UmsMemberReceiveAddress getReceiveAddressById(String receiveAddressId) {
        UmsMemberReceiveAddress umsMemberReceiveAddress=new UmsMemberReceiveAddress();
        umsMemberReceiveAddress.setId(receiveAddressId);
        UmsMemberReceiveAddress umsMemberReceiveAddress1 = umsMemberReceiveAddressMapper.selectOne(umsMemberReceiveAddress);
        return umsMemberReceiveAddress1;
    }


    private UmsMember loginFromDb(UmsMember umsMember) {
        List<UmsMember> umsMembers = userMapper.select(umsMember);
        if(umsMembers!=null){
            //防止查询出多条数据，取第一条
            return umsMembers.get(0);
        }
        return null;
    }
}
