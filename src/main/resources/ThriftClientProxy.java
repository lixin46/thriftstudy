package com.meituan.service.mobile.mtthrift.proxy;

import com.facebook.swift.codec.ThriftCodecManager;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.meituan.mtrace.Endpoint;
import com.meituan.service.mobile.mtthrift.annotation.MTThriftInvocationHandler;
import com.meituan.service.mobile.mtthrift.annotation.MTThriftInvocationHandler.ThriftClientMetadata;
import com.meituan.service.mobile.mtthrift.annotation.MTThriftInvocationHandler.TypeAndName;
import com.meituan.service.mobile.mtthrift.auth.ISignHandler;
import com.meituan.service.mobile.mtthrift.client.cluster.DirectlyCluster;
import com.meituan.service.mobile.mtthrift.client.cluster.DynamicAgentCluster;
import com.meituan.service.mobile.mtthrift.client.cluster.ICluster;
import com.meituan.service.mobile.mtthrift.client.cluster.MtThrfitInvokeInfo;
import com.meituan.service.mobile.mtthrift.client.invoker.IResponseCollector;
import com.meituan.service.mobile.mtthrift.client.invoker.ITimeoutPolicy;
import com.meituan.service.mobile.mtthrift.client.invoker.LocalMockMethodInterceptor;
import com.meituan.service.mobile.mtthrift.client.invoker.MTThriftMethodInterceptor;
import com.meituan.service.mobile.mtthrift.client.model.Server;
import com.meituan.service.mobile.mtthrift.client.pool.MTThriftPoolConfig;
import com.meituan.service.mobile.mtthrift.client.route.ILoadBalancer;
import com.meituan.service.mobile.mtthrift.monitor.IClientMonitor;
import com.meituan.service.mobile.mtthrift.monitor.NullClientMonitor;
import com.meituan.service.mobile.mtthrift.mtrace.LocalPointConf;
import com.meituan.service.mobile.mtthrift.mtrace.MtraceClientTBinaryProtocol;
import com.meituan.service.mobile.mtthrift.transport.CustomizedTFramedTransport;
import com.meituan.service.mobile.mtthrift.util.AnnotationUtil;
import com.meituan.service.mobile.mtthrift.util.Consts;
import com.meituan.service.mobile.mtthrift.util.JdkUtil;
import com.meituan.service.mobile.mtthrift.util.TraceInfoUtil;
import com.sankuai.octo.protocol.TraceInfo;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.apache.thrift.async.TAsyncClient;
import org.apache.thrift.async.TAsyncClientManager;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TNonblockingTransport;
import org.apache.thrift.transport.TTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * 客户端代理
 */
public class ThriftClientProxy implements FactoryBean<Object>, ApplicationContextAware, InitializingBean {
    private final static Logger logger = LoggerFactory.getLogger(ThriftClientProxy.class);
    private static final int DEFAULT_TIMEOUT = 2000;
    private static boolean catInitialized = false;

    /**
     * 服务类,编译idl文件生成
     */
    @Setter
    @Getter
    private Class<?> serviceInterface;
    private MTThriftPoolConfig mtThriftPoolConfig;// 网络连接池配置，可不配
    // 同步还是异步，目前仅支持异步
    private boolean async = false;
    private static int cores = Runtime.getRuntime().availableProcessors();
    private int asyncSelectorThreadCount = cores * 2;
    private static List<TAsyncClientManager> asyncClientManagerList = null;
    private int timeout;
    private int connTimeout = Consts.connectTimeout;
    // zookeeper地址
    @Deprecated
    private String zkServers;
    // 方式1：zk管理的动态集群
    private String zkPath;
    // 方式2：指定的server列表
    private String serverIpPorts;
    // 若设置, 则直连到本地测试服务
    private int localServerPort = -1;
    private IClientMonitor clientMonitor;
    // 该变量已废弃，外部接口暂保留
    private boolean isImplFacebookService = false;
    @Deprecated
    private boolean isHabse = false;
    private int maxResponseMessageBytes = 16384000;
    private boolean serverDynamicWeight = false;
    private boolean enableRemoteDCServer = true;
    private String serviceName;

