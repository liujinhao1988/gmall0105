package com.atguigu.gmall.order.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.OmsOrder;
import com.atguigu.gmall.bean.OmsOrderItem;
import com.atguigu.gmall.mq.ActiveMQUtil;
import com.atguigu.gmall.order.mapper.OmsOrderItemMapper;
import com.atguigu.gmall.order.mapper.OmsOrderMapper;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.OrderService;
import com.atguigu.gmall.util.RedisUtil;
import org.apache.activemq.command.ActiveMQMapMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;
import tk.mybatis.mapper.entity.Example;

import javax.jms.*;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    RedisUtil redisUtil;

    @Autowired
    OmsOrderMapper omsOrderMapper;

    @Autowired
    OmsOrderItemMapper omsOrderItemMapper;

    @Reference
    CartService cartService;
    @Autowired
    ActiveMQUtil activeMQUtil;

    @Override
    public String checkTradeCode(String memberId, String tradeCode) {

        Jedis jedis = null;

        try {
            jedis = redisUtil.getJedis();

            String tradeKey = "user:" + memberId + ":tradeCode";

            String tradeCodeFromCache = jedis.get(tradeKey);//使用lua脚本在发现key的同时将key删除，防止并发订单攻击

            //lua脚本
            //对比防重删令牌
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

            Long eval = (Long) jedis.eval(script, Collections.singletonList(tradeKey),
                    Collections.singletonList(tradeCode));


            //if(StringUtils.isNotBlank(tradeCodeFromCache)&&tradeCodeFromCache.equals(tradeCode)){
            if (eval != null && eval != 0) {
                //jedis.del(tradeKey);

                return "success";
            } else {
                return "fail";
            }
        } finally {
            jedis.close();
        }

    }

    @Override
    public String genTradeCode(String memberId) {
        Jedis jedis = redisUtil.getJedis();

        String tradeKey = "user:" + memberId + ":tradeCode";

        String tradeCode = UUID.randomUUID().toString();

        jedis.setex(tradeKey, 60 * 15, tradeCode);

        jedis.close();
        return tradeCode;
    }

    @Override
    public void saveOrder(OmsOrder omsOrder) {
        //保存订单表

        omsOrderMapper.insertSelective(omsOrder);
        String orderId = omsOrder.getId();
        //保存订单详情
        List<OmsOrderItem> omsOrderItems = omsOrder.getOmsOrderItems();
        for (OmsOrderItem omsOrderItem : omsOrderItems) {
            omsOrderItem.setOrderId(orderId);
            // 插入
            omsOrderItemMapper.insertSelective(omsOrderItem);

            //同时要删除购物车
            // cartService.delCart();
        }

    }

    @Override
    public OmsOrder getOrderByOutTradeNo(String outTradeNo) {

        OmsOrder omsOrder = new OmsOrder();
        omsOrder.setOrderSn(outTradeNo);
        OmsOrder omsOrder1 = omsOrderMapper.selectOne(omsOrder);
        return omsOrder1;
    }

    @Override
    public void updateOrder(OmsOrder omsOrder) {

        Example e=new Example(OmsOrder.class);
        e.createCriteria().andEqualTo("orderSn",omsOrder.getOrderSn());

        OmsOrder omsOrderUpdate=new OmsOrder();

        omsOrderUpdate.setStatus("1");


        //发送新的队列，d\订单已支付，提供给库存消费
        Connection connection =null;
        Session session =null;

        try{
            connection = activeMQUtil.getConnectionFactory().createConnection();
            session = connection.createSession(true, Session.SESSION_TRANSACTED);


            Queue payhment_success_queue = session.createQueue("ORDER_PAY_QUEUE");
            MessageProducer producer = session.createProducer(payhment_success_queue);


            //两种ｍｅｓｓａｇｅ
            TextMessage textMessage = new ActiveMQTextMessage();//字符串文本

            //MapMessage mapMessage = new ActiveMQMapMessage();//ｈａｓｈ结构

            OmsOrder omsOrderParam =new OmsOrder();
            omsOrderParam.setOrderSn(omsOrder.getOrderSn());
            OmsOrder omsOrderResponse = omsOrderMapper.selectOne(omsOrderParam);

            OmsOrderItem omsOrderItemParam =new OmsOrderItem();
            omsOrderItemParam.setOrderSn(omsOrderParam.getOrderSn());
            List<OmsOrderItem> select = omsOrderItemMapper.select(omsOrderItemParam);

            omsOrderResponse.setOmsOrderItems(select);


            textMessage.setText(JSON.toJSONString(omsOrderResponse));



            omsOrderMapper.updateByExampleSelective(omsOrderUpdate,e);
            producer.send(textMessage);

            session.commit();
        }catch (Exception es){
            //消息回滚
            try {
                session.rollback();
            } catch (JMSException ex) {
                ex.printStackTrace();
            }
        }finally {
            try {
                connection.close();
            } catch (JMSException ex) {
                ex.printStackTrace();
            }
        }

    }

}
