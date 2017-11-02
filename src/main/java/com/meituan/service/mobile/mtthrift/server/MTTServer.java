package com.meituan.service.mobile.mtthrift.server;

import com.meituan.mtrace.Endpoint;
import com.meituan.service.mobile.mtthrift.auth.AuthFailedException;
import com.meituan.service.mobile.mtthrift.auth.AuthorUtil;
import com.meituan.service.mobile.mtthrift.auth.IAuthHandler;
import com.meituan.service.mobile.mtthrift.client.invoker.IMTThriftFilter;
import com.meituan.service.mobile.mtthrift.proxy.ThriftServerPublisher;
import com.meituan.service.mobile.mtthrift.server.customize.CustomizedTThreadedSelectorServer;
import com.meituan.service.mobile.mtthrift.util.Consts;
import com.meituan.service.mobile.mtthrift.util.MtThriftManifest;
import com.sankuai.inf.octo.mns.MnsInvoker;
import com.sankuai.inf.octo.mns.ProcessInfoUtil;
import com.sankuai.sgagent.thrift.model.ConfigStatus;
import com.sankuai.sgagent.thrift.model.CustomizedStatus;
import com.sankuai.sgagent.thrift.model.SGService;
import com.sankuai.sgagent.thrift.model.ServiceDetail;
import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.server.TServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 对TServer的进一步封装
 */
public abstract class MTTServer {
    protected Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    /**
     * 处理器,这个与serviceProcessorMap的区别是???
     */
    protected TProcessor tprocessor;

    protected String ip;
    protected int port;
    protected String env;// 环境名（同一服务）
    protected String clusterManager;
    protected String strAgentUrl;
    protected String appKey;
    protected Endpoint localEndpoint;
    protected int slowStartSeconds;

    protected boolean inited;
    protected boolean serializeNullStringAsBlank;

    protected TServer server;

    /**
     * meituan name service
     * 用于服务注册和获取
     */
    protected MnsInvoker mnsInvoker;
    protected int shutdownWaitTime;
    protected Class<?> serviceInterface;
    protected ConfigStatus configStatus;
    protected String serviceSimpleName = "";
    protected Object serviceImpl;
    /**
     * 服务处理器映射,key为服务名称,value为TProcessor,即具体的处理器
     */
    protected Map<String, TProcessor> serviceProcessorMap = new HashMap<String, TProcessor>();
    protected Map<String, Executor> executorMap = new HashMap<String, Executor>();

    protected int selectorThreads = 0;
    protected int workerThreads = 0;
    protected int maxWorkerThreads;
    protected int workQueueSize;
    protected int acceptQueueSizePerThread = 0;
    protected IMTThriftFilter reuqestFilter;

    protected long maxReadBufferBytes = 10 * 1024 * 1024;
    protected int maxServerConn;
    protected int limitCount;
    protected int limitSecondsTime;
    protected IAuthHandler authHandler;
    protected volatile boolean isShutdown = false;

