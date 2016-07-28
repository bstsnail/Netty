import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.TcpConnection;
import org.apache.log4j.Logger;
import protocol.Protocol;
import utils.string.StringUtils;

public class EventHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = Logger.getLogger(EventHandler.class);
    private ByteBuf mByteBuf;
    private TcpConnection mTcpConnection;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        mByteBuf = ctx.alloc().buffer();
        mTcpConnection = new TcpConnection(ctx);

        System.out.println("Added " + ctx.toString());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        mTcpConnection.close();
        mByteBuf.release();
        mByteBuf = null;

        System.out.println("Removed " + ctx.toString());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.out.println("exceptionCaught " + cause.toString());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws NumberFormatException {
        ByteBuf tmpBuf = (ByteBuf) msg;
        mByteBuf.writeBytes(tmpBuf);
        tmpBuf.release();

        while (mByteBuf.readableBytes() >= Protocol.MAX_HEAD_LEN) {
            // System.out.println("readable: " + mByteBuf.readableBytes());

            // get bytes from byte buffer.
            byte[] headBuf = new byte[Protocol.MAX_HEAD_LEN];
            mByteBuf.getBytes(0, headBuf);

            int bodyLen = StringUtils.string2Int(new String(headBuf));

            // something goes wrong. break the loop.
            if (bodyLen <= 0 || bodyLen > Protocol.MAX_DATA_LEN) {
                mTcpConnection.close();
                break;
            }

            // not enough data. wait for next read.
            if (mByteBuf.readableBytes() < (bodyLen + Protocol.MAX_HEAD_LEN))
                break;

            // get body bytes from byte buffer.
            byte[] bodyBuf = new byte[bodyLen];
            mByteBuf.getBytes(Protocol.MAX_HEAD_LEN, bodyBuf);

            boolean result = HandlerDispatcher.distribute(mTcpConnection, new String(bodyBuf));

            // get head and body data from buffer. and decrease readIndex and writeIndex.
            ByteBuf pckBuf = mByteBuf.readBytes(Protocol.MAX_HEAD_LEN + bodyLen);
            pckBuf.release();
            mByteBuf.discardReadBytes();

            if (!result) {
                mTcpConnection.close();
                break;
            }
        }
    }
}