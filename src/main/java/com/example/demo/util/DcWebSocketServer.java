package com.example.demo.util;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/imserver/{userId}")
@Component

public class DcWebSocketServer {
    static Log logger= LogFactory.get(DcWebSocketServer.class);
    /**
     * 在线数量
     */
    @Getter
    @Setter
    private static  int onlineCount=0;
    /**
     * concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。
     */
    private static ConcurrentHashMap<String,DcWebSocketServer> socketMap=new ConcurrentHashMap<>();
    /**
     * 与某个客户端的连接会话，需要通过它来给客户端发送数据
     */
    private Session session;
    /**
     * 接收userId
     */
    private String userId="";

    @OnOpen
    public void onOpen(Session session, @PathParam("userId")String userId){
        this.session=session;
        this.userId=userId;
        if(socketMap.containsKey(userId)){
            socketMap.remove(userId);
            socketMap.put(userId,this);
        }else{
            socketMap.put(userId,this);
            addOnlineCount();
        }
        logger.info("用户连接:"+userId+",当前在线人数为:" + getOnlineCount());

        try {
            sendMessage("success");
        }catch (IOException exception){
            logger.error(exception);
        }
    }

    @OnClose
    public void onClose(){
        if(socketMap.containsKey(userId)){
            socketMap.remove(userId);
            subOnlineCount();
        }
        logger.info("用户退出："+userId+",当前在线人数为:"+onlineCount);
    }
    @OnMessage
    public void onMessage(String message,Session session){
        logger.info("用户消息:"+userId+",报文:"+message);
        //可以群发消息
        //消息保存到数据库、redis
        if(!StringUtils.isEmpty(message)){
            try {
                //解析发送的报文
                JSONObject jsonObject = JSON.parseObject(message);
                //追加发送人(防止串改)
                jsonObject.put("fromUserId",this.userId);
                String toUserId=jsonObject.getString("toUserId");
                //传送给对应toUserId用户的websocket
                if(!StringUtils.isEmpty(toUserId)&&socketMap.containsKey(toUserId)){
                    socketMap.get(toUserId).sendMessage(jsonObject.toJSONString());
                }else{
                    logger.error("请求的userId:"+toUserId+"不在该服务器上");
                    //否则不在这个服务器上，发送到mysql或者redis
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        logger.error("用户错误:"+this.userId+",原因:"+error.getMessage());
        error.printStackTrace();
    }
    /**
     * 实现服务器主动推送
     */
    public void sendMessage(String message) throws IOException {
        this.session.getBasicRemote().sendText(message);
    }
    public static synchronized int getOnlineCount() {
        return onlineCount;
    }

    public static synchronized void addOnlineCount() {
        DcWebSocketServer.onlineCount++;
    }

    public static synchronized void subOnlineCount() {
        DcWebSocketServer.onlineCount--;
    }

    /**
     * 发送自定义消息
     * */
    public static void sendInfo(String message,@PathParam("userId") String userId) throws IOException {
        logger.info("发送消息到:"+userId+"，报文:"+message);
        if(!StringUtils.isEmpty(message)&&socketMap.containsKey(userId)){
            socketMap.get(userId).sendMessage(message);
        }else{
            logger.error("用户"+userId+",不在线！");
        }
    }
}
