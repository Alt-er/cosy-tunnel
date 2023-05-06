package cn.com.apexedu.forward.session;

import io.netty.channel.Channel;

import java.util.concurrent.ConcurrentHashMap;

public class Session {

    final static ConcurrentHashMap<Channel,Session > sessionMap = new ConcurrentHashMap<>();


    // 远程监听的端口
    private int remotePort;

    // 本地监听的IP
    private String localIp;

    // 本地监听的端口
    private int localPort;


    public Session getSession(Channel channel){
        return sessionMap.get(channel);
    }

    public void removeSession(Channel channel){
        sessionMap.remove(channel);
    }


}
