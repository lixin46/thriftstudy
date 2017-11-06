package com.meituan.service.mobile.mtthrift.proxy;

import com.facebook.swift.codec.ThriftCodecManager;
import com.google.common.collect.ImmutableMap;
import com.meituan.mtrace.Tracer;
import com.meituan.service.mobile.mtthrift.annotation.ThriftMethodProcessor;
import com.meituan.service.mobile.mtthrift.annotation.metadata.ThriftMethodMetadata;
import com.meituan.service.mobile.mtthrift.annotation.metadata.ThriftServiceMetadata;
import com.meituan.service.mobile.mtthrift.auth.IAuthHandler;
import com.meituan.service.mobile.mtthrift.client.invoker.IMTThriftFilter;
import com.meituan.service.mobile.mtthrift.degrage.ServerDegradHandler;
import com.meituan.service.mobile.mtthrift.degrage.ServiceDegradeException;
import com.meituan.service.mobile.mtthrift.monitor.IServerMonitor;
import com.meituan.service.mobile.mtthrift.mtrace.LocalPointConf;
import com.meituan.service.mobile.mtthrift.mtrace.MtraceServerTBinaryProtocol;
import com.meituan.service.mobile.mtthrift.server.MTTServer;
import com.meituan.service.mobile.mtthrift.server.MTTThreadedSelectorServer;
import com.meituan.service.mobile.mtthrift.server.netty.NettyServer;
import com.meituan.service.mobile.mtthrift.util.AnnotationUtil;
import com.meituan.service.mobile.mtthrift.util.ConfigStatusUtil;
import com.meituan.service.mobile.mtthrift.util.DynamicProxyUtil;
import com.meituan.service.mobile.mtthrift.util.JdkUtil;
import com.sankuai.sgagent.thrift.model.ConfigStatus;
import lombok.Getter;
import lombok.Setter;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.*;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Constructor;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import static com.google.common.collect.Maps.newHashMap;

public class ThriftServerPublisher implements ApplicationContextAware, InitializingBean {
    private final static Logger logger = LoggerFactory.getLogger(ThriftServerPublisher.class);
    // 静态并发map,所有实例共享
    private static final Map<Integer, Class<?>> port2serviceInterface = new ConcurrentHashMap<>();
    //
    public static final Map<String, String> serviceInterfaceThriftTypeMap = new ConcurrentHashMap<>();

    @Setter
    @Getter
    private static boolean serverCatReport = true;
    private ApplicationContext applicationContext;
    private int minWorkerThreads = 10;
    private int maxWorkerThreads = 256;
    private int workQueueSize = 0;
    //
    private int selectorThreads = 4;
    private int port;
    private IServerMonitor serverMonitor;
    private Class<?> serviceInterface;
    private String serviceSimpleName;
    private Object serviceImpl;
    private boolean daemon = true;
    // rpc拦截过滤器
    private IMTThriftFilter reuqestFilter;
    //请求最大字节数
    private long maxRequestMessageBytes;

    /**
     * MTTServer对TServer进行了封装,内部持有TServer对象,
     *
     */
    private MTTServer _server;
    private boolean _interruptPublish = false;
    private String zkServers;// zookeeper地址
    private String zkPath;// 方式1：zk管理的动态集群
    @Setter
    @Getter
    private String clusterManager = "OCTO";
    @Setter
    @Getter
    private String strAgentUrl;
    // ???
    private int slowStartSeconds = 180;

    @Setter
    @Getter
    private String appKey;
    private boolean serializeNullStringAsBlank = false;
    private int shutdownWaitTime = 6;
    private boolean printLog = false;
    private boolean annotatedThrift = false;
    private ServerDegradHandler serverDegradHandler = null;
    @Setter
    @Getter
    private ConfigStatus configStatus = ConfigStatusUtil.newDefaultConfigStatus();
    private String serverType = ServerType.Netty.name();
    //
    private Map<Class<?>, ThriftServiceBean> serviceProcessorMap = new HashMap<>();
    private int maxServerConn = -1;
    private int limitCount = -1;
    private int limitSecondsTime = -1;
    private IAuthHandler authHandler;

