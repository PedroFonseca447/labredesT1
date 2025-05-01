import java.io.IOException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws IOException {
        int porta = 50000; // porta padrão
        if (args.length > 0) {
            try {
                porta = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("[!] Porta inválida, usando 50000 como padrão.");
            }
        }

        UdpServer server = new UdpServer(porta);
        server.start();

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("Comando> ");
            String input = scanner.nextLine();
            server.comandProcess(input);
        }
    }
}