    // 集群
    private ICluster cluster;

    private Object serviceProxy;
    private Constructor synConstructor;
    private Constructor asynConstructor;
    private TAsyncClientManager asyncClientManager;
    private TProtocolFactory asyncProtocol;
    private String clusterManager = "OCTO";
    private String strAgentUrl;
    private boolean retryRequest = true;
    private int retryTimes = 3;
    @Setter
    @Getter
    private String appKey;
    private String remoteAppkey;
    private int remoteServerPort;
    private Endpoint localEndpoint;
    private int slowStartSeconds = 180;
    private boolean bUpdateLocalConfig = false;
    private ILoadBalancer userDefinedBalancer = null;
    private ITimeoutPolicy timeoutPolicy = null;
    private IResponseCollector responseCollector = null;
    private boolean annotatedThrift = false;
    private String mockServiceImpl;
    private LoadingCache<TypeAndName, ThriftClientMetadata> clientMetadataCache;
    private boolean gzip = false;
    private boolean snappy = false;
    private boolean chenkSum = false;
    private byte[] protocol = Consts.protocol;
    @Setter
    @Getter
    private boolean disableTimeoutStackTrace = false;
    private ISignHandler signHandler;
    private boolean filterByServiceName = false;
    private boolean remoteUniProto = false;//直连时服务端是否为统一协议


    public void setUserDefinedBalancer(Class<?> userDefinedBalancerClass) {
        try {
            userDefinedBalancer = (ILoadBalancer) (userDefinedBalancerClass.newInstance());
        } catch (InstantiationException e) {
            logger.error("InstantiationException " + e);
            userDefinedBalancer = null;
        } catch (IllegalAccessException e) {
            logger.error("IllegalAccessException " + e);
            userDefinedBalancer = null;
        }
    }

    public void setTimeoutPolicy(ITimeoutPolicy timeoutPolicy) {
        this.timeoutPolicy = timeoutPolicy;
    }

    public void setMtThriftPoolConfig(MTThriftPoolConfig mtThriftPoolConfig) {
        this.mtThriftPoolConfig = mtThriftPoolConfig;
    }

