package chat.cli;

import chat.service.MessageService;
import chat.service.MessageWatcherService;
import chat.service.PresenceService;

import java.util.List;
import java.util.Scanner;

public class CommandLoop {

    private final Scanner scanner;
    private final PresenceService presence;
    private final MessageService messageService;
    private final MessageWatcherService watcher;
    private final String username;

    private boolean running = true;

    public CommandLoop(
            Scanner scanner,
            PresenceService presence,
            MessageService messageService,
            MessageWatcherService watcher,
            String username
    ) {
        this.scanner = scanner;
        this.presence = presence;
        this.messageService = messageService;
        this.watcher = watcher;
        this.username = username;
    }

    public void start() throws Exception {

        System.out.println("\nBem-vindo, " + username);

        // 🔥 ativa watcher global
        watcher.watchAll(username);

        while (running) {

            System.out.print("\n> ");
            String input = scanner.nextLine().trim();

            if (input.equals("online")) {
                handleOnline();
            }

            else if (input.startsWith("msg ")) {
                handleMsg(input);
            }

            else if (input.startsWith("abrir ")) {
                handleAbrir(input);
            }

            else if (input.equals("logout")) {
                handleLogout();
            }

            else {
                System.out.println("Comando desconhecido");
            }
        }
    }

    private void handleOnline() throws Exception {

        List<String> users = presence.listOnline();

        System.out.println(String.join(", ", users));
    }

    private void handleMsg(String input) throws Exception {

        String[] parts = input.split(" ", 3);

        String to = parts[1];
        String msg = parts[2];

        messageService.send(username, to, msg);
    }

    private void handleAbrir(String input) throws Exception {

        String to = input.split(" ")[1];

        messageService.list(username, to);
    }

    private void handleLogout() throws Exception {

        presence.logout(username);
        running = false;
    }
}