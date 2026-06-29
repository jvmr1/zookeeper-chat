package chat.service;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import java.util.List;

public class PresenceService {

    private final ZooKeeper zk;

    // Raiz da presença no sistema
    private static final String ROOT = "/chat/presence";

    public PresenceService(ZooKeeper zk) {
        this.zk = zk;
    }

    /**
     * Usuário entra online (ephemeral node)
     */
    public void login(String username) throws Exception {

        ensureRoot();

        String path = ROOT + "/" + username;

        // Se já existir (reconexão ou bug), remove antes
        if (zk.exists(path, false) != null) {
            zk.delete(path, -1);
        }

        zk.create(
                path,
                "online".getBytes(),

                ZooDefs.Ids.OPEN_ACL_UNSAFE, // sem ACL por enquanto (lab)

                CreateMode.EPHEMERAL // some automaticamente se o client cair
        );

        System.out.println(username + " entrou online");
    }

    /**
     * Usuário sai voluntariamente
     */
    public void logout(String username) throws Exception {

        String path = ROOT + "/" + username;

        if (zk.exists(path, false) != null) {
            zk.delete(path, -1);
        }

        System.out.println(username + " saiu");
    }

    /**
     * Lista usuários online
     */
    public List<String> listOnline() throws Exception {

        ensureRoot();

        return zk.getChildren(ROOT, false);
    }

    /**
     * Verifica se usuário está online
     */
    public boolean isOnline(String username) throws Exception {
        return zk.exists(ROOT + "/" + username, false) != null;
    }

    /**
     * Garante estrutura base
     */
    private void ensureRoot() throws Exception {

        if (zk.exists("/chat", false) == null) {
            zk.create(
                    "/chat",
                    new byte[0],
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.PERSISTENT
            );
        }

        if (zk.exists(ROOT, false) == null) {
            zk.create(
                    ROOT,
                    new byte[0],
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.PERSISTENT
            );
        }
    }
}