/*
 * Copyright 2015-2020 Gamioo Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gamioo.robot;

import com.alibaba.fastjson.JSON;
import io.gamioo.core.concurrent.GameThreadFactory;
import io.gamioo.core.util.FileUtils;
import io.gamioo.core.util.TelnetUtils;
import io.gamioo.core.util.ThreadUtils;
import io.gamioo.robot.entity.Proxy;
import io.gamioo.robot.entity.Server;
import io.gamioo.robot.entity.Target;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * some description
 *
 * @author Allen Jiang
 * @since 1.0.0
 */
public class H5Robot {
    private static final Logger logger = LogManager.getLogger(H5Robot.class);
    private ScheduledExecutorService stat = Executors.newScheduledThreadPool(1, new GameThreadFactory("stat"));
    private Target target;
    private Map<Integer, Proxy> proxyStore;
    private List<Long> userList;
    private boolean complete;
    private int step;

    public void init() {
        stat.scheduleAtFixedRate(() -> {
            int num = WebSocketClient.getConnectNum();
            logger.info("活跃的连接数 num={}", num);
//            if (complete) {
//                if(num < 20){
//                    step++;
//                    if (step >=6) {
//                        logger.info("启动连接补偿");
//                        this.asyncHandle();
//                        step = 0;
//                    }
//                }else{
//                    step=0;
//                }
//
//            }
        }, 60000, 60000, TimeUnit.MILLISECONDS);
    }


    public static void main(String[] args) {
        String version = H5Robot.class.getPackage().getImplementationVersion();
        logger.info("start working version={}", version);
        H5Robot robot = new H5Robot();
        robot.init();
        List<Long> userList = robot.getUserList("user.txt");
        robot.setUserList(userList);
        Target target = robot.getTarget("target.json");
        robot.setTarget(target);
        Map<Integer, Proxy> proxyStore = robot.getProxyStore("cell.json");
        robot.setProxyStore(proxyStore);
        robot.handle();


        logger.info("end working");

    }

    public void asyncHandle() {
        new Thread(() -> {
            handle();
        }).start();

    }

    public void handle() {
        complete = false;
        int id = 0;
        int size = proxyStore.size();
        if (size > 0) {
            int max = (int) Math.ceil(1f * target.getNumber() / size);
            for (int i = 0; i < max; i++) {
                for (Proxy proxy : proxyStore.values()) {
                    Date now = new Date();
                    if (now.before(proxy.getExpireTime())) {
                        WebSocketClient client = new WebSocketClient(++id,userList.get(id-1),proxy, target);
                        ThreadUtils.sleep(target.getInterval()+target.getError()*10);
                        client.connect();
                    }
                }
            }

        } else {
            for (int i = 0; i < target.getNumber(); i++) {
                WebSocketClient client = new WebSocketClient(++id, userList.get(id-1),null, target);
                ThreadUtils.sleep(target.getInterval()+target.getError()*10);
                client.connect();
            }
        }
        complete = true;
    }


    public <T extends Server> List<T> getServerList(Class<T> clazz, String path) {
        List<T> list = new ArrayList<>();
        File file = FileUtils.getFile(path);
        try {
            List<String> array = FileUtils.readLines(file, Charset.defaultCharset());
            array.forEach(e -> {
                T T = JSON.parseObject(e, clazz);
                boolean connected = TelnetUtils.isConnected(T.getIp(), T.getPort());
                if (connected) {
                    T.parse();
                    list.add(T);
                } else {
                    logger.error("目标无法通信 target={}", T);
                }
            });
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return list;
    }

    public List<Long> getUserList(String path) {
        List<Long> ret = new ArrayList<>();
        File file = FileUtils.getFile(path);
        try {
            List<String> content = FileUtils.readLines(file);
            content.forEach((value) -> {
                ret.add(Long.parseLong(value));
            });

        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return ret;
    }

    public Target getTarget(String path) {
        Target ret = null;
        File file = FileUtils.getFile(path);
        try {
            String content = FileUtils.readFileToString(file);
            ret = JSON.parseObject(content, Target.class);
            boolean connected = TelnetUtils.isConnected(ret.getIp(), ret.getPort());
            if (connected) {
                ret.parse();
            } else {
                logger.error("目标无法通信 target={}", ret);
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return ret;

    }

    public Map<Integer, Proxy> getProxyStore(String path) {
        Map<Integer, Proxy> ret = new ConcurrentHashMap<>();
        Date now = new Date();
        File file = FileUtils.getFile(path);
        try {
            String array = FileUtils.readFileToString(file);
            List<Proxy> list = JSON.parseArray(array, Proxy.class);
            for (int i = 0; i < list.size(); i++) {
                Proxy proxy = list.get(i);
                proxy.setId(i + 1);
                boolean connected = TelnetUtils.isConnected(proxy.getIp(), proxy.getPort());
                if (now.before(proxy.getExpireTime()) && connected) {
                    ret.put(proxy.getId(), proxy);
                } else {
                    logger.error("目标无法通信 target={}", proxy);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return ret;
    }

    public List<Proxy> getProxyListX(String path) {
        List<Proxy> list = new ArrayList<>();
        File file = FileUtils.getFile(path);
        try {
            List<String> array = FileUtils.readLines(file, Charset.defaultCharset());
            array.forEach(e -> {
                Proxy proxy = new Proxy();
                proxy.init(e);
                boolean connected = TelnetUtils.isConnected(proxy.getIp(), proxy.getPort());
                if (connected) {
                    list.add(proxy);
                } else {
                    //    logger.error("目标无法通信 target={}", proxy);
                }
            });
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return list;
    }

    public Target getTarget() {
        return target;
    }

    public void setTarget(Target target) {
        this.target = target;
    }

    public Map<Integer, Proxy> getProxyStore() {
        return proxyStore;
    }

    public void setProxyStore(Map<Integer, Proxy> proxyStore) {
        this.proxyStore = proxyStore;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public List<Long> getUserList() {
        return userList;
    }

    public void setUserList(List<Long> userList) {
        this.userList = userList;
    }
}
