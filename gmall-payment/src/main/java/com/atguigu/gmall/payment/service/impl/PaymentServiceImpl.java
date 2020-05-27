package com.atguigu.gmall.payment.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.atguigu.gmall.bean.PaymentInfo;
import com.atguigu.gmall.mq.ActiveMQUtil;
import com.atguigu.gmall.payment.mapper.PaymentInfoMapper;
import com.atguigu.gmall.service.PaymentService;
import org.apache.activemq.ScheduledMessage;
import org.apache.activemq.command.ActiveMQMapMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import tk.mybatis.mapper.entity.Example;

import javax.jms.*;
import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    PaymentInfoMapper paymentInfoMapper;

    @Autowired
    ActiveMQUtil activeMQUtil;

    @Autowired
    AlipayClient alipayClient;

    @Override
    public void savePaymentInfo(PaymentInfo paymentInfo) {

        paymentInfoMapper.insertSelective(paymentInfo);

    }

    @Override
    public void updatePayment(PaymentInfo paymentInfo) {
        //幂等性检查
        PaymentInfo paymentInfoParam=new PaymentInfo();
        paymentInfoParam.setOrderSn(paymentInfo.getOrderSn());
        PaymentInfo paymentInfoResult = paymentInfoMapper.selectOne(paymentInfoParam);
        if(StringUtils.isNotBlank(paymentInfoResult.getPaymentStatus())&&paymentInfoResult.getPaymentStatus().equals("已支付")){
            return;
        }else {
            String orderSn=paymentInfo.getOrderSn();

            Example e=new Example(PaymentInfo.class);
            e.createCriteria().andEqualTo("orderSn",orderSn);


            Connection connection =null;
            Session session =null;
            try {
                connection = activeMQUtil.getConnectionFactory().createConnection();
                session = connection.createSession(true, Session.SESSION_TRANSACTED);
            } catch (JMSException ex) {
                ex.printStackTrace();
            }

            try{
                paymentInfoMapper.updateByExampleSelective(paymentInfo,e);

                //支付成功后引起的系统服务，订单服务，库存服务，物流
                //调用mq发送支付成功的消息

                Queue payhment_success_queue = session.createQueue("PAYHMENT_SUCCESS_QUEUE");
                MessageProducer producer = session.createProducer(payhment_success_queue);


                //两种ｍｅｓｓａｇｅ
                //TextMessage textMessage = new ActiveMQTextMessage();//字符串文本

                MapMessage mapMessage = new ActiveMQMapMessage();//ｈａｓｈ结构

                mapMessage.setString("out_trade_no",paymentInfo.getOrderSn());
                producer.send(mapMessage);

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

    @Override
    public void sendDelayPaymentResultCheckQueue(String outTradeNo,int count) {
        Connection connection =null;
        Session session =null;
        try {
            connection = activeMQUtil.getConnectionFactory().createConnection();
            session = connection.createSession(true, Session.SESSION_TRANSACTED);
        } catch (JMSException ex) {
            ex.printStackTrace();
        }

        try{


            Queue payhment_success_queue = session.createQueue("PAYMENT_CHECK_QUEUE");
            MessageProducer producer = session.createProducer(payhment_success_queue);


            //两种ｍｅｓｓａｇｅ
            //TextMessage textMessage = new ActiveMQTextMessage();//字符串文本

            MapMessage mapMessage = new ActiveMQMapMessage();//ｈａｓｈ结构

            mapMessage.setString("out_trade_no",outTradeNo);
            mapMessage.setInt("count",count);

            //延迟消息队列的延迟时间，若无则是普通消息队列
            //延迟半分钟
            mapMessage.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY,1000*10);

            producer.send(mapMessage);

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

    @Override
    public Map<String, Object> checkAlipayPayment(String out_trade_no) {
        Map<String,Object> resultMap = new HashMap<>();

        AlipayTradeQueryRequest request=new AlipayTradeQueryRequest();
        Map<String,Object> requestMap = new HashMap<>();
        requestMap.put("out_trade_no",out_trade_no);
        request.setBizContent(JSON.toJSONString(requestMap));
        AlipayTradeQueryResponse response= null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        if(response.isSuccess()){
            System.out.println("有可能交易已创建,调用成功");
            resultMap.put("out_trade_no",response.getOutTradeNo());
            resultMap.put("trade_no",response.getTradeNo());
            resultMap.put("trade_status",response.getTradeStatus());
            resultMap.put("call_back_content",response.getMsg());
        }else {
            System.out.println("有可能交易未创建,调用失败");
        }


        return resultMap;
    }
}
