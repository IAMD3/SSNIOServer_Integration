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


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * 双线程模型(Acceptor&Reactor...线程未必越多越好) -> 后续考虑推出多线程版本(更高效利用多核处理器&IO资源) -> Reactor线程 & Worker线程的拆分...扩展(未来)
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
 *
 * <p>
 * 如果你最快地了解如何使用该容器
 * 启动{@link SSNIOServer#main(java.lang.String[])} 后
 * 浏览器打开localhost:8080体验一下在HTTP协议下的效果吧
 **/
public class SSNIOServer {


    /**
     * 用户自定义协议 识别&解析 扩展点
     **/
    public CodeCFactory codeCFactory;
    /**
     * 用户自定义业务实现的扩展点
     **/
    public XHandler xHandler;

    public void start() throws IOException {

        XAcceptor xAcceptor = new XAcceptor();
        xAcceptor.codeCFactory = codeCFactory;

        XReactor xReactor = new XReactor();
        xReactor.handler = xHandler;

        new Thread(xAcceptor).start();
        new Thread(xReactor).start();
        System.err.println("welcome to SSNIO server");
    }

    public static void main(String[] args) throws IOException {
        SSNIOServer server = new SSNIOServer();
        server.codeCFactory =  new HttpCodeCFactory();

        String httpResponse = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: 51\r\n" +
                "Content-Type: text/html\r\n" +
                "\r\n" +
                "<html><body>Hello FROM SSNIOServer XD</body></html>";
        byte[] httpResp_bytes = httpResponse.getBytes("UTF-8");

        XHandler handler = requestInXBuffer ->{

            HttpRequest httpRequest = (HttpRequest) requestInXBuffer;
            System.err.println("HEADER:"+httpRequest.getHeaderStr());
            System.err.println("BODY:"+httpRequest.getBodyStr());

            XBuffer xBuffer = new XBuffer();
            xBuffer.xSocketId = requestInXBuffer.xSocketId;
            xBuffer.cache(httpResp_bytes);
            return xBuffer;
        };

        server.xHandler = handler;

        server.start();
    }


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
        default XHandler  next() {return null;};
    }

    /**
     * 用户识别自定义协议下的业务数据包扩展点
     * 解决粘包/拆包问题
     * 实现可参考 {@code DefaultHttpXParser}
     */
    public interface XParser {

        /**
         * @param src "不确定"长度的 经过N(N>1 整数)次NIO read后累加的缓存
         *            <p>
         *            函数对应的功能:
         *            1 识别缓存(XBuffer)数据是否足够成为一个完整特定协议下的业务数据包(e.g HTTP下的一个完整报文)
         *            2 使用缓存(XBuffer)中的数据反序列化业务数据包
         *            3 释放缓存(XBuffer)中被成功用于反序列化的数据,保留未被用于反序列化的数据
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
         * 端口
         */
        public static final int PORT = 8080;

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
         * NIO 事件触发的对应函数之间交互用队列
         * 联系:
         * 1 XAcceptor监听IO Accept事件 生成的SocketChannel的封装对象XSocket 放入该队列
         * 2 XReactor在一次循环中取出全部XSocket对象中SocketChannel -> 向Selector注册Read事件的监听
         */
        public static Queue<XSocket> INBOUND_QUEUE = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

        /**
         * 对应INBOUND_QUEUE
         * 联系:
         * 1 承载由XHandler生成 响应对应的业务数据包载体XBuffer
         * 2 XReactor在一次循环中取出全部XBuffer -> A:更新写事件相关Selector的监听信息 B:往对应XSocket中DefaultXWriter塞入XBuffer
         */
        public static Queue<XBuffer> OUTBOUND_QUEUE = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

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
     * XSocket如同一个桥梁->维护了读写相关组件/缓存的一对一对应关系
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
            this.xWriter = new DefaultXWriter();
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
         *                 尝试重复读取 -> 读到本次IO 读事件下无法读取更多数据为止
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
     * 阻塞型 IO Accept事件监听器
     * 职责:
     * 监听端口 -> 生成SocketChannel封装对象XSocket -> 推送队列
     * 由线程单独启动(为了不阻塞其他非阻塞型操作)
     * 该对象全局唯一且不会随着连接/请求的增加而改变 -> 从Reactor对象中抽离且由单独线程维护便于更好的理解
     */
    public class XAcceptor implements Runnable {

        /**
         * 阻塞式 服务端channel
         * 阻塞状态...直到连接建立
         */
        private ServerSocketChannel ssc;

        /**
         * 用于获取 自定义协议下的业务数据包解析器的简单工厂
         */
        public CodeCFactory codeCFactory;

        public XAcceptor() throws IOException {
            this.ssc = ServerSocketChannel.open();
            ssc.bind(new InetSocketAddress(Container.PORT));
        }

        @Override
        public void run() {

            while (true) {
                try {
                    SocketChannel sc = ssc.accept();
                    //服务端监听端口对应的ServerSocketChannel -> 阻塞式
                    //客户端连接对应的SocketChannel -> 非阻塞式
                    sc.configureBlocking(false);
                    XSocket xSocket = new XSocket(sc);
                    xSocket.xParser = codeCFactory.createXReader();

                    Container.INBOUND_QUEUE.offer(xSocket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /**
     * 参考Doug Lea Reactor Pattern
     * Reactor对象
     * SSNIOServer 中最核心模块
     * <p>
     * 维护:
     * A 执行业务模型序列化 & 反序列化 & 处理的XHandler
     * B 监听IO 读 & 写事件的Selector组
     * C IO Accept 事件产生的XSocket容器connectedSocketsMap
     * D 记录 待被写入客户端的在途业务数据包对应的XSocket容器activeWritingSocketsMap
     * E 记录 不再需要监听IO写事件的SocketChannel 的封装对象XSocket容器inactiveWritingSocketsMap
     * <p>
     * 由单独线程启动 执行"无限次"的循环:
     * 1 获取所有IO Accept事件产生的SocketChannel -> 向Selector注册读事件的监听
     * 2 向读事件对应的Selector咨询 产生"IO读事件"对应的SocketChannel, 找到封装对象XSocket-> 执行读取后 委派XParser进行缓存 尝试解析
     * 3 在上一步成功获取"完整"业务数据包的场景下 执行XHandler 进行 : A 业务数据包载体XBuffer -> 业务模型转换 B业务模型处理 C 业务模型 -> 响应对应的业务数据包载体XBuffer转换
     * 4 在XHandler产生XBuffer场景下 通过对应xSocketId找到SocketChannel封装对象XSocket后: A 推送XBuffer到对应的DefaultXWriter B 更新XSocket容器组 activeWritingSocketsMap & inactiveWritingSocketsMap
     * 5 通过XSocket容器组 向写事件对应的Selector更新注册信息(可能再次注册上个循环中注册的成员)
     * 6 向写事件对应的Selector咨询 产生"IO写事件"对应的SocketChannel, 找到封装对象XSocket-> 委派DefaultXWriter进行写操作
     * 7 向DefaultXWriter咨询是否还有待写入的业务数据包,更新inactiveWritingSocketsMap信息
     * 8 下一个循环....
     */
    public class XReactor implements Runnable {
        public XHandler handler;

        private Map<String, XSocket> connectedSocketsMap;
        private Map<String, XSocket> activeWritingSocketsMap;
        private Map<String, XSocket> inactiveWritingSocketsMap;

        private Selector readSelector;
        private Selector writeSelector;

        public XReactor() throws IOException {
            this.connectedSocketsMap = new HashMap<>();
            this.activeWritingSocketsMap = new HashMap<>();
            inactiveWritingSocketsMap = new HashMap<>();
            this.readSelector = Selector.open();
            this.writeSelector = Selector.open();
        }

        @Override
        public void run() {

            while (true) {
                try {
                    /*****************读相关*********************************/
                    registerAllAcceptedSockets();//步骤1
                    readReadySockets();//步骤2 & 3 & 4A
                    /****************写相关*********************************/
                    refreshWritingSocketsInfo(); //步骤4B
                    refreshRegistrationOfWringSockets();//步骤5
                    writeToReadyChannel();//步骤 6 & 7
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


        /**************************************私有函数*********************************************/
        private void registerAllAcceptedSockets() throws IOException {
            XSocket socket = Container.INBOUND_QUEUE.poll();

            while (socket != null) {
                SelectionKey sk = socket.socketChannel
                        .register(readSelector, SelectionKey.OP_READ);

                sk.attach(socket);
                this.connectedSocketsMap.put(socket.xSocketId, socket);
                socket = Container.INBOUND_QUEUE.poll();
            }
        }

        private void readReadySockets() throws IOException {
            int selectedCount = readSelector.selectNow();

            if (selectedCount > 0) {
                Set<SelectionKey> selectionKeys = readSelector.selectedKeys();
                Iterator<SelectionKey> it = selectionKeys.iterator();

                while (it.hasNext()) {
                    SelectionKey nextSelectionKey = it.next();
                    XSocket xSocket = (XSocket) nextSelectionKey.attachment();

                    xSocket.read();
                    List<XBuffer> completeMsgBufferBlocks = xSocket.xParser.getOutputs();

                    if (/**本次IO读事件下解析出完整的业务数据包**/completeMsgBufferBlocks.size() > 0) {
                        if (handler != null) {
                            /**执行XHandler 进行 :
                             *  A 业务数据包载体XBuffer -> 业务模型转换
                             *  B 业务模型处理
                             *  C 业务模型 -> 响应对应的业务数据包载体XBuffer转换**/
                            completeMsgBufferBlocks
                                    .stream()
                                    .map(handler::handle)
                                    .forEach(Container.OUTBOUND_QUEUE::offer);
                            completeMsgBufferBlocks.clear();
                        }
                    }
                    it.remove();
                }
                selectionKeys.clear();
            }
        }

        private void refreshWritingSocketsInfo() {
            XBuffer completeMsgBufferBlock = Container.OUTBOUND_QUEUE.poll();

            while (completeMsgBufferBlock != null) {
                String xSocketId = completeMsgBufferBlock.xSocketId;
                XSocket associatedSocket = connectedSocketsMap.get(xSocketId);

                associatedSocket.xWriter
                        .enqueue(completeMsgBufferBlock);
                activeWritingSocketsMap.put(xSocketId, associatedSocket);
                inactiveWritingSocketsMap.remove(xSocketId);

                completeMsgBufferBlock = Container.OUTBOUND_QUEUE.poll();
            }
        }

        private void refreshRegistrationOfWringSockets() throws IOException {
            Collection<XSocket> activeSockets = activeWritingSocketsMap.values();
            /**可能会重复注册上个循环中已注册的SocketChannel**/
            for (XSocket socket : activeSockets) {
                SelectionKey sk = socket.socketChannel.
                        register(writeSelector, SelectionKey.OP_WRITE);
                sk.attach(socket);
            }
            activeWritingSocketsMap.clear();
            Collection<XSocket> inactiveSockets = inactiveWritingSocketsMap.values();
            for (XSocket socket : inactiveSockets) {
                SelectionKey sk = socket.socketChannel.keyFor(writeSelector);
                sk.cancel();
            }
            inactiveWritingSocketsMap.clear();
        }

        private void writeToReadyChannel() throws IOException {
            int selectedCount = writeSelector.selectNow();
            if (selectedCount > 0) {
                Set<SelectionKey> selectionKeys = writeSelector.selectedKeys();
                Iterator<SelectionKey> it = selectionKeys.iterator();
                while (it.hasNext()) {
                    SelectionKey selectionKey = it.next();
                    XSocket writableXSocket = (XSocket) selectionKey.attachment();

                    writableXSocket.write();
                    if (writableXSocket.xWriter.isEmpty()) {
                        inactiveWritingSocketsMap.put(writableXSocket.xSocketId, writableXSocket);
                    }
                    it.remove();
                }
                selectionKeys.clear();
            }
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
    public static class XBuffer {
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

    /********************************作为扩展点加入的HTTP相关组件  如果使用自定义协议可删除*******************************************/

    /**
     * 协议HTTP下请求对应的模型
     * 提供简单的:
     * URI参数获取
     * 报文Header部分字符串获取
     * 报文Body部分字符串获取
     * 大道至简 通讯皆为字节流 -> 何必转来转去徒增烦恼
     **/
    public static class HttpRequest extends XBuffer {
        /**
         * 请求头在XBuffer.content中偏移量
         */
        public int headersOffset;
        /**
         * 请求实体在XBuffer.content中偏移量
         */
        public int bodyOffset;
        /**
         * URI参数
         */
        public Map<String, String> uriParamsMap;

        /**
         * 获取URI参数
         */
        public Map<String, String> tryGetUriParams() {
            try {
                if (uriParamsMap != null) return uriParamsMap;
                uriParamsMap = new HashMap<>();
                String requestLine = tryGetStr(0, headersOffset, "utf-8");
                requestLine = URLDecoder.decode(requestLine);

                String[] pieces = requestLine.split("\\?");

                if (pieces.length != 2) return new HashMap<>();

                String paramSetStr = pieces[1];
                String paramReg = "\\w+=\\w+";
                Pattern pattern = Pattern.compile(paramReg);
                Matcher matcher = pattern.matcher(paramSetStr);

                while (matcher.find()) {
                    String param_pair_str = matcher.group();
                    String[] key_value_arr = param_pair_str.split("=");
                    uriParamsMap.put(key_value_arr[0], key_value_arr[1]);
                }
                return uriParamsMap;
            } catch (Exception e) {
                System.err.println("URI parameters parsing exception:" + e);
                return new HashMap<>();
            }

        }

        /**
         * 获取首部段字符串
         */
        public String getHeaderStr() {
            int headerLength;
            if (bodyOffset == -1) {
                headerLength = super.length - headersOffset;
            } else {
                headerLength = bodyOffset - headersOffset;
            }

            return tryGetStr(headersOffset, headerLength, "UTF-8");
        }

        /**
         * 获取实体字符串
         */
        public String getBodyStr() {
            if (bodyOffset == -1) return "";

            int bodyLength = super.length - bodyOffset;

            return tryGetStr(bodyOffset, bodyLength, "UTF-8");
        }

        /**
         * 获取字符串
         */
        private String tryGetStr(int offset, int length, String charset) {
            if (length <= 0) return "";
            byte[] bytes_buffer = new byte[length];
            System.arraycopy(super.content, offset, bytes_buffer, 0, length);

            String rst;
            try {
                rst = new String(bytes_buffer, charset);
            } catch (UnsupportedEncodingException e) {
                rst = "";
            }
            return rst;
        }
    }


    public static class DefaultHttpXParser implements XParser {
        private List<XBuffer> requests = new ArrayList<>();
        private boolean endOfStreamReached =false;
        @Override
        public void parse(XBuffer src) throws IOException {
            // XBuffer  -> contains the bytes read from NIO read
            HttpRequest request = HttpUtil.tryToParseHttpRequest(src);
            while (request != null) {
                requests.add(request);
                request = HttpUtil.tryToParseHttpRequest(src);
            }
        }
        @Override
        public List<XBuffer> getOutputs() {
            if (requests.size() == 0) return new ArrayList<>();
            return requests;
        }
    }

    public static class HttpCodeCFactory implements CodeCFactory {
        @Override
        public XParser createXReader() {
            return new DefaultHttpXParser();
        }
    }

    /**
     * 取第一行 -> 作为请求行
     * 不断取后面行作为header行,并且判断有没有content-length
     * 取到连续两次\r\n(header与body之间有两行) 作为body
     **/
    public static class HttpUtil {
        /**
         * must contained from a complete http request
         */
        private static final byte[] CONTENT_LENGTH = new byte[]{'C', 'o', 'n', 't', 'e', 'n', 't', '-', 'L', 'e', 'n', 'g', 't', 'h'};

        public static HttpRequest tryToParseHttpRequest(XBuffer readerBuffer) throws IOException {
            if (readerBuffer.length == 0) {
                return null;
            }
            readerBuffer.trim(0, readerBuffer.length);
            byte[] content = readerBuffer.content;

            int request_line_end_index = findNextLine(0, content.length, content);
            if (request_line_end_index == -1) return null;

            int header_start_index = request_line_end_index + 1;
            int header_end_index = findNextLine(header_start_index, content.length, content);
            int content_length = 0;

            while (/**not qualified**/header_end_index != -1 && /**reach body**/header_end_index != header_start_index + 1) {
                if (matches(content, header_start_index, CONTENT_LENGTH)) {
                    content_length = findContentLength
                            (content, header_start_index, header_end_index);
                }
                header_start_index = header_end_index + 1;
                header_end_index = findNextLine(header_start_index, content.length, content);
            }

            if (/**header not complete**/header_end_index == -1) return null;
            if (content_length == 0) {
                //return a request without body
                HttpRequest request = new HttpRequest();
                request.xSocketId = readerBuffer.xSocketId;
                request.content = content;
                request.headersOffset = request_line_end_index + 1;
                request.bodyOffset = -1;
                request.length =content.length;
                //clear all data of readerBuffer;
                readerBuffer.reset();
                return request;
            }
            //try to parse body
            int body_start_index = header_end_index + 1;
            int body_end_index = body_start_index + content_length;

            if (body_end_index == content.length) {
                // rare but perfect condition
                HttpRequest request = new HttpRequest();
                request.xSocketId = readerBuffer.xSocketId;
                request.content = content;
                request.headersOffset = request_line_end_index + 1;
                request.bodyOffset = body_start_index;
                request.length = content.length;

                //clear all data of readerBuffer;
                readerBuffer.reset();
                return request;
            } else if (body_end_index < content.length) {
                //拆包
                HttpRequest request = new HttpRequest();
                byte[] request_content = new byte[body_end_index];
                System.arraycopy(content, 0, request_content, 0, body_end_index);

                request.xSocketId = readerBuffer.xSocketId;
                request.content = content;
                request.headersOffset = request_line_end_index + 1;
                request.bodyOffset = body_start_index;
                request.length = content.length;
                // remain the rest part of buffer
                int offset = body_end_index + 1;
                readerBuffer.trim(offset, readerBuffer.length - offset);
                return request;
            }
            return null;
        }

        private static int findContentLength(byte[] src, int startIndex, int endIndex) throws UnsupportedEncodingException {
            int indexOfColon = findNext(src, startIndex, endIndex, (byte) ':');
            //skip spaces after colon
            int index = indexOfColon + 1;
            while (src[index] == ' ') {
                index++;
            }
            int valueStartIndex = index;
            int valueEndIndex = index;
            boolean endOfValueFound = false;

            while (index < endIndex && !endOfValueFound) {
                switch (src[index]) {
                    case '0':
                        ;
                    case '1':
                        ;
                    case '2':
                        ;
                    case '3':
                        ;
                    case '4':
                        ;
                    case '5':
                        ;
                    case '6':
                        ;
                    case '7':
                        ;
                    case '8':
                        ;
                    case '9': {
                        index++;
                        break;
                    }
                    default: {
                        endOfValueFound = true;
                        valueEndIndex = index;
                    }
                }
            }
            return Integer.parseInt(new String(src, valueStartIndex, valueEndIndex - valueStartIndex, "UTF-8"));
        }
        private static int findNext(byte[] src, int startIndex, int endIndex, byte value) {
            for (int index = startIndex; index < endIndex; index++) {
                if (src[index] == value) return index;
            }
            return -1;
        }
        private static boolean matches(byte[] src, int offset, byte[] value) {
            for (int i = offset, n = 0; n < value.length; i++, n++) {
                if (src[i] != value[n]) return false;
            }
            return true;
        }
        private static int findNextLine(int startIndex, int endIndex, byte[] src) {
            for (int index = startIndex; index < endIndex; index++) {
                if (src[index] == '\n') {
                    if (src[index - 1] == '\r') {
                        return index;
                    }
                }
                ;
            }
            return -1;
        }
    }
}
