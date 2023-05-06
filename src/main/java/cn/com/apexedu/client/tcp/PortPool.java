package cn.com.apexedu.client.tcp;

import cn.com.apexedu.client.websocket.WebSocketClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PortPool {
    private Set<Integer> availablePorts;
    private Map<Integer, Integer> usedPorts;
    private int start;
    private int end;

    private static int timeout = 60 * 30;

    private long startupTime = System.currentTimeMillis() / 1000;

    public PortPool(int start, int end) {
        this.start = start;
        this.end = end;
        availablePorts = new HashSet<>();
        usedPorts = new ConcurrentHashMap<>();
        for (int i = start; i <= end; i++) {
            availablePorts.add(i);
        }
    }

    public synchronized int getPort() {
        if (availablePorts.isEmpty()) {
            int diff = (int) (System.currentTimeMillis() / 1000 - startupTime);
            // 创建迭代器并遍历Map
            Iterator<Map.Entry<Integer, Integer>> iterator = usedPorts.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, Integer> entry = iterator.next();
                if (diff - entry.getValue() > timeout) { // 满足条件的元素
                    iterator.remove(); // 删除该元素
                    availablePorts.add(entry.getKey());
                }
            }

            if (availablePorts.isEmpty())
                throw new RuntimeException("No available port in pool.");
        }
        int port = availablePorts.stream().findFirst().get();
        availablePorts.remove(port);
        usedPorts.put(port, (int) (System.currentTimeMillis() / 1000 - startupTime));
        return port;
    }

    public synchronized void releasePort(int port) {
        if (usedPorts.containsKey(port)) {
            usedPorts.remove(port);
            availablePorts.add(port);
        }
    }

    public void logLastUsedTime(int port) {
        if (usedPorts.containsKey(port)) {
            usedPorts.replace(port, (int) (System.currentTimeMillis() / 1000 - startupTime));
        }
    }

    public static void main(String[] args) {


        PortPool pool = new PortPool(50000, 60000);
        for (int i = 0; i < 10; i++) {
            int port = pool.getPort();
            System.out.println("Get port: " + port);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            pool.releasePort(port);
            System.out.println("Release port: " + port);
        }
    }
}