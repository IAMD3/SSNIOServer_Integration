package awesome.yk.all.in.one;
/*
 *  Copyright ©  Yukai Tang
 *  SSNIO Server -> 带给你轻量级的NIO Server体验
 *
 *  一张设计图
 *  一个类文件
 *  一套注释
 *  一份Demo
 *  一个嵌入式NIO容器
 *
 *  大道至简
 *
 * 版本NO.00000000000000001(Dev测试版)
 *
 */

import com.sun.tools.javac.util.Assert;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Description: master T
 * {@code SSNIOServer}实现了一个超轻量级-嵌入式-支持自定义协议-支持HTTP1.0 的NIO 服务器
 * 适用于:
 * *资源有限设备(e.g 树莓派) 高效运行
 * *服务器启动速度极致的场景
 * *极速的请求&响应测试
 * *传输层之上的自定义协议性能测试
 * *作为一个信息管理系统的web载体
 * *静态资源的提供者
 * *JAVA NIO学习
 * <p>
 * 参考Doug Lea <Scalable IO in Java> 中的Reactor模式
 *
 * @author Yukai Tang
 * @link http://gee.cs.oswego.edu/dl/cpjslides/nio.pdf
 *
 * <p>
 * 这款WEB原生支持HTTP1.0协议并适应多数场景
 * 单线程模型(线程未必越多越好) -> 后续考虑推出多线程版本(更高效利用多核处理器&IO资源) -> Reactor线程 & Worker线程的拆分...扩展(未来)
 * <p>
 * SSNIOServer 的完整功能由一个Source文件(此文件)展示 -> 尽可能地降低使用难度(只需要简单的引入该文件就能享受)
 * SSNIO没有使用任何第三方依赖并且集成了HTTP 1.0 的解析实现(往往协议的decode/encode需要使用者自己实现) -> 使用者不用担心任何依赖冲突问题xD
 * 使用者可以通过实现XParser来实现自定义协议的decode逻辑(参考DefaultHttpXParser)
 * 也可以优化/删除集成的HTTP组件(DefaultHttpXParser HttpUtil Request HttpCodeCFactory 四个) -> 对容器进一步简化
 * <p>
 * <p>
 * 跟BIO容器不同NIO Server无法做到用线程隔离请求
 * 也无法保障一次IO读事件后读取的字节流 能解析成一个"完整"的业务数据包(0个 半个 一个 多个 多个半...都可能)
 * 需要考虑 :
 * 1 如何判断一次IO 读事件触发的读函数读取了一个"完整"的业务数据包
 * 2 读取的数据不足以形成一个业务数据包时,在下次IO读事件触发读操作前需要做什么
 * 在设计上 SSNIOServer以连接作为隔离点,每个连接封装为XSocket对象并且独自维护读&写缓存 SocketChannel 写指针等信息
 * 配合特定协议对应的识别组件(SSNIOServer自带HTTPUtil作为HTTP 1.0协议的识别组件 其他自定义协议需要使用者自己写识别逻辑)解决上述两点
 *
 * <p>
 * 文档代码一体:
 * 此Source文件详细描述了每个类与方法的实现与设计思想 在设计上尽可能的满足"可用" "可读" "轻量" "简洁" 四个点
 * 个人非常推荐NIO的初学者使用与学习 如果有任何问题与建议 -> 欢迎随时联系
 * <p>
 * 此版本的SSNIOServer以一个类文件的形式展示(集成对应不同职责的内部类)
 * 也有以多文件形式展示的结构上更清晰的版本
 **/
public class SSNIOServer {

    //***********************************抽象*********************************************************

    /**
     * 用户自定义业务实现的扩展点
     * 责任链模式 -> 可以通过请求的传递完成:
     * A 弱类型XBuffer -> 强类型自定义业务数据包对象 转换 (请求)
     * B 业务处理
     * C 强类型自定义业务数据包对象 ->弱类型XBuffer(响应)
     * 对应Reactor模式下的Worker角色
     * <p>
     * 与Netty这样传统的NIO Server略微不同, 在自定协议的序列化/反序列化上没有对应的Encoder/Decoder类接口需要用户实现
     * 不过....这不代表不需要实现 你可以通过将请求向 "解码Handler" - > "业务Handler" - > "编码Handler"的顺序传递达到一样的效果
     */
    public interface XHandler {
        /**
         * @param reqBuffer "正好"代表一个"完整"业务数据包的请求缓存
         * @return 正好"代表一个"完整"业务数据包的响应缓存
         * <p>
         * 这里...请求 响应对应的业务数据包格式不强求一致
         * e.g 客户端可以 只包含1byte数据的请求获取一个完整的HTTP响应报文
         */
        XBuffer handle(XBuffer reqBuffer);

