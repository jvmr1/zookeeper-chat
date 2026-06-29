package chat;

import org.apache.zookeeper.*;
import java.util.concurrent.CountDownLatch;

public class ZKClient implements Watcher {

    private ZooKeeper zk;
    private CountDownLatch connected = new CountDownLatch(1);

    public ZKClient(String host) throws Exception {
        zk = new ZooKeeper(host, 3000, this);
        connected.await();
    }

    public ZooKeeper get() {
        return zk;
    }

    @Override
    public void process(WatchedEvent event) {
        if (event.getState() == Event.KeeperState.SyncConnected) {
            connected.countDown();
        }
    }
}