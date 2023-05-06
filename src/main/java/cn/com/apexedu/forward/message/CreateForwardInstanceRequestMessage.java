package cn.com.apexedu.forward.message;

public class CreateForwardInstanceRequestMessage extends Message {


    public CreateForwardInstanceRequestMessage(int connectionId, String localIp, int localPort, int remotePort) {
        this.remotePort = remotePort;
        this.localIp = localIp;
        this.localPort = localPort;
        this.connectionId = connectionId;
    }

    private int connectionId;
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

    public int getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(int connectionId) {
        this.connectionId = connectionId;
    }

    @Override
    public int getMessageType() {
        return CREATE_FORWARD_INSTANCE_REQUEST_MESSAGE;
    }
}
