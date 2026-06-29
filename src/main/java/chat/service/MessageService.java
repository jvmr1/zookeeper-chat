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

        // garante ordem fixa (evita alice_bob vs bob_alice)
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }

    private String basePath(String convId) {
        return "/chat/conversations/" + convId;
    }

    public void send(String from, String to, String message) throws Exception {

        String convId = conversationId(from, to);

        String root = basePath(convId);
        String msgPath = root + "/messages/msg";

        ensure(root);
        ensure(root + "/messages");

        String created = zk.create(
                msgPath,
                (from + ":" + message).getBytes(),
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.PERSISTENT_SEQUENTIAL
        );

        System.out.println("Mensagem enviada: " + created);
    }

    public void list(String from, String to) throws Exception {

        String convId = conversationId(from, to);
        String root = basePath(convId) + "/messages";

        List<String> msgs = zk.getChildren(root, false);

        for (String m : msgs) {
            byte[] data = zk.getData(root + "/" + m, false, null);
            System.out.println(m + " -> " + new String(data));
        }
    }

    private void ensure(String path) throws Exception {

        String[] parts = path.split("/");

        String current = "";

        for (int i = 1; i < parts.length; i++) {

            current += "/" + parts[i];

            if (zk.exists(current, false) == null) {

                zk.create(
                        current,
                        new byte[0],
                        ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.PERSISTENT
                );
            }
        }
    }
}