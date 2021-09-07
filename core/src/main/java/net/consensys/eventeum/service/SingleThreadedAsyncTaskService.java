/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.consensys.eventeum.service;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import net.consensys.eventeum.chain.settings.NodeSettings;
import net.consensys.eventeum.service.sync.MyAction;

/**
 * An async task service that utilises a single thread executor
 *
 * @author Craig Williams <craig.williams@consensys.net>
 */
@Component("asyncTaskService")
public class SingleThreadedAsyncTaskService implements AsyncTaskService {

    private Map<String, ExecutorService> executorServices;
    private Map<String, AtomicInteger> taskLimitationsMap;
    private Map<String, CompletableFuture<Void>> lastTaskMap;
    private Map<String, Integer> blockCacheCountMap;
    private NodeSettings nodeSettings;

    public SingleThreadedAsyncTaskService(NodeSettings nodeSettings) {
        executorServices = new HashMap<>();
        taskLimitationsMap = new HashMap<>();
        lastTaskMap = new HashMap<>();
        blockCacheCountMap = new HashMap<>();
        this.nodeSettings = nodeSettings;
    }

    @Override
    public void execute(String executorName, Runnable task) {
        getOrCreateExecutor(executorName).execute(task);
    }

    @Override
    public void executeWithLimitation(String executorName, MyAction action){
        if (!taskLimitationsMap.containsKey(executorName)) {
            taskLimitationsMap.put(executorName, new AtomicInteger(0));
        }
        while(true){
            int currentTaskCount = taskLimitationsMap.get(executorName).get();
            System.out.println(executorName + " current exection count is: " + currentTaskCount);

            if(currentTaskCount < 5){
                break;
            }
            try{
                Thread.sleep(1000);
            }
            catch(Exception exception){
            }
        }
        taskLimitationsMap.get(executorName).getAndIncrement();
        getOrCreateExecutor(executorName).execute(new Runnable(){
            public void run(){
                try{
                    action.invoke();
                }
                catch(Exception exception){
                    System.out.println(exception.getMessage());
                }
                taskLimitationsMap.get(executorName).getAndDecrement();
            }
        });
    }

    @Override
    public <T> Future<T> submit(String executorName, Callable<T> task) {
        return getOrCreateExecutor(executorName).submit(task);
    }

    @Override
    public CompletableFuture<Void> executeWithCompletableFuture(String executorName, Runnable task) {
        return CompletableFuture.runAsync(task, getOrCreateExecutor(executorName));
    }

    @Override
    public void executeWithCompletableFutureWithLimitation(String executorName, Runnable task) {
        if (!taskLimitationsMap.containsKey(executorName)) {
            taskLimitationsMap.put(executorName, new AtomicInteger(0));
        }
        if(!blockCacheCountMap.containsKey(executorName)){
            String nodeName = executorName.split("-")[1];
            Integer blockCacheCount = nodeSettings.getNode(nodeName).getMaxBlockCacheCount();
            blockCacheCountMap.put(executorName, blockCacheCount);
            System.out.println("=====" + nodeName + " cache count is :" + blockCacheCount);
        }
        int maxCachedBlockCount = blockCacheCountMap.get(executorName);
        int currentTaskCount = taskLimitationsMap.get(executorName).get();
        
        if(currentTaskCount > maxCachedBlockCount){
            System.out.println("===== begin to start last task complete");
            lastTaskMap.get(executorName).join();
            taskLimitationsMap.get(executorName).set(0);
        }
        CompletableFuture<Void> currentTask = CompletableFuture.runAsync(task, getOrCreateExecutor(executorName));
        lastTaskMap.put(executorName, currentTask);
        taskLimitationsMap.get(executorName).getAndIncrement();
    }

    private ExecutorService getOrCreateExecutor(String executorName) {
        if (!executorServices.containsKey(executorName)) {
            executorServices.put(executorName, buildExecutor(executorName));
        }

        return executorServices.get(executorName);
    }

    protected ExecutorService buildExecutor(String executorName) {
        return Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setNameFormat(executorName + "-%d").build());
    }
}