        /**
         * @return 下一个执行的处理器
         */
        XHandler next();
    }

    /**
     * 用户识别自定义协议下的业务数据包扩展点
     * 解决粘包/拆包问题
     * 实现可参考 {@code DefaultHttpXParser}
     */
    public interface XParser {

        /**
         * @param src "不确定"长度的 经过N(N>1 整数)次NIO read后累加的缓存
         *
         * 函数对应的功能:
         * 1 识别缓存(XBuffer)数据是否足够成为一个完整特定协议下的业务数据包(e.g HTTP下的一个完整报文)
         * 2 使用缓存(XBuffer)中的数据反序列化业务数据包
         * 3 释放缓存(XBuffer)中被成功用于反序列化的数据,保留未被用于反序列化的数据
         */
        void parse(XBuffer src) throws IOException;

        /**
         * @return 反序列化后的"完整"业务数据包数组
         * 需要用户在实现类维护成功反序列化的数据数组
         */
        List<XBuffer> getOutputs();
    }

    /**
     * 获取XParser实现类的工厂
     * <p>
     * <p>
     * 读操作时使用XParser作为 业务数据包的 识别 & 缓存
     * 委派{@code DefaultXWriter}执行写操作
     */
    public interface CodeCFactory {
        XParser createXReader();
    }

    //**********************************实现********************************************************

    /**
     * 承载:
     * 配置 环境变量
     * 模块之间的共享数据
     * 全局函数(比如id生成函数)
     */
    public static class Container {
        /**
         * 缓存初始值
         */
        public static int X_BUFFER_INITIAL_SIZE = 4 * 1024; //4KB

        /**
         * IO 事件对应触发的函数之间
         * 交互用的队列默认大小
         */
        public static final int QUEUE_CAPACITY = 1024;

        /**
         * ID计数
         */
        public static AtomicInteger COUNT = new AtomicInteger(0);

        /**
         * NIO SocketChannel 调用写函数传递的缓存对象
         * 业务模型经过 序列化相关XHandler处理完毕后  -> 生成XBuffer
         * IO写事件 -> XBuffer 拷贝到ByteBuffer -> 进行写操作
         */
        public static ByteBuffer WRITING_MEDIATOR = ByteBuffer.allocate(X_BUFFER_INITIAL_SIZE * 100);

        /**
         * NIO SocketChannel 调用读函数传递的缓存对象
         * IO读事件 -> 读操作填充ByteBuffer -> ByteBuffer 拷贝到XBuffer -> XParser尝试解析业务数据包
         * 传递业务数据包到序列化相关XHandler -> 业务模型
         */
        public static ByteBuffer READING_MEDIATOR = ByteBuffer.allocate(X_BUFFER_INITIAL_SIZE * 100);

        /**
         * 竞争安全的ID 生成函数
         * 用户可以用自定义的id生成 函数替换它(e.g UUID)
         */
        public static String getXSocketId() {
            return "XSocket@" + COUNT.getAndIncrement();
        }
    }


    /**
     * NIO SocketChannel 的封装对象
     * 每个成功建立的客户端连接维护一个独立的XSocket
     * 内部:
     * 维护一个唯一标识符xSocketId(String)
     * 维护一组读写缓存(XBuffer)
     * 维护一个特定协议下的业务数据包解析器(XParser)
     * 维护一个通用的 跨协议的Writer(单个响应对应的业务数据包必须对应单个XBuffer缓存)
     * XSocket维护了读写相关组件/缓存的一对一对应关系
     */
    public class XSocket {
        public String xSocketId;
        public SocketChannel socketChannel;
        public XBuffer readBuffer;
        public XBuffer writeBuffer;
        public XParser xParser;
        public DefaultXWriter xWriter;
        public boolean endOfStreamReached = false;

