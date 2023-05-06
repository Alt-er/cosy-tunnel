package cn.com.apexedu.forward.protocol;

import cn.com.apexedu.forward.message.Message;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.List;

@ChannelHandler.Sharable
public class MessageCodecSharable extends MessageToMessageCodec<ByteBuf , Message> {

    private static final Gson gson = new Gson();

    public static byte[] toJson(Message obj) {
        return gson.toJson(obj).getBytes();
    }

    public static Message fromJson(byte[] bytes) {
        return gson.fromJson(new String(bytes), Message.class);
    }


    @Override
    protected void encode(ChannelHandlerContext ctx, Message message, List<Object> outList) throws Exception {
        ByteBuf out =ctx.alloc().buffer();
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
        byte[] bytes = toJson(message);
        // 8.写入长度
        out.writeInt(bytes.length);
        // 9.写入数据
        out.writeBytes(bytes);

        outList.add(out);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 1.获取魔数
        int magicNum = in.readInt();
        // 2.获取版本
        byte version = in.readByte();
        // 3.获取序列化方式
        byte serializerType = in.readByte();
        // 4.指令 消息类型
        byte messageType =  in.readByte();
        // 5.获取序列号
        int sequenceId = in.readInt();
        // 6.获取填充占位
        byte fill = in.readByte();
        // 7.读取内容的长度
        int dataLength = in.readInt();
        // 8.获取内容
        byte [] bytes = new byte[dataLength];
        in.readBytes(bytes ,0 , dataLength);
        // 9.反序列化
        Message message = fromJson(bytes);

        out.add(message);
    }
}
