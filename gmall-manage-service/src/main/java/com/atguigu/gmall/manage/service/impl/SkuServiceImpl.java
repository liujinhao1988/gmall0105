package com.atguigu.gmall.manage.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.PmsSkuAttrValue;
import com.atguigu.gmall.bean.PmsSkuImage;
import com.atguigu.gmall.bean.PmsSkuInfo;
import com.atguigu.gmall.bean.PmsSkuSaleAttrValue;
import com.atguigu.gmall.manage.mapper.PmsSkuAttrValueMapper;
import com.atguigu.gmall.manage.mapper.PmsSkuImageMapper;
import com.atguigu.gmall.manage.mapper.PmsSkuInfoMapper;
import com.atguigu.gmall.manage.mapper.PmsSkuSaleAttrValueMapper;
import com.atguigu.gmall.service.SkuService;
import com.atguigu.gmall.util.RedisUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;


import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class SkuServiceImpl implements SkuService {
    @Autowired
    PmsSkuInfoMapper pmsSkuInfoMapper;

    @Autowired
    PmsSkuAttrValueMapper pmsSkuAttrValueMapper;

    @Autowired
    PmsSkuSaleAttrValueMapper pmsSkuSaleAttrValueMapper;

    @Autowired
    PmsSkuImageMapper pmsSkuImageMapper;


    @Autowired
    RedisUtil redisUtil;

    @Override
    public void saveSkuInfo(PmsSkuInfo pmsSkuInfo) {

        //插入skuInfo
        int i = pmsSkuInfoMapper.insertSelective(pmsSkuInfo);
        String skuId=pmsSkuInfo.getId();

        //插入平台属性关联
        List<PmsSkuAttrValue> skuAttrValueList = pmsSkuInfo.getSkuAttrValueList();

        for (PmsSkuAttrValue pmsSkuAttrValue : skuAttrValueList) {
            pmsSkuAttrValue.setSkuId(skuId);
            pmsSkuAttrValueMapper.insertSelective(pmsSkuAttrValue);
        }


        //插入销售属性关联

        List<PmsSkuSaleAttrValue> skuSaleAttrValueList = pmsSkuInfo.getSkuSaleAttrValueList();
        for (PmsSkuSaleAttrValue pmsSkuSaleAttrValue : skuSaleAttrValueList) {
            pmsSkuSaleAttrValue.setSkuId(skuId);
            pmsSkuSaleAttrValueMapper.insertSelective(pmsSkuSaleAttrValue);
        }

        //插入图片信息

        List<PmsSkuImage> skuImageList = pmsSkuInfo.getSkuImageList();
        for (PmsSkuImage pmsSkuImage : skuImageList) {
            pmsSkuImage.setSkuId(skuId);
            pmsSkuImageMapper.insertSelective(pmsSkuImage);
        }
    }


    //改为redis缓存
    public PmsSkuInfo getSkuByIdFromDb(String skuId){
        //sku商品对象
        PmsSkuInfo pmsSkuInfo=new PmsSkuInfo();
        pmsSkuInfo.setId(skuId);
        PmsSkuInfo skuInfo = pmsSkuInfoMapper.selectOne(pmsSkuInfo);
        //sku图片集合
        PmsSkuImage pmsSkuImage=new PmsSkuImage();
        pmsSkuImage.setSkuId(skuId);
        List<PmsSkuImage> pmsSkuImages = pmsSkuImageMapper.select(pmsSkuImage);
        skuInfo.setSkuImageList(pmsSkuImages);
        return skuInfo;
    }




    @Override
    public PmsSkuInfo getSkuById(String skuId,String ip) {

        System.out.println("ip为"+ip+"的同学："+Thread.currentThread().getName()+"进入商品详情请求");

        PmsSkuInfo pmsSkuInfo = new PmsSkuInfo();
        //连接缓存
        Jedis jedis = redisUtil.getJedis();
        //查询缓存
        String skuKey="sku:"+skuId+":info";
        String skuJson=jedis.get(skuKey);

        if(StringUtils.isNotBlank(skuJson)){
            System.out.println("ip为"+ip+"的同学："+Thread.currentThread().getName()+"从缓存中获取商品详情");
            pmsSkuInfo=JSON.parseObject(skuJson,PmsSkuInfo.class);
        }else{

            System.out.println("ip为"+ip+"的同学："+Thread.currentThread().getName()+"缓存中没有，申请分布式锁： "+"sku:"+skuId+":lock");
            //如果缓存中没有 查询mysql
            //设置分布式锁
            String token = UUID.randomUUID().toString();
            String OK = jedis.set("sku:" + skuId + ":lock", token, "nx", "px", 10*1000);

            if(StringUtils.isNotBlank(OK)&&OK.equals("OK")){
                System.out.println("ip为"+ip+"的同学："+Thread.currentThread().getName()+"有权在10秒的过期时间内访问数据库： "+"sku:"+skuId+":lock");
                //设置成功，有权在10秒的过期时间内访问数据库
                pmsSkuInfo=getSkuByIdFromDb(skuId);
                try {
                    Thread.sleep(1000*5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(pmsSkuInfo!=null){
                    //mysql查询结果存入redis
                    jedis.set("sku:"+skuId+":info",JSON.toJSONString(pmsSkuInfo));
                }else{
                    //数据库中不存在该sku
                    //为了防止缓存穿透，将null或者空字符串设置给redis
                    jedis.setex("sku:"+skuId+":info",60*3,JSON.toJSONString(""));//三分钟不会有数据打到数据库上
                }

                //

                //在访问mysql后，将mysql的分布式锁释放
                System.out.println("ip为"+ip+"的同学："+Thread.currentThread().getName()+"使用完毕，将锁归还： "+"sku:"+skuId+":lock");

                //问题1 如果在redis中的锁已经过期了，然后锁过期的那个请求又执行完毕，回来删锁,删除了其他线程的锁，怎么办？

                //不同的锁key是一样的，但可以将value设置的不一样 这样在删锁时知道删哪个
                String lockToken = jedis.get("sku:" + skuId + ":lock");
                if(StringUtils.isNotBlank(lockToken)&&lockToken.equals(token)){
                    //问题2 如果碰巧在查询redis锁还没删除的时候，正在网络传输时，锁过期了
                    //怎么办？

                    // jedis.eval("lua");可与用lua脚本，在查询到key的同时删除该key，防止高并发下的意外发生

                    jedis.del("sku:" + skuId + ":lock");//用tocken确认删哪个锁
                }

            }else{
                System.out.println("ip为"+ip+"的同学："+Thread.currentThread().getName()+"自旋（该线程在睡眠几秒后，重新尝试访问）");
                //设置失败 自旋（该线程在睡眠几秒后，重新尝试访问）
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return getSkuById(skuId,ip);
            }



        }
        jedis.close();
        return pmsSkuInfo;
    }

    @Override
    public List<PmsSkuInfo> getSkuSaleAttrValueListBySpu(String productId) {

        List<PmsSkuInfo> pmsSkuInfos = pmsSkuInfoMapper.selectSkuSaleAttrValueListBySpu(productId);

        return pmsSkuInfos;
    }

    @Override
    public List<PmsSkuInfo> getAllSku(String catalog3Id) {
        List<PmsSkuInfo> pmsSkuInfos =pmsSkuInfoMapper.selectAll();
        for (PmsSkuInfo pmsSkuInfo : pmsSkuInfos) {
            String skuId =pmsSkuInfo.getId();

            PmsSkuAttrValue pmsSkuAttrValue=new PmsSkuAttrValue();
            pmsSkuAttrValue.setSkuId(skuId);
            List<PmsSkuAttrValue> select = pmsSkuAttrValueMapper.select(pmsSkuAttrValue);
            pmsSkuInfo.setSkuAttrValueList(select);
        }
        return pmsSkuInfos;
    }

    @Override
    public boolean checkPrice(String productSkuId, BigDecimal productPrice) {
        boolean b=false;
        PmsSkuInfo pmsSkuInfo = new PmsSkuInfo();
        pmsSkuInfo.setId(productSkuId);
        PmsSkuInfo pmsSkuInfo1 = pmsSkuInfoMapper.selectOne(pmsSkuInfo);

        BigDecimal price = pmsSkuInfo1.getPrice();
        //比较函数用compareTo
        if(price.compareTo(productPrice)==0) {
            b=true;
        }


        return b;
    }
}