        public XSocket(SocketChannel sc) {
            this.socketChannel = sc;
            this.xSocketId = Container.getXSocketId();
            this.readBuffer = new XBuffer();
            this.readBuffer.xSocketId = this.xSocketId;
            this.writeBuffer = new XBuffer();
            this.writeBuffer.xSocketId = this.xSocketId;
        }

        /**
         * 委派DefaultXWriter 执行写操作
         */
        public void write() throws IOException {
            xWriter.write(Container.WRITING_MEDIATOR, socketChannel);
        }

        /**
         * IO读事件触发
         * 重复尝试读取二进制数据流 -> 直到在这一次IO读事件下 对应的SocketChannel读不到数据为止
         * 累加IO读事件下累计读取的数据 -> 调用业务数据包解析器XParser尝试解析
         */
        public void read() throws IOException {
            ByteBuffer mediator = Container.READING_MEDIATOR;

            int byteRead = keepReadingToByteBuffer(mediator);
            if (byteRead == 0) return;
            if (byteRead == -1) {
                endOfStreamReached = true;
                return;
            }

            mediator.flip();
            readBuffer.cache(mediator);
            xParser.parse(readBuffer);
            mediator.clear();
        }

        /**
         * @param mediator NIO SocketChannel接收的缓存参数
         * 尝试重复读取 -> 读到本次IO 读事件下无法读取更多数据为止
         */
        private int keepReadingToByteBuffer(ByteBuffer mediator) throws IOException {
            int bytesRead = socketChannel.read(mediator);
            int totalBytesRead = bytesRead;

            while (bytesRead > 0) {
                bytesRead = socketChannel.read(mediator);
                totalBytesRead += bytesRead;
            }

            return totalBytesRead;
        }
    }


    /**
     * {@code XBuffer} 一款简单的缓存设计:
     * 考虑点 -> A.弹性 B.性能
     * 基于传输层的任何自定义协议下的业务数据包都可以用二进制数组表示(byte[] 代码层面的表示)
     * NIO 的特征: 每次读取的数据不一定能decode成一个完整的业务数据包(粘包....拆包....)
     * 使用每个channel维护 读 & 写对应的缓存的方式解决
     * 读:配合特定协议下的业务数据包检查组件 -> 判断 & 反序列化业务数据包
     * 写:配合记录写位置的指针 -> 下一次写事件时resume上次步骤
     * <p>
     * 转化:
     * 支持byte[] -> XBuffer
     * 支持ByteBuffer[] -> XBuffer
     * <p>
     * 关联:
     * 一对读/写XBuffer可对应一个NIO Channel
     * 一个XBuffer可对应一个或N(N可以不是整数)个特定协议下的完整业务数据包的二进制数据
     */
    public class XBuffer {
        /**
         * 客户端连接关联字段
         */
        public String xSocketId;
        /**
         * 数据载体 可能有冗余空间
         */
        public byte[] content;

        /**
         * 逻辑数据长度
         */
        public int length;

        /**
         * 构造函数 初始化数据逻辑长度指针 & 内部byte[]大小
         */
        public XBuffer() {
            content = new byte[Container.X_BUFFER_INITIAL_SIZE];
            length = 0;
        }

        /**
         * 使内部byte[]无冗余空间
         * 此时内部成员length == content.length
         */
        public void trim() {
            trim(0, length);
        }

        /**
         * 以offset作为起始点裁剪内部byte[]
         *
         * @param offset 起始偏移量
         * @param length 裁剪长度
         */
        public void trim(Integer offset, Integer length) {
            Assert.check(content.length > offset, "content length must longer than offset value");

            byte[] desc = new byte[length];
            System.arraycopy(content, offset, desc, 0, length);
            content = desc;
            this.length = length;
        }


        /**
         * 在原有的数据基础上缓存byte[]
         * 内部byte[]不够时 -> 扩容为2倍
         *
         * @param src 缓存的byte[]
         */
        public void cache(byte[] src) {
            int remainBytes = src.length;
            while (length + remainBytes > content.length) {
                expend2Double();
            }

            System.arraycopy(src, 0, content, length, remainBytes);
            length += remainBytes;
        }

