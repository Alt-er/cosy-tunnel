package cn.com.apexedu.forward.services;

public class PortForwardInfo {

    public PortForwardInfo(String localIp, int localPort, int remotePort) {
        this.remotePort = remotePort;
        this.localIp = localIp;
        this.localPort = localPort;
    }

    // 远程监听的端口
    private int remotePort;

    // 本地监听的IP
    private String localIp;

    // 本地监听的端口
    private int localPort;

    public int getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(int remotePort) {
        this.remotePort = remotePort;
    }

    public String getLocalIp() {
        return localIp;
    }

    public void setLocalIp(String localIp) {
        this.localIp = localIp;
    }

    public int getLocalPort() {
        return localPort;
    }

    public void setLocalPort(int localPort) {
        this.localPort = localPort;
    }
}
