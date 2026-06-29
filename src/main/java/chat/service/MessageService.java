package chat.service;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import java.util.List;

public class MessageService {

    private final ZooKeeper zk;

    public MessageService(ZooKeeper zk) {
        this.zk = zk;
    }

    private String conversationId(String a, String b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }

    private String basePath(String convId) {
        return "/chat/conversations/" + convId;
    }

    public void send(String from, String to, String message) throws Exception {

        String convId = conversationId(from, to);

        String root = basePath(convId);
        ensure(root);
        ensure(root + "/messages");

        String msgPath = root + "/messages/msg";

        zk.create(
                msgPath,
                (from + ":" + message).getBytes(),
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT_SEQUENTIAL);
    }

    public void list(String from, String to) throws Exception {

        String convId = conversationId(from, to);
        String root = basePath(convId) + "/messages";

        List<String> msgs = zk.getChildren(root, false);

        // garante ordem correta
        msgs.sort(String::compareTo);

        for (String m : msgs) {

            byte[] data = zk.getData(root + "/" + m, false, null);
            String raw = new String(data);

            // formato: from:message
            String[] parts = raw.split(":", 2);

            String sender = parts[0];
            String msg = parts.length > 1 ? parts[1] : raw;

            System.out.println(m + " [" + sender + "] -> " + msg);
        }
    }

    private void ensure(String path) throws Exception {

        String[] parts = path.split("/");
        String current = "";

        for (int i = 1; i < parts.length; i++) {

            current += "/" + parts[i];

            if (zk.exists(current, false) == null) {
                try {
                    zk.create(
                            current,
                            new byte[0],
                            ZooDefs.Ids.OPEN_ACL_UNSAFE,
                            CreateMode.PERSISTENT);
                } catch (org.apache.zookeeper.KeeperException.NodeExistsException e) {
                    // Ignore, already created
                }
            }
        }
    }
}