    public MTThriftPoolConfig getMtThriftPoolConfig() {
        if (mtThriftPoolConfig == null) {
            mtThriftPoolConfig = new MTThriftPoolConfig();
            mtThriftPoolConfig.setMaxActive(cores * 50);
            mtThriftPoolConfig.setMaxIdle(cores * 5);
            mtThriftPoolConfig.setMinIdle(5);
            mtThriftPoolConfig.setMaxWait(500);
            mtThriftPoolConfig.setTestOnBorrow(false);
        }
        return mtThriftPoolConfig;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getTimeout() {
        return timeout > 0 ? timeout : DEFAULT_TIMEOUT;
    }


    public int getConnTimeout() {
        return connTimeout;
    }

    public void setConnTimeout(int connTimeout) {
        this.connTimeout = connTimeout;
    }

    @Deprecated
    public void setZkServers(String zkServers) {
        this.zkServers = zkServers;
    }

    public void setGzip(boolean gzip) {
        this.gzip = gzip;
    }

    public void setSnappy(boolean snappy) {
        this.snappy = snappy;
    }

    public void setChenkSum(boolean chenkSum) {
        this.chenkSum = chenkSum;
    }

    private String getZkServers() {
        return zkServers;
    }

    @Deprecated
    public void setZkPath(String zkPath) {
        this.zkPath = zkPath;
    }

    public void setServerIpPorts(String serverIpPorts) {
        this.serverIpPorts = serverIpPorts;
    }

    protected IClientMonitor getClientMonitor() {
        if (clientMonitor == null) {
            clientMonitor = new NullClientMonitor();
        }
        return clientMonitor;
    }

    public void setClientMonitor(IClientMonitor clientMonitor) {
        this.clientMonitor = clientMonitor;
    }

    public void setImplFacebookService(boolean implFacebookService) {
        // do nothing
    }

    public void setIsImplFacebookService(boolean implFacebookService) {
        // do nothing
    }

    public void setHabse(boolean habse) {
        isHabse = habse;
        if (isHabse) {
            setIsImplFacebookService(false);
        }
    }

    public int getMaxResponseMessageBytes() {
        return maxResponseMessageBytes;
    }

    public void setMaxResponseMessageBytes(int maxResponseMessageBytes) {
        if (maxResponseMessageBytes > 1000) {
            this.maxResponseMessageBytes = maxResponseMessageBytes;
        }
    }

    public void setServerDynamicWeight(boolean serverDynamicWeight) {
        this.serverDynamicWeight = serverDynamicWeight;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        if (StringUtils.isBlank(serviceName))
            this.serviceName = serviceInterface.getName();
        return serviceName;
    }

    public ThriftClientProxy() {
    }

    private ApplicationContext applicationContext;

    /**
     * ApplicationContextAware接口注入
     * @param applicationContext
     * @throws BeansException
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * FactoryBean接口获取bean
     * @return
     * @throws Exception
     */
    @Override
    public Object getObject() throws Exception {
        return serviceProxy;
    }

    public int getServersSize() {
        if (cluster != null && cluster.getServerConnList() != null) {
            return cluster.getServerConnList().size();
        }
        return 0;
    }

    @Override
    public Class<?> getObjectType() {
        if (serviceInterface == null)
            return null;

        return getIfaceInterface();
    }

    @Override
    public boolean isSingleton() {
        return true;
    }


    public String getMockServiceImpl() {
        return mockServiceImpl;
    }

    public void setMockServiceImpl(String mockServiceImpl) {
        this.mockServiceImpl = mockServiceImpl;
    }

    @Override
    public void afterPropertiesSet() throws Exception {

        // 如果未设置本地appkey则警告
        if (null == appKey || appKey.isEmpty()) {
            logger.warn("appKey is empty(remoteAppkey is {}, serviceInterface is {}), " +
                    "may cause problem, please use the right appKey !!!", remoteAppkey, serviceInterface);
            appKey = "";
        }
        // 存在多个ThriftServerPublisher的情况下，这里暂时没法取到准确的port，使用LocalPointConf.getAppPort代替

        /**
         * 本地连接点???
         * appkey
         * host
         * port
         */
        localEndpoint = new Endpoint(appKey, LocalPointConf.getAppIp(), 0);

        // 如果异步
        if (async) {
            // 如果异步客户端管理器列表为null
            if (null == asyncClientManagerList) {
                synchronized (ThriftClientProxy.class) {
                    if (null == asyncClientManagerList) {
                        asyncClientManagerList = new ArrayList<>();
                        // 根据异步选择器线程数,创建对应数量的TAsyncClientManager异步客户端管理器
                        for (int i = 0; i < asyncSelectorThreadCount; i++) {
                            asyncClientManagerList.add(new TAsyncClientManager());
                        }
                    }
                }
            }
            // 创建异步协议
            asyncProtocol = new MtraceClientTBinaryProtocol.Factory(localEndpoint);
        }

        // 如果本地服务器端口号大于0,则代表设置了本地连接
        if (localServerPort > 0) {
            // 本地ip+端口
            serverIpPorts = LocalPointConf.getAppIp() + Consts.colon + localServerPort;
            logger.info("connect 2 local server: " + serverIpPorts);
        }

        // 非jdk6
        if (!JdkUtil.isJdk6()) {
            // 服务类是否带有@ThriftService注解
            annotatedThrift = AnnotationUtil
                    .detectThriftAnnotation(serviceInterface);

            // 编解码管理器???
            final ThriftCodecManager codecManager = checkNotNull(new ThriftCodecManager(), "codecManager is null");
            // 客户端源数据缓存???
            clientMetadataCache = CacheBuilder
                    .newBuilder()
                    .build(new CacheLoader<TypeAndName, ThriftClientMetadata>() {

                        @Override
                        public ThriftClientMetadata load(TypeAndName typeAndName)
                                throws Exception {
                            return new ThriftClientMetadata(typeAndName.getType(), typeAndName.getName(), codecManager);
                        }
                    });
        }

        // 存在模拟服务实现,不为空字符串
        if (mockServiceImpl != null && !mockServiceImpl.trim().equals("")) {
            // 本地模拟方法拦截器???
            LocalMockMethodInterceptor clientInterceptor = new LocalMockMethodInterceptor(mockServiceImpl);
            Class<?> _interface = null;

            // 如果存在@ThriftService注解
            if (annotatedThrift) {
                _interface = serviceInterface;
            }
            // 不存在
            else {
                // 获取服务类的Iface或AsyncIface
                _interface = getIfaceInterface();
            }
            // 代理工厂,指定服务接口和拦截器
            ProxyFactory pf = new ProxyFactory(_interface, clientInterceptor);
            // 获取服务代理
            serviceProxy = pf.getProxy();
            return;
        }

        // serverIpPorts非空,且集群管理器不等于direct(直连)
        if (serverIpPorts != null && serverIpPorts.trim().length() > 0 && !clusterManager.equalsIgnoreCase("DIRECT")) {
            // 集群管理器变更为直连
            clusterManager = "DIRECT";
            logger.warn("Parameter serverIpPorts(remoteAppkey is {}, serviceInterface is {}, serverIpPorts is {}) not empty, " +
                    "clusterManager is modified to DIRECT!", remoteAppkey, serviceInterface, serverIpPorts);
        }

        // octo,默认的集群管理器
        if (clusterManager.equalsIgnoreCase("OCTO")) {
            // 创建动态agent集群???
            cluster = new DynamicAgentCluster(getMtThriftPoolConfig(), getTimeout()
                    , isImplFacebookService, async, serverDynamicWeight, strAgentUrl, appKey, remoteAppkey
                    , getRemoteServerPort(), bUpdateLocalConfig, getServiceName(), this.connTimeout, filterByServiceName);


        }
        // ZK集群管理器,已废弃,会报错
        else if (clusterManager.equalsIgnoreCase("ZK") || clusterManager.equalsIgnoreCase("MIX")) {
            throw new IllegalArgumentException("mtthrift don't support zk module since 1.6.2");
        }
        // 直连
        else if (clusterManager.equalsIgnoreCase("DIRECT")) {
            slowStartSeconds = 0;

            // 直连必须指定ip端口列表
            if (serverIpPorts == null || serverIpPorts.trim().length() <= 0)
                throw new IllegalArgumentException("DIRECT clusterManager, serverIpPorts not valid");
            // 只保留字母,数字,下划线,中划线,点,冒号,其余内容当做分隔符
            String[] ipPortArr = serverIpPorts.trim().split("[^0-9a-zA-Z_\\-\\.:]+");

            //
            Set<Server> servers = new HashSet<>();
            // 遍历ip端口列表
            for (String ipPort : ipPortArr) {
                // 冒号拆分ip和port
                String[] items = ipPort.split(":");
                //
                if (items.length == 2) {
                    //
                    servers.add(new Server(items[0], Integer.parseInt(items[1]), remoteUniProto));
                }
                // 带权重的
                else if (items.length == 3) {
                    Server server = new Server(items[0], Integer.parseInt(items[1]), "", Integer.parseInt(items[2]));
                    server.setUnifiedProto(remoteUniProto);
                    servers.add(server);
                } else {
                    logger.error("ignore thrift server " + ipPort);
                    continue;
                }
            }
            // 创建直连集群
            cluster = new DirectlyCluster(servers, getMtThriftPoolConfig(), getTimeout(), isImplFacebookService, async, getServiceName(), connTimeout);

        } else {
            throw new IllegalArgumentException(" property clusterManger not valid ! ");
        }

        // 开启校验和
        if (this.chenkSum)
            this.protocol[0] = (byte) (this.protocol[0] | 0x80);

        // 开启gzip
        if (this.gzip)
            this.protocol[0] = (byte) (this.protocol[0] | 0x40);

        // 开启???
        if (this.snappy)
            this.protocol[0] = (byte) (this.protocol[0] | 0x20);

        // 客户端拦截器???
        MTThriftMethodInterceptor clientInterceptor =
                new MTThriftMethodInterceptor(this, cluster, slowStartSeconds,
                        userDefinedBalancer, responseCollector, timeoutPolicy);

        // 重试时间
        clientInterceptor.setRetryTimes(this.retryTimes);
        // 重试次数
        clientInterceptor.setRetryRequest(retryRequest);
        // 做校验或初始化连接
        clientInterceptor.afterPropertiesSet();
        //集群管理器
        clientInterceptor.setClusterManager(this.clusterManager);

        /**
         * 获取接口
         */
        Class<?> _interface;
        if (annotatedThrift) {
            _interface = serviceInterface;
            MTThriftInvocationHandler.ThriftClientMetadata clientMetadata = clientMetadataCache
                    .getUnchecked(new TypeAndName(
                            serviceInterface, serviceInterface.getName()));
            clientInterceptor.setClientMetadata(clientMetadata);
        } else {
            _interface = getIfaceInterface();
        }

        // 创建代理工厂
        ProxyFactory pf = new ProxyFactory(_interface, clientInterceptor);
        // 获取代理对象
        serviceProxy = pf.getProxy();

        //避免cat和mtrace多次初始化
        if (!catInitialized) {
            TraceInfoUtil.catInitAtClient(this);
            TraceInfoUtil.mtraceInitAtClient(appKey);
            catInitialized = true;
        }
    }

    private String getZkServersFromProperty() {
        Properties props = new Properties();
        InputStream is = null;
        try {
            String propName = Consts.ZK_PROPERTIES_FILE;
            is = this.getClass().getClassLoader().getResourceAsStream(propName);
            props.load(is);
            return props.getProperty("zookeeper.server", "");
        } catch (Exception e) {
            logger.error("Default zk config error " + Consts.ZK_PROPERTIES_FILE);
            throw new RuntimeException("ZK properties file error", e);
        } finally {
            if (is != null)
                try {
                    is.close();
                } catch (IOException e) {
                    logger.info("close exception...", e.getMessage());
                }
        }
    }

    private String getClusterName() {
        String clusterName = null;
        if (serviceName != null && serviceName.trim().length() > 0) {
            clusterName = serviceName;
        } else if (zkPath != null && zkPath.trim().length() > 0) {
            clusterName = zkPath.replaceFirst("^/", "").replaceAll("\\|/", "_");
        } else {
            clusterName = serviceInterface.getName();
        }
        return clusterName;
    }

    public void destroy() {
        if (cluster != null) {
            cluster.destroy();
        }
    }

    public void noticeInvoke(String methodName, String serverIpPort, long takesMills) {
        getClientMonitor().noticeInvoke(getServiceSimpleName(), methodName, serverIpPort, takesMills);
    }

    public void noticeGetConnect(long takesMills) {
        getClientMonitor().noticeGetConnect(getServiceSimpleName(), takesMills);
    }

    @Deprecated
    public void noticeException(String methodName, String serverIpPort, String exceptionMessage, Throwable e) {
    }

    public boolean isAsync() {
        return async;
    }

    private Constructor<?> getClientConstructorWithTProtocol() {
        if (async) {
            if (asynConstructor == null) {
                try {
                    asynConstructor = getAsyncClientClass().getConstructor(TProtocolFactory.class, TAsyncClientManager.class, TNonblockingTransport.class);
                } catch (Exception e) {
                    throw new IllegalArgumentException("serviceInterface must contain Sub Class of AsyncClient with Constructor(TProtocol.class)");
                }
            }
            return asynConstructor;
        } else {
            if (synConstructor == null) {
                try {
                    synConstructor = getSynClientClass().getConstructor(TProtocol.class);
                } catch (Exception e) {
                    throw new IllegalArgumentException("serviceInterface must contain Sub Class of Client with Constructor(TProtocol.class)");
                }
            }
            return synConstructor;
        }
    }

    public Object getClientInstance(TTransport socket, MtThrfitInvokeInfo mtThrfitInvokeInfo) throws IllegalAccessException, InvocationTargetException,
            InstantiationException {
        getClientConstructorWithTProtocol();

        if (async) {
            Object o = asynConstructor.newInstance(asyncProtocol, asyncClientManagerList.get(socket.hashCode() % asyncSelectorThreadCount), socket);
            ((TAsyncClient) o).setTimeout(timeout);
            return o;
        } else {
            if (isHabse) {// habse不支持TFramedTransport
                TProtocol protocol = new TBinaryProtocol(socket);
                Object o = synConstructor.newInstance(protocol);
                return o;
            } else {

                CustomizedTFramedTransport transport = (CustomizedTFramedTransport) socket;
                transport.setUnifiedProto(mtThrfitInvokeInfo.isUniProto());
                transport.setServiceName(getServiceName());
                transport.setProtocol(this.protocol);
                if (mtThrfitInvokeInfo.isUniProto()) {
                    TraceInfo traceInfo = TraceInfoUtil.clientSend(mtThrfitInvokeInfo.getSpanName(), localEndpoint,
                            mtThrfitInvokeInfo.getServerAppKey(), mtThrfitInvokeInfo.getServerIp(), mtThrfitInvokeInfo.getServerPort());
                    transport.setTraceInfo(traceInfo);
                }

                MtraceClientTBinaryProtocol protocol = new MtraceClientTBinaryProtocol(transport, mtThrfitInvokeInfo);
                protocol.setLocalEndpoint(localEndpoint);
                protocol.setClusterManager(clusterManager);
                return synConstructor.newInstance(protocol);
            }
        }
    }

    public TProtocol getProtocol(TTransport socket, MtThrfitInvokeInfo mtThrfitInvokeInfo) {
        CustomizedTFramedTransport transport = (CustomizedTFramedTransport) socket;
        transport.setUnifiedProto(mtThrfitInvokeInfo.isUniProto());
        transport.setServiceName(getServiceName());
        transport.setProtocol(this.protocol);
        if (mtThrfitInvokeInfo.isUniProto()) {
            TraceInfo traceInfo = TraceInfoUtil.clientSend(mtThrfitInvokeInfo.getSpanName(), localEndpoint,
                    mtThrfitInvokeInfo.getServerAppKey(), mtThrfitInvokeInfo.getServerIp(), mtThrfitInvokeInfo.getServerPort());
            transport.setTraceInfo(traceInfo);
        }
        MtraceClientTBinaryProtocol protocol = new MtraceClientTBinaryProtocol(transport, mtThrfitInvokeInfo);
        protocol.setLocalEndpoint(localEndpoint);
        protocol.setClusterManager(clusterManager);
        return protocol;
    }


    private Class<?> getIfaceInterface() {
        if (async)
            return getAsyncIfaceInterface();
        else
            return getSynIfaceInterface();
    }

    private String serviceInterfaceSimpleName;

    public String getServiceSimpleName() {
        if (serviceInterfaceSimpleName != null)
            return serviceInterfaceSimpleName;
        serviceInterfaceSimpleName = serviceInterface.getSimpleName();
        return serviceInterfaceSimpleName;
    }

    private Class<?> getSynClientClass() {
        Class<?>[] classes = serviceInterface.getClasses();
        for (Class c : classes)
            if (c.isMemberClass() && !c.isInterface() && c.getSimpleName().equals("Client")) {
                return c;
            }
        throw new IllegalArgumentException("serviceInterface must contain Sub Class of Client");
    }

    private Class<?> getAsyncClientClass() {
        Class<?>[] classes = serviceInterface.getClasses();
        for (Class c : classes)
            if (c.isMemberClass() && !c.isInterface() && c.getSimpleName().equals("AsyncClient")) {
                return c;
            }
        throw new IllegalArgumentException("serviceInterface must contain Sub Class of AsyncClient");
    }

    private Class<?> getSynIfaceInterface() {
        if (annotatedThrift) {
            return serviceInterface;
        } else {
            // 获取public的类声明
            Class<?>[] classes = serviceInterface.getClasses();
            // 遍历
            for (Class c : classes)
                // 成员类,且是接口,且简单名称为Iface
                if (c.isMemberClass() && c.isInterface() && c.getSimpleName()
                        .equals("Iface")) {
                    return c;
                }
            throw new IllegalArgumentException(
                    "serviceInterface must contain Sub Interface of Iface");
        }
    }

    private Class<?> getAsyncIfaceInterface() {
        Class<?>[] classes = serviceInterface.getClasses();
        for (Class c : classes)
            if (c.isMemberClass() && c.isInterface() && c.getSimpleName().equals("AsyncIface")) {
                return c;
            }
        throw new IllegalArgumentException("serviceInterface must contain Sub Interface of AsyncIface");
    }

    @Deprecated
    public void setStrAgentUrl(String strAgentUrl) {
        this.strAgentUrl = strAgentUrl;
    }

    public boolean isRetryRequest() {
        return retryRequest;
    }

    public void setRetryRequest(boolean retryRequest) {
        this.retryRequest = retryRequest;
    }

    public String getRemoteAppkey() {
        return remoteAppkey;
    }

    public void setRemoteAppkey(String remoteAppkey) {
        this.remoteAppkey = remoteAppkey;
    }

    public String getClusterManager() {
        return clusterManager;
    }

    @Deprecated
    public void setClusterManager(String clusterManager) {
        this.clusterManager = clusterManager;
    }

    public int getRemoteServerPort() {
        return remoteServerPort;
    }

    public void setRemoteServerPort(int remoteServerPort) {
        this.remoteServerPort = remoteServerPort;
    }

    public boolean isEnableRemoteDCServer() {
        return enableRemoteDCServer;
    }

    public void setEnableRemoteDCServer(boolean enableRemoteDCServer) {
        this.enableRemoteDCServer = enableRemoteDCServer;
    }

    public int getSlowStartSeconds() {
        return slowStartSeconds;
    }

    public void setSlowStartSeconds(int slowStartSeconds) {
        this.slowStartSeconds = slowStartSeconds;
    }

    public void setbUpdateLocalConfig(boolean bUpdateLocalConfig) {
        this.bUpdateLocalConfig = bUpdateLocalConfig;
    }

    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    @Deprecated
    public void setAnnotatedThrift(boolean annotatedThrift) {
        this.annotatedThrift = annotatedThrift;
    }

    public boolean getAnnotatedThrift() {
        return this.annotatedThrift;
    }

    public void setLocalServerPort(int localServerPort) {
        this.localServerPort = localServerPort;
    }

    public void setResponseCollector(Class<?> responseCollectorClass) {
        try {
            responseCollector = (IResponseCollector) (responseCollectorClass.newInstance());
        } catch (InstantiationException e) {
            logger.error("InstantiationException " + e);
            responseCollector = null;
        } catch (IllegalAccessException e) {
            logger.error("IllegalAccessException " + e);
            responseCollector = null;
        }
    }

    public ISignHandler getSignHandler() {
        return signHandler;
    }

    public void setSignHandler(ISignHandler signHandler) {
        this.signHandler = signHandler;
    }

    public boolean isFilterByServiceName() {
        return filterByServiceName;
    }

    public void setFilterByServiceName(boolean filterByServiceName) {
        this.filterByServiceName = filterByServiceName;
    }

    /**
     * 直连时服务端是否为统一协议
     */
    public boolean isRemoteUniProto() {
        return remoteUniProto;
    }

    /**
     * 直连时服务端是否为统一协议
     *
     * @param remoteUniProto
     */
    public void setRemoteUniProto(boolean remoteUniProto) {
        this.remoteUniProto = remoteUniProto;
    }
}