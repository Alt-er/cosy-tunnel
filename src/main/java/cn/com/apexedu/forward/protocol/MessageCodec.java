package cn.com.apexedu.forward.protocol;

import cn.com.apexedu.forward.message.ForwardDataMessage;
import cn.com.apexedu.forward.message.Message;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import java.util.List;

public class MessageCodec extends ByteToMessageCodec<Message> {

    private static final Gson gson = new Gson();

    public static byte[] toJson(Message obj) {
        return gson.toJson(obj).getBytes();
    }

    public static Message fromJson(byte[] bytes, Class<? extends Message> cls) {
        return gson.fromJson(new String(bytes), cls);
    }

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Message message, ByteBuf out) throws Exception {
        // 1.魔数
        out.writeBytes("cosy".getBytes());
        // 2.版本
        out.writeByte(1);
        // 3.序列化方式 0 json , 1 保留
        out.writeByte(0);
        // 4.指令
        out.writeByte(message.getMessageType());
        // 5.seq
        out.writeInt(message.getSequenceId());
        // 6.填充1byte 凑12byte
        out.writeByte(0xff);
        // 7.Serializable 序列化
        byte[] bytes = null;
        if (message.getMessageType() == Message.FORWARD_DATA_MESSAGE) {
            // 如果是转发数据包就优化一下 不要发无关的东西 直接把字节数据放进去
            ForwardDataMessage fdm = (ForwardDataMessage) message;
            bytes = fdm.getPayload();
        } else {
            bytes = toJson(message);
        }
        // 8.写入长度
        out.writeInt(bytes.length);
        // 9.写入数据
        out.writeBytes(bytes);
    }

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf in, List<Object> list) throws Exception {
        // 1.获取魔数
        int magicNum = in.readInt();
        // 2.获取版本
        byte version = in.readByte();
        // 3.获取序列化方式
        byte serializerType = in.readByte();
        // 4.指令 消息类型
        byte messageType = in.readByte();
        // 5.获取序列号
        int sequenceId = in.readInt();
        // 6.获取填充占位
        byte fill = in.readByte();
        // 7.读取内容的长度
        int dataLength = in.readInt();
        // 8.获取内容
        byte[] bytes = new byte[dataLength];
        in.readBytes(bytes, 0, dataLength);
        // 9.反序列化
        if (messageType == Message.FORWARD_DATA_MESSAGE) {
            // sequenceId 序列化的时候处理过了 sequenceId就是连接id , 暂时这么处理
            Message message = new ForwardDataMessage(sequenceId, bytes);
            list.add(message);
        } else {
            Message message = fromJson(bytes, Message.getMessageClassByType(messageType));
            list.add(message);
        }

    }
}
