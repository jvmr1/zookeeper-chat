package chat.service;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import java.util.List;

public class GroupWatcherService {

    private final ZooKeeper zk;

    public GroupWatcherService(ZooKeeper zk) {
        this.zk = zk;
    }

    private boolean isOwner(String groupPath, String self) {
        try {
            byte[] data = zk.getData(groupPath, false, null);
            if (data == null || data.length == 0) return false;
            String s = new String(data);
            for (String part : s.split(";")) {
                String[] kv = part.split(":", 2);
                if (kv.length == 2 && kv[0].equals("owner") && kv[1].equals(self)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // ignorar falhas de leitura
        }
        return false;
    }

    public void watchRequests(String group, String self) throws Exception {
        String path = "/chat/groups/" + group + "/requests";
        if (zk.exists(path, false) == null) return;

        zk.getChildren(path, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if (event.getType() == Event.EventType.NodeChildrenChanged) {
                    try {
                        List<String> reqs = zk.getChildren(path, false);
                        if (!reqs.isEmpty()) {
                            System.out.println("\n[SOLICITAÇÃO] Usuários aguardando no grupo " + group + ": " + String.join(", ", reqs));
                            System.out.println("Use 'grupo aprovar " + group + " <usuario>' ou 'grupo reprovar " + group + " <usuario>' para responder.");
                            System.out.print("\n> ");
                        }
                        watchRequests(group, self); // re-register
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public void watchAllGroups(String self) throws Exception {

        String root = "/chat/groups";

        if (zk.exists(root, false) == null)
            return;

        // Watcher no root: detecta criação de novos grupos enquanto online
        List<String> groups = zk.getChildren(root, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                if (event.getType() == Event.EventType.NodeChildrenChanged) {
                    try {
                        watchAllGroups(self);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        for (String group : groups) {

            String groupFullPath = root + "/" + group;

            // Se for dono do grupo, observa a fila de solicitações (requests)
            if (isOwner(groupFullPath, self)) {
                watchRequests(group, self);
            }

            String membersPath = groupFullPath + "/members";

            if (zk.exists(membersPath, false) == null)
                continue;

            // Watcher nos membros: detecta quando o usuário é adicionado a um grupo
            // existente
            zk.getChildren(membersPath, new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    if (event.getType() == Event.EventType.NodeChildrenChanged) {
                        try {
                            // se agora é membro, ativa o watcher de mensagens
                            if (zk.exists(root + "/" + group + "/members/" + self, false) != null) {
                                watch(group, self);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });

            // Se já é membro, registra watcher nas mensagens imediatamente
            if (zk.exists(root + "/" + group + "/members/" + self, false) != null) {
                watch(group, self);
            }
        }
    }

    public void watch(String group, String self) throws Exception {

        String path = "/chat/groups/" + group + "/messages";

        if (zk.exists(path, false) == null)
            return;

        zk.getChildren(path, new Watcher() {

            @Override
            public void process(WatchedEvent event) {

                if (event.getType() == Event.EventType.NodeChildrenChanged) {

                    try {

                        List<String> msgs = zk.getChildren(path, false);
                        msgs.sort(String::compareTo);

                        String last = msgs.get(msgs.size() - 1);

                        byte[] data = zk.getData(path + "/" + last, false, null);

                        String raw = new String(data);
                        String[] p = raw.split(":", 2);

                        String from = p[0];
                        String msg = p[1];

                        if (!from.equals(self)) {

                            System.out.println(
                                    "\n[GRUPO " + group + "] " + from + ": " + msg);

                            System.out.print("\n> ");
                        }

                        watch(group, self); // re-register

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
}