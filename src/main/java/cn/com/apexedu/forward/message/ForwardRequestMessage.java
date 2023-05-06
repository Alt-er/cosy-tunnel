package cn.com.apexedu.forward.message;

public class ForwardRequestMessage extends Message {

    private String username;

    private String password;

    // 远程监听的端口
    private int remotePort;

    // 本地监听的IP
    private String localIp;

    // 本地监听的端口
    private int localPort;

    public ForwardRequestMessage(String username, String password, String localIp, int localPort, int remotePort) {
        this.username = username;
        this.password = password;
        this.localIp = localIp;
        this.localPort = localPort;
        this.remotePort = remotePort;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

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

    @Override
    public int getMessageType() {
        return FORWARD_REQUEST_MESSAGE;
    }
}