        /**
         * @see awesome.yk.all.in.one.SSNIOServer.XBuffer#cache(byte[])
         * 重载实现
         */
        public void cache(ByteBuffer src) {
            int remainBytes = src.remaining();
            while (length + remainBytes > content.length) {
                expend2Double();
            }

            src.get(content, length, remainBytes);
            length += remainBytes;
        }


        /**
         * 逻辑数据长度 & 内部byte[] -> GC ROOT 不再指向原来维护的内部byte[]
         */
        public void reset() {
            content = new byte[Container.X_BUFFER_INITIAL_SIZE];
            length = 0;
        }

        /**
         * 扩展内部的byte[]为2倍
         */
        private void expend2Double() {
            byte[] desc;
            if (content.length < Container.X_BUFFER_INITIAL_SIZE) {
                desc = new byte[Container.X_BUFFER_INITIAL_SIZE];
            } else {
                desc = new byte[content.length * 2];
            }

            System.arraycopy(content, 0, desc, 0, length);
            content = desc;
        }

    }


    /**
     * 关联 -> 一个维护NIO连接的 SocketChannel
     * 通用的 跨协议的Writer
     * 响应用的业务数据包对应一个弱类型XBuffer对象
     */
    public class DefaultXWriter {

        /**
         * 在途用于响应的业务数据包队列
         */
        private Queue<XBuffer> respQueue;

        /**
         * 当前在途用于响应的业务数据包
         */
        private XBuffer inFlyRespBuffer;

        /**
         * 记录当前在途用于响应的业务数据包
         * 在累计的IO 写事件中
         * 写了多少数据的偏移量指针
         */
        private int processingRespOffset;

        public DefaultXWriter() {
            respQueue = new ArrayBlockingQueue<XBuffer>(Container.QUEUE_CAPACITY);
            processingRespOffset = 0;
        }


        /**
         * NIO 写带来不确定性:
         * 1. 写了完整一个业务数据包 -> Queue中取下一个业务数据包做为下一个在途响应
         * 2. 写了部分业务数据包 -> 记录位置...下次对应写IO事件时候Resume
         *
         * @param mediator 用于向NIO SocketChannel写数据的过渡缓存
         * @param desc     维护客户端连接
         *                 <p>
         *                 简化设计 -> 响应用的自定义协议下的业务数据包统一用{@code XBuffer}表示
         *                 对应连接下 -> 一次IO 写事件触发一次该函数
         *                 为了降低复杂度 -> 但本次函数感知自己写完一个完整的 用于响应的业务数据包时(processingRespOffset == inFlyRespBuffer.length) -> 结束本次函数
         *                 哪怕在本次IO写事件下 "还允许写更多的数据"
         */
        public void write(ByteBuffer mediator, SocketChannel desc) throws IOException {
            if (inFlyRespBuffer == null) return;

            mediator.put(inFlyRespBuffer.content
                    , processingRespOffset
                    , inFlyRespBuffer.length - processingRespOffset);
            mediator.flip();

            int bytesWritten = desc.write(mediator);
            processingRespOffset += bytesWritten;

            while (/**上次写操作是否写了数据**/bytesWritten > 0
                    && /**是否写了一个完整业务数据包**/mediator.hasRemaining()) {
                bytesWritten = desc.write(mediator);
                processingRespOffset += bytesWritten;
            }

            if (/**写了一个完整业务数据包**/processingRespOffset == inFlyRespBuffer.length) {
                inFlyRespBuffer = respQueue.poll();
                processingRespOffset = 0;//重置偏移量
            }
            mediator.clear();
        }

        /**
         * @param xBuffer 一个完整的响应业务数据包对应的缓存
         *                {@code DefaultXWriter}维护对应SocketChannel下的所有在途 用于响应的业务数据包
         */
        public void enqueue(XBuffer xBuffer) {
            if (inFlyRespBuffer == null) {
                inFlyRespBuffer = xBuffer;
            } else {
                respQueue.offer(xBuffer);
            }
        }

        /**
         * @return 是否有在途 用于响应的业务数据包
         */
        public boolean isEmpty() {
            return inFlyRespBuffer == null;
        }
    }


}
