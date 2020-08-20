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
 * 版本NO.00000000000000001(Dev测试版)
 *
 */

import com.sun.tools.javac.util.Assert;

/**
 * Description: master T
 * {@code SSNIOServer}实现了一个超轻量级-嵌入式-支持自定义协议-支持HTTP1.0 的NIO 服务器
 * 适用于:
 * *资源有限的设备(e.g 树莓派) 高效运行
 * *服务器启动速度极致的场景
 * *极速的请求&响应测试
 * *传输层之上的自定义协议性能测试
 * *作为一个信息管理系统的web载体
 * *静态资源的提供者
 * *JAVA NIO学习
 * <p>
 * 参考Doug Lea <Scalable IO in Java> 中的Reactor模式
 *
 * @author Yukai
 * @link http://gee.cs.oswego.edu/dl/cpjslides/nio.pdf
 * <p>
 * 这款WEB原生支持HTTP1.0协议并适应多数场景
 * 单线程模型(线程未必越多越好) -> 后续考虑推出多线程版本(更高效利用多核处理器&IO资源) -> Reactor线程 & Worker线程的拆分...扩展(未来)
 * <p>
 * SSNIOServer 的完整功能由一个Source文件(此文件)展示 -> 尽可能地降低使用该嵌入式Server的难度(只需要简单的引入该文件就能享受)
 * 为了进一步便于使用,SSNIO没有使用任何第三方依赖并且集成了HTTP 1.0 的解析实现(往往协议的decode/encode需要使用者自己实现)
 * 使用者可以通过实现XParser来实现自定义协议的decode逻辑(参考DefaultHttpXParser)
 * <p>
 * 文档代码一体:
 * 此Source文件详细描述了每个类与方法的实现与设计思想 在设计上尽可能的满足"可用" "可读" "轻量" "简洁"
 * 个人非常推荐NIO的初学者使用与学习 如果有任何问题与建议 -> 欢迎随时联系
 **/
public class SSNIOServer {


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
         * 重置指针 & 内部byte[] -> GC ROOT 不再指向原来维护的内部byte[]
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

}