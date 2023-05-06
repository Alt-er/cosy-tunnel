package cn.com.apexedu.forward.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PortForwardOnlineManager {
    static final Logger logger = LoggerFactory.getLogger(PortForwardOnlineManager.class);
//    static {
//        new Thread(()->{
//            for(;;){
//                try {
//                    Thread.sleep(5000);
//                    List<PortForwardInfo> onlineList = getOnlineList();
//                    onlineList.forEach(l -> {
//                        logger.debug("定时打印在线端口转发列表 >> {}:{} => {}" , l.getLocalIp() , l.getLocalPort() ,l.getRemotePort());
//                    });
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        }).start();
//    }

    private static CopyOnWriteArrayList<PortForwardInfo> portForwardInfos = new CopyOnWriteArrayList<>();

    public static synchronized boolean add(PortForwardInfo portForwardInfo) {
        boolean contains = portForwardInfos.stream().anyMatch(p -> {
            return p.getRemotePort() == portForwardInfo.getRemotePort();
        });
        if (contains) {
            logger.debug("添加端口转发失败,端口:{}在服务端已被监听...");
            return false;
        }
        portForwardInfos.add(portForwardInfo);
        return true;
    }

    public static synchronized boolean remove(PortForwardInfo portForwardInfo) {
        return portForwardInfos.remove(portForwardInfo);
    }

    /**
     * 获取当前正在监听的端口
     *
     * @return
     */
    public static synchronized List<PortForwardInfo> getOnlineList() {
        return new ArrayList<>(portForwardInfos);
    }

    /**
     * 情况所有数据
     */
    public static synchronized void clear() {
        portForwardInfos.clear();
    }

}
