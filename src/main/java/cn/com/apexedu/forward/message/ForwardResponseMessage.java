package cn.com.apexedu.forward.message;

public class ForwardResponseMessage extends Message {

    public ForwardResponseMessage(boolean success, String message, String localIp, int localPort, int remotePort) {
        this.success = success;
        this.message = message;
        this.localIp = localIp;
        this.localPort = localPort;
        this.remotePort = remotePort;
    }

    // 远程监听的端口
    private int remotePort;

    // 本地监听的IP
    private String localIp;

    // 本地监听的端口
    private int localPort;

    private boolean success;

    private String message;

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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    @Override
    public int getMessageType() {
        return FORWARD_RESPONSE_MESSAGE;
    }
}
