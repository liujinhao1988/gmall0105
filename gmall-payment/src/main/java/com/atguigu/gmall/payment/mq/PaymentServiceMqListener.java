package com.atguigu.gmall.payment.mq;

import com.atguigu.gmall.bean.PaymentInfo;
import com.atguigu.gmall.service.PaymentService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import java.util.Date;
import java.util.Map;

@Component
public class PaymentServiceMqListener {
    @Autowired
    PaymentService paymentService;

    @JmsListener(destination  ="PAYMENT_CHECK_QUEUE",containerFactory = "jmsQueueListener")
    public void consumePaymentCheckResult(MapMessage mapMessage) throws JMSException {
        String out_trade_no=mapMessage.getString("out_trade_no");
        //Integer count = Integer.parseInt(""+mapMessage.getString("count"));
        //以上,报错count = null;
        Integer count = 0;
        if(mapMessage.getString("count")!=null){
            count = Integer.parseInt(""+mapMessage.getString("count"));
        }



        //调用paymentService的支付宝检查借口
        System.out.println("进行延迟检查,调用支付检查的接口服务");


        //测试时为null
        Map<String,Object> resultMap =paymentService.checkAlipayPayment(out_trade_no);
        //Map<String,Object> resultMap =null;


        //空指针问题
        //检查resultMap不为空
        if(resultMap!=null&&!resultMap.isEmpty()){
            String trade_status=(String)resultMap.get("trade_status");
            //根据查询的支付状态结果,判断是否进行下一次的延迟任务还是支付成功更新数据和后续任务
            //空指针问题
            //检查trade_status不为空
            if(StringUtils.isNotBlank(trade_status) &&trade_status.equals("TRADE_SUCCESS")){
                //支付成功,更新支付发送支付队列
                PaymentInfo paymentInfo=new PaymentInfo();
                paymentInfo.setOrderSn(out_trade_no);
                paymentInfo.setPaymentStatus("已支付");
                paymentInfo.setAlipayTradeNo((String) resultMap.get("trade_no"));//支付宝的交易凭证好
                paymentInfo.setCallbackContent((String) resultMap.get("call_back_content"));//回调请求字符锤昂
                paymentInfo.setCallbackTime(new Date());

                paymentService.updatePayment(paymentInfo);
                System.out.println("已经支付成功,调用支付服务,修改支付信息发送支付成功的队列");
                return;//跳出
            }
        }

        //resultMap为空
        if(count>0){
            //继续发送延迟检查任务,计算延迟时间等
            System.out.println("没有支付成功,检查剩余次数为"+count+",继续发送延迟检查任务");
            count--;
            paymentService.sendDelayPaymentResultCheckQueue(out_trade_no,count);
        } else {
            System.out.println("检查剩余次数用尽,结束检查");
        }



    }
}
