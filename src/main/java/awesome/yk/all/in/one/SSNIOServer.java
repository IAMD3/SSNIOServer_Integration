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

/**
 * Description: master T
 * {@code SSNIOServer}实现了一个超轻量级-嵌入式-支持自定义协议-支持HTTP1.0 的NIO 服务器
 * 适用于:
 *      *资源有限的设备(e.g 树莓派) 高效运行
 *      *服务器启动速度极致的场景
 *      *极速的请求&响应测试
 *      *传输层之上的自定义协议性能测试
 *      *作为一个信息管理系统的web载体
 *      *静态资源的提供者
 *      *JAVA NIO学习
 *
 * 参考Doug Lea <Scalable IO in Java> 中的Reactor模式
 * @link http://gee.cs.oswego.edu/dl/cpjslides/nio.pdf
 *
 * 这款WEB原生支持HTTP1.0协议并适应多数场景
 * 单线程模型(线程未必越多越好) -> 后续考虑推出多线程版本(更高效利用多核处理器&IO资源) -> Reactor线程 & Worker线程的拆分...扩展(未来)
 *
 * SSNIOServer 的完整功能由一个Source文件(此文件)展示 -> 尽可能地降低使用该嵌入式Server的难度(只需要简单的引入该文件就能享受)
 * 为了进一步便于使用,SSNIO没有使用任何第三方依赖并且集成了HTTP 1.0 的解析实现(往往协议的decode/encode需要使用者自己实现)
 * 使用者可以通过实现XParser来实现自定义协议的decode逻辑(参考DefaultHttpXParser)
 *
 * 此Source文件详细描述了每个类与方法的实现与设计思想 在设计上尽可能的满足"可用" "可读" "轻量" "简洁"  四个点
 * 个人非常推荐NIO的初学者使用与学习 如果有任何问题与建议 -> 欢迎随时联系
 *
 * @author Yukai
 **/
public class SSNIOServer {






}