    // ApplicationContextAware接口注入
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }



    public ServerDegradHandler getServerDegradHandler() {
        return serverDegradHandler;
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        // 检查配置状态,检查什么???
        ConfigStatusUtil.checkConfigStatus(this.configStatus);

        // 端口到服务的映射中,包含了当前端口
        if (port2serviceInterface.containsKey(port)) {
            // 中断发布
            _interruptPublish = true;
            // 如果服务类相同,则忽略
            if (port2serviceInterface.get(port).equals(serviceInterface)) {
                logger.error("ignore publish " + serviceImpl+",you have published " + serviceInterface + " on " + port);
            }
            // 服务类不同,则报错,因为多个服务绑定到同一个端口了
            else {
                throw new IllegalArgumentException("you have published " + port2serviceInterface.get(port) + " on " + port + ", can't republish " + serviceInterface + " on "
                        + port);
            }
            return;
        }
        // 端口映射中,不包含当前端口
        else if (serviceInterface != null) {
            // 添加映射
            port2serviceInterface.put(port, serviceInterface);
        }

        // 服务器类型为netty
        if (serverType.equalsIgnoreCase(ServerType.Netty.name())) {
            // MTTServer
            _server = new NettyServer(port);
        }
        // 服务器类型为thrift
        else {
            // MTTServer
            _server = new MTTThreadedSelectorServer(port);
        }

        // 选择器线程数???
        _server.setSelectorThreads(selectorThreads);
        // 最小工作线程数
        _server.setWorkerThreads(minWorkerThreads);
        // 最大工作线程数
        _server.setMaxWorkerThreads(maxWorkerThreads);
        // 工作队列长度
        _server.setWorkQueueSize(workQueueSize);
        // ???
        _server.setAcceptQueueSizePerThread(50);
        // 请求过滤器???
        _server.setReuqestFilter(reuqestFilter);
        // 最大读缓存???
        _server.setMaxReadBufferBytes(maxRequestMessageBytes);
        // ???
        _server.setStrAgentUrl(strAgentUrl);
        // 集群管理器???
        _server.setClusterManager(clusterManager);
        // ???
        _server.setSlowStartSeconds(getSlowStartSeconds());
        // 服务接口,即编译idl文件生成的服务类
        _server.setServiceInterface(serviceInterface);
        // 服务实现
        _server.setServiceImpl(serviceImpl);
        // appkey
        _server.setAppKey(getAppKey());
        // 初始化本地端点???如何???
        // 获取本地ip,创建Endpoint
        _server.initLocalEndPoint();
        // 是否序列化null值为空字符串
        _server.setSerializeNullStringAsBlank(serializeNullStringAsBlank);
        // 关闭等待时间
        _server.setShutdownWaitTime(this.shutdownWaitTime);
        // 设置配置状态???
        _server.setConfigStatus(configStatus);
        // 设置最大服务器连接???
        _server.setMaxServerConn(maxServerConn);
        // 限制数???
        _server.setLimitCount(limitCount);
        // 限制秒???
        _server.setLimitSecondsTime(limitSecondsTime);
        // 认证处理器???
        _server.setAuthHandler(authHandler);

        // 降级处理器
        serverDegradHandler = new ServerDegradHandler(strAgentUrl, appKey);
        // ???
        serverDegradHandler.getDegradeActionsByAgent(appKey);

        // 如果集群管理器不为OCTO,则报警
        if(!clusterManager.equalsIgnoreCase("OCTO"))
            logger.warn("clusterManager should changed to OCTO!");

        // 如果服务器类型为netty,且服务处理器Map存在元素
        if ((serverType.equalsIgnoreCase(ServerType.Netty.name())) && serviceProcessorMap.size() > 0) {
            // 遍历服务处理器
            for (Map.Entry<Class<?>, ThriftServiceBean> entry : serviceProcessorMap.entrySet()) {
                //
                Class<?> serviceInterface = entry.getKey();
                //
                ThriftServiceBean serviceBean = entry.getValue();
                // 获取服务实现
                Object serviceImpl = serviceBean.getServiceImpl();
                // 不是jdk6,且服务接口类存在ThriftService注解(该注解属于facebook快照包)
                if (!JdkUtil.isJdk6() && AnnotationUtil.detectThriftAnnotation(serviceInterface)) {
                    //
                    TProcessor processor = new ThriftServiceProcessor(DynamicProxyUtil.createJdkDynamicProxy(serviceInterface, serviceImpl, serverMonitor, this));
                    // 注册处理器
                    _server.registerTProcessor(serviceInterface.getName(), processor);
                    // 添加映射
                    serviceInterfaceThriftTypeMap.put(serviceInterface.getSimpleName(), "annotation");
                }
                // jdk6,或没有ThriftService注解
                else {
                    //
                    TProcessor processor = new MtTProcessor(getProcessorConstructorIface(serviceInterface).
                            newInstance(DynamicProxyUtil.createJdkDynamicProxy(getSynIfaceInterface(serviceInterface), serviceImpl, serverMonitor, this)));
                    //
                    _server.registerTProcessor(serviceInterface.getName(), processor);
                    //
                    serviceInterfaceThriftTypeMap.put(serviceInterface.getSimpleName(), "idl");
                }

                // 服务名称
                String serviceName = serviceInterface.getName();
                // 获取执行器
                if (serviceBean.getServiceExecutor() != null) {
                    _server.registerExecutor(serviceName, serviceBean.getServiceExecutor());
                }
                //
                for (Map.Entry<String, Executor> executorEntry : serviceBean.getMethodExecutor().entrySet()) {
                    String name = serviceName + "#" + executorEntry.getKey();
                    _server.registerExecutor(name, executorEntry.getValue());
                }
            }// end for
        }
        // 服务器类型为thrift,或处理器map为空
        else {
            // 非jdk6,且服务类存在@ThriftService注解
            if (!JdkUtil.isJdk6() && AnnotationUtil.detectThriftAnnotation(serviceInterface)) {
                //
                TProcessor processor = new ThriftServiceProcessor(DynamicProxyUtil.createJdkDynamicProxy(serviceInterface, serviceImpl, serverMonitor, this));
                //
                _server.registerTProcessor(processor);
                //
                serviceInterfaceThriftTypeMap.put(serviceInterface.getSimpleName(), "annotation");
            }
            // jdk6,或不存在@ThriftService注解
            else {
                /**
                 * 从服务类中查找名称为Processor的内部类,反射获取其构造方法创建对象
                 */
                TProcessor processor = new MtTProcessor(getProcessorConstructorIface(serviceInterface).
                        newInstance(DynamicProxyUtil.createJdkDynamicProxy(getSynIfaceInterface(serviceInterface), serviceImpl, serverMonitor, this)));
                //
                _server.registerTProcessor(processor);
                //
                serviceInterfaceThriftTypeMap.put(serviceInterface.getSimpleName(), "idl");
            }
        }


        // 如果中断发布,则返回
        if (_interruptPublish) {
            return;
        }

        // 添加关闭钩子
        _server.addShutDownHook();
        // 启动服务器,daemon决定是否在新线程中启动
        _server.run(daemon);


        if (serviceProcessorMap != null && serviceProcessorMap.size() > 0) {
            logger.info("mtthrift service published: " + serviceProcessorMap.keySet().toString());
        } else {
            logger.info("mtthrift service published: " + serviceInterface);
        }
    }

    public void publish() throws Exception {
        if (_server == null) {
            afterPropertiesSet();
        }
    }

    public void destroy() {
        if (_server != null) {
            _server.shutdown();
        }
    }

    private Class<?> getSynIfaceInterface(Class<?> serviceInterface) {
        Class<?>[] classes = serviceInterface.getClasses();
        for (Class c : classes)
            if (c.isMemberClass() && c.isInterface() && c.getSimpleName().equals("Iface")) {
                return c;
            }
        throw new IllegalArgumentException("serviceInterface must contain Sub Interface of Iface");
    }

    private Class<TProcessor> getProcessorClass(Class<?> serviceInterface) {
        Class<?>[] classes = serviceInterface.getClasses();
        for (Class c : classes)
            if (c.isMemberClass() && !c.isInterface() && c.getSimpleName().equals("Processor")) {
                return c;
            }
        throw new IllegalArgumentException("serviceInterface must contain Sub Interface of Processor");
    }

    private Constructor<TProcessor> getProcessorConstructorIface(Class<?> serviceInterface) {
        try {
            return getProcessorClass(serviceInterface).getConstructor(getSynIfaceInterface(serviceInterface));
        } catch (Exception e) {
            throw new IllegalArgumentException("serviceInterface must contain Sub Class of Processor with Constructor(Iface.class):" + e.getMessage());
        }
    }


    public int getSlowStartSeconds() {
        return slowStartSeconds;
    }

    public void setSlowStartSeconds(int slowStartSeconds) {
        if (slowStartSeconds < 30) {
            slowStartSeconds = 30;
            logger.warn("slowStartSeconds < 10, changed to 10 !");
        }
        if (slowStartSeconds > 600) {
            logger.warn("slowStartSeconds > 600, changed to 600 !");
            slowStartSeconds = 600;
        }
        this.slowStartSeconds = slowStartSeconds;
    }

    public boolean isSerializeNullStringAsBlank() {
        return serializeNullStringAsBlank;
    }

    public void setSerializeNullStringAsBlank(
            boolean serializeNullStringAsBlank) {
        this.serializeNullStringAsBlank = serializeNullStringAsBlank;
    }

    public int getMaxWorkerThreads() {
        return maxWorkerThreads;
    }

    public void setMaxWorkerThreads(int maxWorkerThreads) {
        this.maxWorkerThreads = maxWorkerThreads;
    }

    public int getWorkQueueSize() {
        return workQueueSize;
    }

    public void setWorkQueueSize(int workQueueSize) {
        this.workQueueSize = workQueueSize;
    }

    public void setShutdownWaitTime(int shutdownWaitTime) {
        this.shutdownWaitTime = shutdownWaitTime;
    }

    public void setAnnotatedThrift(boolean annotatedThrift) {
        this.annotatedThrift = annotatedThrift;
    }

    public boolean isAnnotatedThrift() {
        return this.annotatedThrift;
    }

    public boolean isPrintLog() {
        return printLog;
    }

    private String getExceptionMessage(Throwable e) {
        StackTraceElement[] stacks = e.getStackTrace();
        if (stacks != null && stacks.length > 0) {
            StackTraceElement stackTraceElement = stacks[0];
            return e.getClass().getName() + (e.getMessage() == null ? "" : ":" + e.getMessage()) + "(" + stackTraceElement.getFileName() + "," + stackTraceElement.getMethodName() + "() line " + stackTraceElement.getLineNumber() + ")";
        } else {
            return e.getClass().getName() + (e.getMessage() == null ? "" : ":" + e.getMessage());
        }
    }

    @Deprecated
    public void setZkServers(String zkServers) {
        this.zkServers = zkServers;
    }

    @Deprecated
    public void setZkPath(String zkPath) {
        this.zkPath = zkPath;
    }

    public class MtTProcessor implements TProcessor {
        private TProcessor _processor;

        private MtTProcessor(TProcessor _processor) {
            this._processor = _processor;
        }

        public TProcessor get_processor() {
            return _processor;
        }

        @Override
        public boolean process(TProtocol in, TProtocol out) throws TException {
            try {
                return _processor.process(in, out);
            } catch (Throwable e) {
                if (e instanceof TTransportException || e instanceof TProtocolException || (e instanceof TBase && e instanceof TException)) {
                    throw (TException) e;
                } else {
                    if (e instanceof UndeclaredThrowableException && null != e.getCause()) {
                        e = e.getCause();
                    }
                    //捕获服务端抛出的未知异常，避免客户端释放连接
                    String eMessage = null;
                    if (in instanceof MtraceServerTBinaryProtocol && (eMessage = getExceptionMessage(e)) != null) {
                        TMessage msg = ((MtraceServerTBinaryProtocol) in).reReadMessageBegin();
                        if (msg != null) {
                            //将服务端异常描述写入响应
                            eMessage += " traceId:" + Tracer.id() + " ";
                            TApplicationException x = new TApplicationException(TApplicationException.INTERNAL_ERROR, eMessage);
                            ((MtraceServerTBinaryProtocol) out).rewriteMessageBegin(msg.name, TMessageType.EXCEPTION, msg.seqid);
                            x.write(out);
                            out.writeMessageEnd();
                            out.getTransport().flush();
                            if (!(e instanceof ServiceDegradeException))
                                logger.error(eMessage, e);
                            return false;
                        }
                    }
                    throw new TException("server exception:" + e.getMessage(), e);
                }
            }
        }
    }

    public class ThriftServiceProcessor implements TProcessor {

        private final ThriftCodecManager codecManager = new ThriftCodecManager();
        private Map<String, ThriftMethodProcessor> methods;

        public ThriftServiceProcessor(Object serviceImpl) {
            Map<String, ThriftMethodProcessor> processorMap = newHashMap();

            ThriftServiceMetadata serviceMetadata = new ThriftServiceMetadata(serviceImpl.getClass(), codecManager.getCatalog());
            for (ThriftMethodMetadata methodMetadata : serviceMetadata.getMethods().values()) {
                String methodName = methodMetadata.getName();
                ThriftMethodProcessor methodProcessor = new ThriftMethodProcessor(serviceImpl, serviceMetadata.getName(), methodMetadata, codecManager);
                if (processorMap.containsKey(methodName)) {
                    throw new IllegalArgumentException("Multiple @ThriftMethod-annotated methods named '" + methodName + "' found in the given services");
                }
                processorMap.put(methodName, methodProcessor);
            }

            methods = ImmutableMap.copyOf(processorMap);
            if (null == methods)
                logger.error("find no thrift method!");

        }

        @Override
        public boolean process(TProtocol in, TProtocol out)
                throws TException {

            TMessage message = in.readMessageBegin();
            int sequenceId = message.seqid;// lookup method
            String methodName = message.name;

            ThriftMethodProcessor method = methods.get(methodName);
            if (method == null) {
                TProtocolUtil.skip(in, TType.STRUCT);
                writeApplicationException(out, methodName, sequenceId, TApplicationException.UNKNOWN_METHOD, "Invalid method name: '" + methodName + "'", null);
                return false;
            }
            try {
                boolean result = method.process(in, out, sequenceId);
                return result;
            } catch (Throwable e) {
                if (e instanceof TTransportException || e instanceof TProtocolException || (
                        e instanceof TBase && e instanceof TException)) {
                    throw (TException) e;
                } else {
                    //捕获服务端抛出的未知异常，避免客户端释放连接{
                    if (e instanceof UndeclaredThrowableException && null != e.getCause()) {
                        e = e.getCause();
                    }
                    String eMessage = null;
                    if (in instanceof MtraceServerTBinaryProtocol
                            && (eMessage = getExceptionMessage(e)) != null) {
                        TMessage msg = ((MtraceServerTBinaryProtocol) in)
                                .reReadMessageBegin();
                        if (msg != null) {
                            //将服务端异常描述写入响应
                            eMessage += " traceId:" + Tracer.id() + " ";
                            TApplicationException x = new TApplicationException(
                                    TApplicationException.INTERNAL_ERROR,
                                    eMessage);
                            ((MtraceServerTBinaryProtocol) out)
                                    .rewriteMessageBegin(msg.name,
                                            TMessageType.EXCEPTION, msg.seqid);
                            x.write(out);
                            out.writeMessageEnd();
                            out.getTransport().flush();
                            logger.error(eMessage, e);
                            return false;
                        }
                    }
                    throw new TException("server exception:" + e.getMessage(), e);
                }
            }
        }

        public TApplicationException writeApplicationException(
                TProtocol outputProtocol,
                String methodName,
                int sequenceId,
                int errorCode,
                String errorMessage,
                Throwable cause)
                throws TException
        {
            //加入traceId
            errorMessage += " traceId:" + Tracer.id() + " ";

            // unexpected exception
            TApplicationException applicationException = new TApplicationException(errorCode, errorMessage);
            if (cause != null) {
                applicationException.initCause(cause);
            }

            logger.error(applicationException.getMessage(), errorMessage);

            // Application exceptions are sent to client, and the connection can be reused
            outputProtocol.writeMessageBegin(new TMessage(methodName, TMessageType.EXCEPTION, sequenceId));
            applicationException.write(outputProtocol);
            outputProtocol.writeMessageEnd();
            outputProtocol.getTransport().flush();

            return applicationException;
        }

    }

    /**
     * 构造方法
     */
    public ThriftServerPublisher() {
    }

    public void setPrintLog(boolean printLog) {
        this.printLog = printLog;
    }

    public void setMinWorkerThreads(int minWorkerThreads) {
        this.minWorkerThreads = minWorkerThreads;
    }

    public void setSelectorThreads(int selectorThreads) {
        this.selectorThreads = selectorThreads;
    }

    public void setPort(int port) {
        this.port = port;
        System.setProperty("app.port", String.valueOf(port));
        // 存在ThriftClientProxy的情况下，其可能先初始化LocalPointConf里的port，这里再覆盖下
        LocalPointConf.setPort(port);
    }

    public void setServerMonitor(IServerMonitor serverMonitor) {
        this.serverMonitor = serverMonitor;
    }

    public void setServiceInterface(Class<?> serviceInterface) {
        this.serviceInterface = serviceInterface;
        this.serviceSimpleName = serviceInterface.getSimpleName();
    }

    public Class<?> getServiceInterface() {
        return this.serviceInterface;
    }

    public String getServiceSimpleName() {
        return serviceSimpleName;
    }

    public void setServiceImpl(Object serviceImpl) {
        this.serviceImpl = serviceImpl;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public void setReuqestFilter(IMTThriftFilter reuqestFilter) {
        this.reuqestFilter = reuqestFilter;
    }

    public void setMaxRequestMessageBytes(long maxRequestMessageBytes) {
        this.maxRequestMessageBytes = maxRequestMessageBytes;
    }

    public Map<Class<?>, ThriftServiceBean> getServiceProcessorMap() {
        return serviceProcessorMap;
    }

    public void setServiceProcessorMap(Map<Class<?>, ThriftServiceBean> serviceProcessorMap) {
        this.serviceProcessorMap = serviceProcessorMap;
    }

    public String getServerType() {
        return serverType;
    }

    public void setServerType(String serverType) {
        this.serverType = serverType;
    }

    enum ServerType {
        Netty, Thrift
    }

    public int getMaxServerConn() {
        return maxServerConn;
    }

    public void setMaxServerConn(int maxServerConn) {
        this.maxServerConn = maxServerConn;
    }

    public int getLimitCount() {
        return limitCount;
    }

    public void setLimitCount(int limitCount) {
        this.limitCount = limitCount;
    }

    public int getLimitSecondsTime() {
        return limitSecondsTime;
    }

    public void setLimitSecondsTime(int limitSecondsTime) {
        this.limitSecondsTime = limitSecondsTime;
    }

    public IAuthHandler getAuthHandler() {
        return authHandler;
    }

    public void setAuthHandler(IAuthHandler authHandler) {
        this.authHandler = authHandler;
    }

    public static class Builder {
        //默认
        private int minWorkerThreads = 10;
        private int maxWorkerThreads = 256;
        private int selectorThreads = 4;
        private boolean daemon = true;
        private boolean annotatedThrift = false;
        private boolean printLog = false;
        private int slowStartSeconds = 180;
        private long maxRequestMessageBytes;//请求最大字节数，默认10 * 1024 * 1024
        private boolean serializeNullStringAsBlank = false;
        private int shutdownWaitTime = 3;
        private String clusterManager = "OCTO";

        //必填
        private int port;
        private String appKey;
        private Class<?> serviceInterface;
        private Object serviceImpl;

        //选填
        private String strAgentUrl;
        private IMTThriftFilter reuqestFilter;// rpc拦截过滤器

        public Builder(String appKey, String serviceInterface, Object serviceImpl, int port) {
            this.appKey = appKey;
            try {
                this.serviceInterface = Class.forName(serviceInterface);
            } catch (ClassNotFoundException e) {
                logger.error(e.getMessage(), e);
            }
            this.serviceImpl = serviceImpl;
            this.port = port;
        }

        public Builder minWorkerThreads(int minWorkerThreads) {
            this.minWorkerThreads = minWorkerThreads;
            return this;
        }

        public Builder maxWorkerThreads(int maxWorkerThreads) {
            this.maxWorkerThreads = maxWorkerThreads;
            return this;
        }

        public Builder selectorThreads(int selectorThreads) {
            this.selectorThreads = selectorThreads;
            return this;
        }

        public Builder daemon(boolean daemon) {
            this.daemon = daemon;
            return this;
        }

        public Builder annotatedThrift(boolean annotatedThrift) {
            this.annotatedThrift = annotatedThrift;
            return this;
        }

        public Builder printLog(boolean printLog) {
            this.printLog = printLog;
            return this;
        }

        public Builder slowStartSeconds(int slowStartSeconds) {
            this.slowStartSeconds = slowStartSeconds;
            return this;
        }

        public Builder maxRequestMessageBytes(int maxRequestMessageBytes) {
            this.maxRequestMessageBytes = maxRequestMessageBytes;
            return this;
        }

        public Builder serializeNullStringAsBlank(boolean serializeNullStringAsBlank) {
            this.serializeNullStringAsBlank = serializeNullStringAsBlank;
            return this;
        }

        public Builder shutdownWaitTime(int shutdownWaitTime) {
            this.shutdownWaitTime = shutdownWaitTime;
            return this;
        }

        public Builder clusterManager(String clusterManager) {
            this.clusterManager = clusterManager;
            return this;
        }

        public Builder strAgentUrl(String strAgentUrl) {
            this.strAgentUrl = strAgentUrl;
            return this;
        }

        public Builder reuqestFilter(IMTThriftFilter reuqestFilter) {
            this.reuqestFilter = reuqestFilter;
            return this;
        }

        public ThriftServerPublisher build() {
            return new ThriftServerPublisher(this);
        }
    }

    /**
     * 专门供内部Builder构建时调用
     * @param builder
     */
    private ThriftServerPublisher(Builder builder) {
        this.appKey = builder.appKey;
        this.serviceInterface = builder.serviceInterface;
        this.serviceImpl = builder.serviceImpl;
        this.port = builder.port;

        this.minWorkerThreads = builder.minWorkerThreads;
        this.maxWorkerThreads = builder.maxWorkerThreads;
        this.selectorThreads = builder.selectorThreads;
        this.daemon = builder.daemon;
        this.annotatedThrift = builder.annotatedThrift;
        this.printLog = builder.printLog;
        this.slowStartSeconds = builder.slowStartSeconds;
        this.maxRequestMessageBytes = builder.maxRequestMessageBytes;
        this.serializeNullStringAsBlank = builder.serializeNullStringAsBlank;
        this.shutdownWaitTime = builder.shutdownWaitTime;
        this.clusterManager = builder.clusterManager;

        this.strAgentUrl = builder.strAgentUrl;
        this.reuqestFilter = builder.reuqestFilter;
        try {
            publish();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }


}
