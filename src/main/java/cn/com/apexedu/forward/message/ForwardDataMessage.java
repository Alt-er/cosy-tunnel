package cn.com.apexedu.forward.message;

public class ForwardDataMessage extends Message {

    public ForwardDataMessage(int connectionId , byte[] payload ) {
        this.connectionId = connectionId;
        this.payload = payload;
    }

    private byte [] payload;

    private int connectionId;

    public int getConnectionId() {
        return connectionId;
    }

    @Override
    public int getSequenceId() {
//        return super.getSequenceId();
        return this.getConnectionId();
    }


    public void setConnectionId(int connectionId) {
        this.connectionId = connectionId;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    @Override
    public int getMessageType() {
        return FORWARD_DATA_MESSAGE;
    }
}
