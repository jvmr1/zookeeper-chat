package chat.service;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.util.List;

public class MessageWatcherService {

    private final ZooKeeper zk;

    public MessageWatcherService(ZooKeeper zk) {
        this.zk = zk;
    }

    public void watchAll(String self) throws Exception {

        String root = "/chat/conversations";

        if (zk.exists(root, false) == null)
            return;

        List<String> convs = zk.getChildren(root, false);

        for (String conv : convs) {

            String[] parts = conv.split("_");

            // só participa se for membro real
            boolean isParticipant = parts[0].equals(self) || parts[1].equals(self);

            if (!isParticipant)
                continue;

            String path = root + "/" + conv + "/messages";

            zk.getChildren(path, new Watcher() {

                @Override
                public void process(WatchedEvent event) {

                    if (event.getType() == Event.EventType.NodeChildrenChanged) {

                        try {

                            List<String> msgs = zk.getChildren(path, false);

                            msgs.sort(String::compareTo);

                            if (!msgs.isEmpty()) {

                                String last = msgs.get(msgs.size() - 1);

                                byte[] data = zk.getData(path + "/" + last, false, null);

                                String raw = new String(data);
                                String[] p = raw.split(":", 2);

                                String from = p[0];
                                String msg = p.length > 1 ? p[1] : raw;

                                // não notifica remetente
                                if (!from.equals(self)) {
                                    System.out.println(
                                            "\n[NOVA MENSAGEM] de " + from + ": " + msg);
                                    System.out.print("\n> ");
                                }
                            }

                            // re-registra watcher
                            watchAll(self);

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }
}