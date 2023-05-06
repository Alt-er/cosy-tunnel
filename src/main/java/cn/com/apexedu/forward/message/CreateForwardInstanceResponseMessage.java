package cn.com.apexedu.forward.message;

public class CreateForwardInstanceResponseMessage extends Message {

    public CreateForwardInstanceResponseMessage(int connectionId , boolean success , String message) {
        this.connectionId = connectionId;
        this.message = message;
        this.success = success;
    }
    private int connectionId;

    private boolean success;

    private String message;

    public int getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(int connectionId) {
        this.connectionId = connectionId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public int getMessageType() {
        return CREATE_FORWARD_INSTANCE_RESPONSE_MESSAGE;
    }
}