    public void setSelectorThreads(int selectorThreads) {
        this.selectorThreads = selectorThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public void setAcceptQueueSizePerThread(int acceptQueueSizePerThread) {
        this.acceptQueueSizePerThread = acceptQueueSizePerThread;
    }

    public void setMaxReadBufferBytes(long maxReadBufferBytes) {
        if (maxReadBufferBytes >= 1000) {
            this.maxReadBufferBytes = maxReadBufferBytes;
        }
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

    public void setReuqestFilter(IMTThriftFilter reuqestFilter) {
        this.reuqestFilter = reuqestFilter;
    }

    private MTTServer() {

    }

    private static String getLocalIp() {
        return ProcessInfoUtil.getLocalIpV4();
    }

    public MTTServer(int port) throws Exception {
        this(getLocalIp(), port);
    }

    public MTTServer(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public void setServiceImpl(Object serviceImpl) {
        this.serviceImpl = serviceImpl;
    }

    public void registerTProcessor(TProcessor tprocessor) {
        this.tprocessor = tprocessor;
    }

    public void registerTProcessor(String serviceName, TProcessor tprocessor) {
        this.serviceProcessorMap.put(serviceName, tprocessor);
    }

    public void registerExecutor(String name, Executor executor) {
        this.executorMap.put(name, executor);
    }

    public void setTprocessor(TProcessor tprocessor) {
        this.tprocessor = tprocessor;
    }

    private void initMnsInvoker(String strAgentUrl) {
        mnsInvoker = MnsInvoker.getInstance(strAgentUrl);

    }

    InetSocketAddress getDeploySocketAddress() {
        ip = ProcessInfoUtil.getLocalIpV4();
        return new InetSocketAddress(ip, port);
    }

    private String getEnv() {
        if (env == null || env.trim().length() == 0) {
            env = System.getProperty("mtthrift_env", "DEFAULT");
        }
        return env;
    }

    public void initLocalEndPoint() {
        ip = ProcessInfoUtil.getLocalIpV4();
        localEndpoint = new Endpoint(appKey, ip, port);
    }

    public Endpoint getLocalEndpoint() {
        return localEndpoint;
    }

    public void init() throws Exception {
        if (inited == false) {
            synchronized (MTTServer.class) {
                // 初始化 Agent 操作类
                initMnsInvoker(strAgentUrl);
                if (!AuthorUtil.authoriseProvider(appKey, localEndpoint.getHost()))
                    throw new AuthFailedException("Authorise failed, appKey:" + appKey
                            + " ip:" + localEndpoint.getHost() + "Unable to register!");
                registerProviderOnMns();
                inited = true;
            }
        }
    }

    /**
     * 借助 Agent 注册接口, 注册为 Octo mns 的 Provider 节点.
     */
    private void registerProviderOnMns() {
        // 创建 Agent 客户端实例
        SGService sgService = new SGService();
        sgService.setAppkey(appKey);

        CustomizedStatus initStatus = configStatus.getInitStatus();
        sgService.setStatus(initStatus.getValue());
        sgService.setIp(localEndpoint.getHost());
        sgService.setPort(localEndpoint.getPort());
        sgService.setLastUpdateTime((int) (System.currentTimeMillis() / 1000));
        sgService.setRole(Consts.defaultRole);
        sgService.setProtocol("thrift");
        sgService.setVersion("mtthrift-v" + MtThriftManifest.getVersion());
        sgService.setWeight(10);
        sgService.setFweight(10.d);
        String extend = clusterManager +
                Consts.vbar + "slowStartSeconds" + Consts.colon + slowStartSeconds;
        sgService.setExtend(extend);
        sgService.setHeartbeatSupport((byte) 0x02);
        if (serviceProcessorMap != null && serviceProcessorMap.size() > 0) {
            sgService.setServiceInfo(new HashMap<String, ServiceDetail>() {
                {
                    for (Map.Entry<String, TProcessor> processorEntry : serviceProcessorMap.entrySet()) {
                        put(processorEntry.getKey(), new ServiceDetail(true));
                    }
                }
            });
        } else {
            sgService.setServiceInfo(new HashMap<String, ServiceDetail>() {
                {
                    put(serviceInterface.getName(), new ServiceDetail(true));
                }
            });
        }

        try {
            MnsInvoker.registServiceWithCmd(0, sgService);
            logger.info("registerProviderOnMns: " + sgService.toString());
        } catch (Exception e) {
            logger.error("Register by agent exception!", e);
        }
    }

    private String getServiceClassName() {
        String className = null;
        if (tprocessor != null) {
            if (tprocessor instanceof ThriftServerPublisher.MtTProcessor) {
                className = ((ThriftServerPublisher.MtTProcessor) tprocessor).get_processor().getClass().getEnclosingClass().getName();
            } else {
                className = tprocessor.getClass().getEnclosingClass().getName();
            }
        }
        return className;
    }

    public void shutdown() {
        logger.info("stopping server: stop listening, stop threads ...");
        try {
            if (server instanceof CustomizedTThreadedSelectorServer) {
                ConfigStatus configStatus = ((CustomizedTThreadedSelectorServer) server).getConfigStatus();
                configStatus.setRuntimeStatus(CustomizedStatus.DEAD);
            }
            MnsInvoker.unRegisterThriftService(appKey, port);
        } catch (TException e) {
            logger.debug(e.getMessage(), e);
        }
        try {
            logger.info("sleep " + this.shutdownWaitTime + " seconds.");
            Thread.sleep(this.shutdownWaitTime * 1000L);
        } catch (InterruptedException e) {
            logger.error("exception while waitting " + this.shutdownWaitTime +
                    " seconds to close thrift server", e);
        }
        server.stop();// 关闭socket
        removeShutDownHook();// 移除kill钩子
        logger.info("finish gracefully stopping service on port: " + port);
    }

    private volatile ShutDownHook _hook;

    public void addShutDownHook() {
        _hook = new ShutDownHook(this);
        Runtime.getRuntime().addShutdownHook(_hook);
    }

    private void removeShutDownHook() {
        if (_hook != null) {
            Runtime.getRuntime().removeShutdownHook(_hook);
        }
    }

    /**
     * 启动入口
     *
     * @param daemon
     * @throws Exception
     */
    public abstract void run(boolean daemon) throws Exception;

    public String getClusterManager() {
        return clusterManager;
    }

    public void setClusterManager(String clusterManager) {
        this.clusterManager = clusterManager;
    }

    public String getStrAgentUrl() {
        return strAgentUrl;
    }

    public void setStrAgentUrl(String strAgentUrl) {
        this.strAgentUrl = strAgentUrl;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public int getSlowStartSeconds() {
        return slowStartSeconds;
    }

    public void setSlowStartSeconds(int slowStartSeconds) {
        this.slowStartSeconds = slowStartSeconds;
    }

    public boolean isSerializeNullStringAsBlank() {
        return serializeNullStringAsBlank;
    }

    public void setSerializeNullStringAsBlank(
            boolean serializeNullStringAsBlank) {
        this.serializeNullStringAsBlank = serializeNullStringAsBlank;
    }


    public Class<?> getServiceInterface() {
        return serviceInterface;
    }

    public void setServiceInterface(Class<?> serviceInterface) {
        this.serviceInterface = serviceInterface;
        this.serviceSimpleName = serviceInterface == null ? "" : serviceInterface.getSimpleName();
    }

    public Map<String, TProcessor> getServiceProcessorMap() {
        return serviceProcessorMap;
    }

    public void setServiceProcessorMap(Map<String, TProcessor> serviceProcessorMap) {
        this.serviceProcessorMap = serviceProcessorMap;
    }

    public Map<String, Executor> getExecutorMap() {
        return executorMap;
    }

    public void setExecutorMap(Map<String, Executor> executorMap) {
        this.executorMap = executorMap;
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

    public void setShutdownWaitTime(int shutdownWaitTime) {
        this.shutdownWaitTime = shutdownWaitTime;
    }

    public void setConfigStatus(ConfigStatus configStatus) {
        this.configStatus = configStatus;
    }

    public ConfigStatus getConfigStatus() {
        return configStatus;
    }

    public String getServiceSimpleName() {
        return serviceSimpleName;
    }

    public IAuthHandler getAuthHandler() {
        return authHandler;
    }

    public void setAuthHandler(IAuthHandler authHandler) {
        this.authHandler = authHandler;
    }

    class ShutDownHook extends Thread {
        private MTTServer _server;

        public ShutDownHook(MTTServer server) {
            this._server = server;
        }

        public void run() {
            _hook = null;
            _server.shutdown();
        }
    }

    class DaemonThread extends Thread {
        private TServer server;

        public DaemonThread(TServer server) {
            this.server = server;
            setDaemon(true);
        }

        public void run() {
            logger.info("Start daemon tserver " + ip + ":" + port);
            server.serve();
        }
    }

}
